package io.github.peanutpersimmon.rilo.ui.feature.settings.report

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.peanutpersimmon.rilo.domain.service.SleepAnnualReport
import io.github.peanutpersimmon.rilo.domain.service.SleepMonthlyReport
import io.github.peanutpersimmon.rilo.domain.service.SleepReport
import io.github.peanutpersimmon.rilo.domain.service.SleepReportFileName
import io.github.peanutpersimmon.rilo.ui.feature.settings.report.annual.AnnualReportImageRenderer
import io.github.peanutpersimmon.rilo.ui.feature.settings.report.annual.asSleepPoster
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.roundToInt

private const val MAX_MONTHLY_HEIGHT_PX = 60_000
private const val MAX_BITMAP_BYTES = 160L * 1024L * 1024L

/** Monthly uses Sleep's 720dp Compose long image; annual uses its fixed 1080×2400 Canvas poster. */
suspend fun renderSleepReportBitmap(context: Context, report: SleepReport): Bitmap = when (report) {
    is SleepMonthlyReport -> renderMonthlyBitmap(context, report)
    is SleepAnnualReport -> withContext(Dispatchers.Main) {
        AnnualReportImageRenderer.renderAnnualReportBitmap(context, report.asSleepPoster())
    }
}

private suspend fun renderMonthlyBitmap(context: Context, report: SleepMonthlyReport): Bitmap =
    withContext(Dispatchers.Main) {
        val activity = context.findActivity() ?: error("报告导出需要 Activity")
        val root = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
        val widthDp = MonthlyReportDesignTokens.ReportWidth
        val widthPx = (widthDp.value * activity.resources.displayMetrics.density).roundToInt()
        val view = ComposeView(activity).apply { translationX = -widthPx * 2f }
        view.setViewTreeLifecycleOwner(root.findViewTreeLifecycleOwner())
        view.setViewTreeSavedStateRegistryOwner(root.findViewTreeSavedStateRegistryOwner())
        view.setContent {
            MaterialTheme(colorScheme = lightColorScheme(background = SleepReportPalette.background,
                surface = SleepReportPalette.surface, primary = SleepReportPalette.primaryDark,
                onBackground = SleepReportPalette.text, onSurface = SleepReportPalette.text)) {
                SleepMonthlyReportContent(report, exportMode = true, modifier = Modifier.width(widthDp))
            }
        }
        root.addView(view, ViewGroup.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT))
        try {
            suspendCancellableCoroutine<Unit> { continuation ->
                view.post { view.post { if (continuation.isActive) continuation.resume(Unit) } }
            }
            view.measure(View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(MAX_MONTHLY_HEIGHT_PX, View.MeasureSpec.AT_MOST))
            val height = view.measuredHeight
            check(height in 1 until MAX_MONTHLY_HEIGHT_PX) { "月报图片过长，无法完整导出" }
            check(widthPx.toLong() * height * 4L <= MAX_BITMAP_BYTES) { "月报图片占用内存过高" }
            view.layout(0, 0, widthPx, height)
            Bitmap.createBitmap(widthPx, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                view.draw(Canvas(bitmap))
            }
        } finally { root.removeView(view) }
    }

suspend fun saveSleepReportBitmap(context: Context, bitmap: Bitmap, report: SleepReport): Result<Uri> =
    withContext(Dispatchers.IO) { runCatching {
        val fileName = SleepReportFileName.forReport(report)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveWithMediaStore(context, bitmap, fileName)
        else saveToAppPictures(context, bitmap, fileName)
    } }

private fun saveWithMediaStore(context: Context, bitmap: Bitmap, fileName: String): Uri {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/RILO")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("无法创建图片")
    try {
        resolver.openOutputStream(uri)?.use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG 写入失败" }
        } ?: error("无法打开图片输出流")
        check(resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null) > 0)
        return uri
    } catch (error: Exception) {
        resolver.delete(uri, null, null)
        throw error
    }
}

private fun saveToAppPictures(context: Context, bitmap: Bitmap, fileName: String): Uri {
    val root = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: error("图片目录不可用")
    val directory = File(root, "RILO")
    check(directory.exists() || directory.mkdirs()) { "无法创建图片目录" }
    val file = File(directory, fileName)
    file.outputStream().use { stream ->
        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) { "PNG 写入失败" }
    }
    return Uri.fromFile(file)
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
