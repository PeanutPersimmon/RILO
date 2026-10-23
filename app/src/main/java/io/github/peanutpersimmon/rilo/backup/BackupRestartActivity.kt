package io.github.peanutpersimmon.rilo.backup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.github.peanutpersimmon.rilo.MainActivity
import java.lang.ref.WeakReference
import java.util.UUID

/** Visible trampoline in a separate process. It stays available until the new main process acks. */
class BackupRestartActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var retry: Button
    private lateinit var token: String
    private var launched = false
    private val fallback = Runnable { showFallback() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        token = intent.getStringExtra(EXTRA_TOKEN).orEmpty()
        status = TextView(this).apply {
            text = "正在重新启动 RILO…"
            textSize = 18f
            gravity = Gravity.CENTER
        }
        retry = Button(this).apply {
            text = "重新打开"
            visibility = View.GONE
            setOnClickListener { launchMain() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 32, 32, 32)
            addView(status)
            addView(retry)
        })
        active = WeakReference(this)
    }

    override fun onResume() {
        super.onResume()
        if (launched) return
        launched = true
        val oldPid = intent.getIntExtra(EXTRA_OLD_PID, -1)
        // The helper has its own PID and task affinity; only the closed-Room main process dies.
        if (oldPid > 0 && oldPid != Process.myPid()) Process.killProcess(oldPid)
        handler.postDelayed({ launchMain() }, 200L)
    }

    private fun launchMain() {
        retry.visibility = View.GONE
        status.text = "正在重新启动 RILO…"
        handler.removeCallbacks(fallback)
        try {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_TOKEN, token)
            })
            // startActivity can return without a usable new UI; only MainActivity's ack finishes us.
            handler.postDelayed(fallback, 2_500L)
        } catch (error: Exception) {
            Log.e(TAG, "Could not reopen RILO", error)
            showFallback()
        }
    }

    private fun showFallback() {
        status.text = "无法自动重新打开 RILO"
        retry.visibility = View.VISIBLE
    }

    private fun acknowledge(value: String) {
        if (value != token || token.isEmpty()) return
        handler.removeCallbacks(fallback)
        finishAndRemoveTask()
    }

    override fun onDestroy() {
        handler.removeCallbacks(fallback)
        if (active?.get() === this) active = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "RidoBackupRestart"
        private const val EXTRA_OLD_PID = "oldPid"
        const val EXTRA_TOKEN = "backupRestartToken"
        private var active: WeakReference<BackupRestartActivity>? = null

        fun restart(context: Context) {
            context.startActivity(Intent(context, BackupRestartActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_OLD_PID, Process.myPid())
                putExtra(EXTRA_TOKEN, UUID.randomUUID().toString())
            })
        }

        internal fun onMainReady(token: String) { active?.get()?.acknowledge(token) }
    }
}

/** Explicit same-app acknowledgement, delivered in the still-visible restart process. */
class BackupRestartAcknowledgementReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BackupRestartActivity.onMainReady(intent.getStringExtra(BackupRestartActivity.EXTRA_TOKEN).orEmpty())
    }
}
