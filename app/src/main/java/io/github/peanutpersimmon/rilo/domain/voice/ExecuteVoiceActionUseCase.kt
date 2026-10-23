package io.github.peanutpersimmon.rilo.domain.voice

import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.repository.RidoCoreRepository
import io.github.peanutpersimmon.rilo.domain.usecase.*
import io.github.peanutpersimmon.rilo.domain.service.CategoryKeywordNormalizer
import io.github.peanutpersimmon.rilo.domain.service.ResolvedClassification
import java.time.ZoneId
import io.github.peanutpersimmon.rilo.domain.time.toMinutePrecision

sealed interface VoiceExecutionResult {
    data class Executed(val receipt: VoiceExecutionReceipt) : VoiceExecutionResult
    data class Fallback(val prepared: VoicePrepared) : VoiceExecutionResult
}

/** Preparation and writes occur in one repository transaction; all saves still use existing business operations. */
class ExecuteVoiceActionUseCase(private val repository: RidoCoreRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val trace: (String, String) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis) {
    suspend operator fun invoke(d: VoiceActionDraft, targetId: Long? = null,
        attempt: VoiceExecutionAttempt = VoiceExecutionAttempt()): VoiceExecutionResult =
        attempt.run { perform(d, targetId) }

    private suspend fun perform(d: VoiceActionDraft, targetId: Long?): VoiceExecutionResult = try {
        repository.voiceTransaction {
            val eventAction = d.action in setOf(VoiceAction.UpdateEvent, VoiceAction.CreateRecordFromEvent)
            val recordAction = d.action in setOf(VoiceAction.UpdateRecord, VoiceAction.StopRecord, VoiceAction.FinishSleep)
            val event = if (eventAction) targetId?.let { repository.getEvent(it) } else null
            val record = if (recordAction) targetId?.let { repository.getSession(it) } else null
            if (eventAction && event == null || recordAction && record == null)
                return@voiceTransaction VoiceExecutionResult.Fallback(VoicePrepared(error = "内容已不存在，请重新输入"))
            val classifiedCreate = d.action in setOf(VoiceAction.CreateEvent,
                VoiceAction.CreateRecord, VoiceAction.StartRecord)
            val categoryUpdate = d.fields.categoryUpdate
            val classification = if (d.action in setOf(VoiceAction.UpdateEvent, VoiceAction.UpdateRecord) &&
                categoryUpdate is VoiceFieldPatch.Set) {
                val normalized = CategoryKeywordNormalizer.normalize(categoryUpdate.value)
                val matches = repository.getCategories().filter {
                    CategoryKeywordNormalizer.normalize(it.name) == normalized
                }
                if (matches.size != 1) return@voiceTransaction VoiceExecutionResult.Fallback(
                    VoicePrepared(error = if (matches.isEmpty()) "未找到分类「${categoryUpdate.value}」，请确认分类名称"
                        else "分类名称不唯一，请确认要使用的分类",
                        clarification = VoiceClarification.CategoryUnresolved))
                ResolvedClassification(matches.single().id)
            } else if (classifiedCreate) {
                val categoryHint = d.fields.categoryHint
                if (categoryHint.isNullOrBlank()) {
                    InitialClassificationUseCase(repository)(InitialClassificationInput(
                        fTitle(d),
                        applyKeywordRules = true,
                    ))
                } else {
                    CategoryHintResolver.resolve(
                        categoryHint = categoryHint,
                        categories = repository.getCategories(),
                        rules = repository.getEnabledKeywordRules(),
                    )
                }
            } else null
            val plan = VoiceExecutionPlanner.prepare(d, event, record, classification, zone, now())
            trace("planner", "action=${d.action}; event=${plan.event != null}; recordKind=${plan.record?.kind}; temporalType=${plan.record?.temporalType}; missing=${plan.missing}; error=${plan.error}")
            if (!plan.canExecuteDirectly) {
                trace("reason", plan.error ?: "missing:${plan.missing}")
                return@voiceTransaction VoiceExecutionResult.Fallback(plan)
            }
            trace("executionAction", d.action.name)
            val before = VoiceDataSnapshot(if (d.action == VoiceAction.UpdateEvent) listOfNotNull(event) else emptyList(), listOfNotNull(record))
            var eventId: Long? = null
            var recordId: Long? = record?.id
            when (d.action) {
                VoiceAction.CreateEvent, VoiceAction.UpdateEvent -> plan.event!!.let { eventId = SaveEventUseCase(repository)(it).requireVoiceSuccess() }
                VoiceAction.StopRecord ->
                    repository.stopConfirmedSession(record!!.id,
                        (d.fields.endAt ?: d.nowMillis).toMinutePrecision()).requireVoiceSuccess()
                VoiceAction.FinishSleep ->
                    repository.finishConfirmedSleepCheckIn(record!!.id,
                        (d.fields.endAt ?: d.nowMillis).toMinutePrecision(), zone).requireVoiceSuccess()
                VoiceAction.UpdateRecord -> plan.record!!.let { SaveSessionEditUseCase(repository)(it).requireVoiceSuccess() }
                VoiceAction.CreateSleepRecord, VoiceAction.StartSleep -> {
                    val s = plan.record!!
                    val input = NewSessionInput(
                        title = s.title,
                        startAtMillis = s.startAtMillis,
                        endAtMillis = s.endAtMillis,
                        categoryId = null,
                        note = s.note,
                        location = s.location,
                        kind = s.kind,
                        createdAtMillis = s.createdAtMillis,
                        sourceEventId = null,
                    )
                    val sleep = CreateSleepSessionUseCase(repository)
                    recordId = if (d.action == VoiceAction.StartSleep) sleep.start(input).requireVoiceSuccess()
                        else sleep.create(input).requireVoiceSuccess()
                }
                VoiceAction.CreateRecord, VoiceAction.StartRecord, VoiceAction.CreateRecordFromEvent -> {
                    val s = plan.record!!
                    val input = NewSessionInput(s.title, s.startAtMillis, s.endAtMillis, s.categoryId,
                        s.note, s.location, s.kind, s.createdAtMillis, sourceEventId = s.sourceEventId,
                        temporalType = s.temporalType)
                    recordId = if (d.action == VoiceAction.StartRecord) StartSessionUseCase(repository)(input, requireIdle = true).requireVoiceSuccess()
                        else CreatePastSessionUseCase(repository)(input).requireVoiceSuccess()
                }
                VoiceAction.Clarify -> return@voiceTransaction VoiceExecutionResult.Fallback(VoicePrepared(error = "请先澄清输入"))
            }
            val after = VoiceDataSnapshot(listOfNotNull(eventId?.let { repository.getEvent(it) }), listOfNotNull(recordId?.let { repository.getSession(it) }))
            val savedType = if (after.events.isNotEmpty()) "EVENT" else when (after.records.firstOrNull()?.kind) {
                SessionKind.NAP, SessionKind.NIGHT_SLEEP -> "SLEEP"
                SessionKind.ACTIVITY -> "RECORD"
                null -> "NONE"
            }
            trace("savedDomainType", savedType)
            val title = plan.event?.title ?: plan.record?.title ?: d.targetText
            val verb = when (d.action) {
                VoiceAction.CreateEvent -> "已创建"
                VoiceAction.CreateRecord, VoiceAction.CreateRecordFromEvent -> "已创建"
                VoiceAction.CreateSleepRecord -> "已创建"
                VoiceAction.StartRecord, VoiceAction.StartSleep -> "已开始"
                VoiceAction.StopRecord, VoiceAction.FinishSleep -> "已结束"
                else -> "已修改"
            }
            val changes = mutableListOf<String>()
            if (d.action == VoiceAction.UpdateEvent) when (val reminder = d.fields.reminderUpdate) {
                VoiceFieldPatch.Keep -> Unit
                VoiceFieldPatch.Clear -> changes += "已取消提醒"
                is VoiceFieldPatch.Set -> changes += if (reminder.value == 0) "已改为准时提醒"
                    else "已将提醒改为提前${reminder.value}分钟"
            }
            if (d.action in setOf(VoiceAction.UpdateEvent, VoiceAction.UpdateRecord))
                when (val category = d.fields.categoryUpdate) {
                    VoiceFieldPatch.Keep -> Unit
                    VoiceFieldPatch.Clear -> changes += "已清除分类"
                    is VoiceFieldPatch.Set -> changes += "已将分类改为${category.value}"
                }
            val message = if (changes.isEmpty()) "$verb「$title」"
                else "$verb「$title」：${changes.joinToString("；")}"
            VoiceExecutionResult.Executed(VoiceExecutionReceipt(message, VoiceUndo(before, after)))
        }
    } catch (e: VoiceWriteRejected) { VoiceExecutionResult.Fallback(VoicePrepared(error = when (val error = e.error) {
        is DomainError.DataIntegrity -> error.reason
        DomainError.SessionOverlap -> "时间与已有记录重叠"
        else -> "业务校验未通过，请修改输入或打开编辑器"
    }).also { trace("reason", it.error ?: "write_rejected") }) }
    private fun fTitle(d: VoiceActionDraft) = d.fields.title.applyTo(null) ?: d.targetText
}
