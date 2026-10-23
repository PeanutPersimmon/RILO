package io.github.peanutpersimmon.rilo.domain.voice

import io.github.peanutpersimmon.rilo.domain.model.*
import io.github.peanutpersimmon.rilo.domain.repository.*
import io.github.peanutpersimmon.rilo.domain.service.SleepKindResolver
import io.github.peanutpersimmon.rilo.domain.voice.agent.QwenToolCallMapper
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentContext
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import io.github.peanutpersimmon.rilo.domain.usecase.CreatePastSessionUseCase
import io.github.peanutpersimmon.rilo.domain.usecase.NewSessionInput
import io.github.peanutpersimmon.rilo.domain.validation.DataValidator
import io.github.peanutpersimmon.rilo.reminder.ReminderPlan

class VoiceExecutionTest {
    private val now = Instant.parse("2026-09-22T10:00:00Z")
    private val zone = ZoneId.of("Asia/Shanghai")
    private fun draft(text: String): VoiceActionDraft {
        val command = when (text) {
            "拿到快递了" -> "create_record" to """{"title":"拿快递","temporal_type":"POINT","occurred_at":"2026-09-22T18:00:00+08:00"}"""
            "今晚19-20在父亲楼有一节选修课" -> "create_event" to """{"title":"选修课","temporal_type":"INTERVAL","start_at":"2026-09-22T19:00:00+08:00","end_at":"2026-09-22T20:00:00+08:00","location":"父亲楼"}"""
            "明天有组会" -> "create_event" to """{"title":"组会","temporal_type":"DATE","date":"2026-09-23"}"""
            "开始写论文" -> "start_record" to """{"title":"写论文"}"""
            "开始跑步" -> "start_record" to """{"title":"跑步"}"""
            "午睡了半小时" -> "create_sleep_record" to """{"sleep_type":"nap","start_at":"2026-09-22T12:00:00+08:00","end_at":"2026-09-22T12:30:00+08:00"}"""
            "开始午睡" -> "start_sleep" to """{"sleep_type":"nap"}"""
            "把选修课记成记录" -> "create_record_from_event" to """{"event_query":"选修课","temporal_type":"INTERVAL","actual_start_at":"2026-09-22T14:00:00+08:00","actual_end_at":"2026-09-22T15:00:00+08:00"}"""
            "记录明天下午三点到四点组会" -> "create_record" to """{"title":"组会","temporal_type":"INTERVAL","start_at":"2026-09-23T15:00:00+08:00","end_at":"2026-09-23T16:00:00+08:00"}"""
            else -> error("No structured fixture for $text")
        }
        return QwenToolCallMapper().map(command.first, command.second, text, VoiceAgentContext(now, zone))
    }
    private fun executor(repo: MemoryVoiceRepository) = ExecuteVoiceActionUseCase(repo, zone) { now.toEpochMilli() }
    private fun update(name: String, json: String, text: String = "修改内容") =
        QwenToolCallMapper().map(name, json, text, VoiceAgentContext(now, zone))


    @Test fun sleepCreationUsesNapBoundarySupportsUndoAndProtectsActiveSession() = runBlocking {
        val repo = MemoryVoiceRepository().apply {
            rules = listOf(KeywordRule(keyword = "午睡", categoryId = 9,
                createdAtMillis = 0, updatedAtMillis = 0))
        }
        val completed = executor(repo)(draft("午睡了半小时")) as VoiceExecutionResult.Executed
        val nap = repo.records.values.single()
        assertEquals(SessionKind.NAP, nap.kind)
        assertNull(nap.categoryId)
        assertEquals(0, repo.ruleReads)
        assertTrue(UndoVoiceActionUseCase(repo)(completed.receipt.undo) is DomainResult.Success)
        assertTrue(repo.records.isEmpty())

        val started = executor(repo)(draft("开始午睡")) as VoiceExecutionResult.Executed
        assertNull(repo.records.values.single().endAtMillis)
        assertEquals("已开始「小憩」", started.receipt.message)
        assertTrue(executor(repo)(draft("开始午睡")) is VoiceExecutionResult.Fallback)
        assertEquals(1, repo.records.size)
    }

    @Test fun voiceRecordAndSleepWritesConvergeToMinutePrecision() = runBlocking {
        val context = VoiceAgentContext(Instant.parse("2026-09-22T10:00:37.456Z"), zone)
        val mapper = QwenToolCallMapper()

        val recordRepo = MemoryVoiceRepository()
        val createRecord = mapper.map("create_record", """{
            "title":"带秒记录","temporal_type":"INTERVAL",
            "start_at":"2026-09-22T17:00:45.123456789+08:00",
            "end_at":"2026-09-22T17:30:59.999999999+08:00"
        }""", "刚才做了记录", context)
        val createRecordResult = ExecuteVoiceActionUseCase(recordRepo, zone,
            now = { context.now.toEpochMilli() })(createRecord)
        assertTrue(createRecordResult.toString(), createRecordResult is VoiceExecutionResult.Executed)
        val record = recordRepo.records.values.single()
        assertEquals(0L, Math.floorMod(record.startAtMillis, 60_000L))
        assertEquals(0L, Math.floorMod(requireNotNull(record.endAtMillis), 60_000L))

        val adjacent = CreatePastSessionUseCase(recordRepo)(NewSessionInput(
            title = "紧邻记录",
            startAtMillis = requireNotNull(record.endAtMillis),
            endAtMillis = requireNotNull(record.endAtMillis) + 30 * 60_000L,
        ))
        assertTrue(adjacent is DomainResult.Success)
        assertFalse(DataValidator.sessionsOverlap(
            record.startAtMillis, record.endAtMillis,
            requireNotNull(record.endAtMillis), requireNotNull(record.endAtMillis) + 30 * 60_000L,
        ))

        val activeRecordRepo = MemoryVoiceRepository()
        val startRecord = mapper.map("start_record", """{"title":"开始记录"}""",
            "开始记录", context)
        assertTrue(ExecuteVoiceActionUseCase(activeRecordRepo, zone,
            now = { context.now.toEpochMilli() })(startRecord) is VoiceExecutionResult.Executed)
        assertEquals(0L, Math.floorMod(activeRecordRepo.records.values.single().startAtMillis, 60_000L))
        val activeRecord = activeRecordRepo.records.values.single()
        val stop = VoiceActionDraft(VoiceAction.StopRecord, activeRecord.title,
            VoiceFields(endAt = context.now.plusSeconds(125).toEpochMilli()), "结束记录",
            context.now.plusSeconds(125).toEpochMilli())
        assertTrue(ExecuteVoiceActionUseCase(activeRecordRepo, zone,
            now = { context.now.plusSeconds(125).toEpochMilli() })(stop, activeRecord.id) is VoiceExecutionResult.Executed)
        assertEquals(0L, Math.floorMod(requireNotNull(activeRecordRepo.records[activeRecord.id]?.endAtMillis), 60_000L))

        val sleepRepo = MemoryVoiceRepository()
        val sleep = mapper.map("create_sleep_record", """{
            "sleep_type":"nap","start_at":"2026-09-22T17:20:12.500+08:00",
            "end_at":"2026-09-22T17:50:48.750+08:00"
        }""", "睡了一会", context)
        assertTrue(ExecuteVoiceActionUseCase(sleepRepo, zone,
            now = { context.now.toEpochMilli() })(sleep) is VoiceExecutionResult.Executed)
        sleepRepo.records.values.single().let {
            assertEquals(0L, Math.floorMod(it.startAtMillis, 60_000L))
            assertEquals(0L, Math.floorMod(requireNotNull(it.endAtMillis), 60_000L))
        }

        val activeSleepRepo = MemoryVoiceRepository()
        val sleepStart = context.now.minusSeconds(3600).toEpochMilli()
        activeSleepRepo.records[7] = Session(id = 7, title = "睡眠", kind = SessionKind.NAP,
            startAtMillis = sleepStart - Math.floorMod(sleepStart, 60_000L),
            createdAtMillis = sleepStart, updatedAtMillis = sleepStart)
        val finish = mapper.map("finish_sleep", "{}", "醒了", context)
        assertTrue(ExecuteVoiceActionUseCase(activeSleepRepo, zone,
            now = { context.now.toEpochMilli() })(finish, 7) is VoiceExecutionResult.Executed)
        assertEquals(0L, Math.floorMod(requireNotNull(activeSleepRepo.records[7]?.endAtMillis), 60_000L))
    }

    @Test fun actualFactCreatesOnlyPointRecordAndUndoDeletesIt() = runBlocking {
        val repo = MemoryVoiceRepository()
        val command = draft("拿到快递了")
        val attempt = VoiceExecutionAttempt()
        val result = executor(repo)(command, attempt = attempt) as VoiceExecutionResult.Executed
        assertSame(result, executor(repo)(command, attempt = attempt))
        assertEquals(SessionTemporalType.POINT, repo.records.values.single().temporalType)
        assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
        assertTrue(repo.records.isEmpty())
    }
    @Test fun coreSentenceExecutesExactlyOneEventWriteAndCanBeUndone() = runBlocking {
        val repo = MemoryVoiceRepository()
        val result = executor(repo)(draft("今晚19-20在父亲楼有一节选修课")) as VoiceExecutionResult.Executed
        assertEquals(1, repo.eventWrites)
        val event = repo.events.values.single()
        assertEquals("选修课", event.title)
        assertEquals("父亲楼", event.location)
        assertEquals(1140, event.startMinuteOfDay)
        assertEquals(1200, event.endMinuteOfDay)
        assertEquals(now.atZone(zone).toLocalDate().toEpochDay(), event.dateEpochDay)
        assertEquals("已创建「选修课」", result.receipt.message)
        assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
        assertTrue(repo.events.isEmpty())
    }
    @Test fun crossingMidnightToolCallWritesExplicitNextDayAndCanUndo() = runBlocking {
        val repo = MemoryVoiceRepository()
        val command = QwenToolCallMapper().map("create_event", """{
            "title":"看电影",
            "temporal_type":"INTERVAL",
            "start_at":"2026-09-24T22:00:00+08:00",
            "end_at":"2026-09-25T00:00:00+08:00"
        }""", "今晚十点到十二点看电影", VoiceAgentContext(now, zone))
        val result = executor(repo)(command) as VoiceExecutionResult.Executed
        val saved = repo.events.values.single()
        assertEquals(java.time.LocalDate.of(2026, 9, 24).toEpochDay(), saved.dateEpochDay)
        assertEquals(1320, saved.startMinuteOfDay)
        assertEquals(java.time.LocalDate.of(2026, 9, 25).toEpochDay(), saved.endDateEpochDay)
        assertEquals(0, saved.endMinuteOfDay)
        assertTrue(UndoVoiceActionUseCase(repo)(result.receipt.undo) is DomainResult.Success)
        assertTrue(repo.events.isEmpty())
    }
    @Test fun repeatedUseCaseCallForSameAttemptWritesOnce() = runBlocking {
        val repo = MemoryVoiceRepository()
        val attempt = VoiceExecutionAttempt()
        val command = draft("今晚19-20在父亲楼有一节选修课")
        val first = executor(repo)(command, attempt = attempt)
        val second = executor(repo)(command, attempt = attempt)
        assertSame(first, second)
        assertEquals(1, repo.eventWrites)
        assertEquals(1, repo.events.size)
    }
    @Test fun startRecordIsExecutableAndExistingActiveRecordIsProtected() = runBlocking {
        val repo = MemoryVoiceRepository()
        assertTrue(executor(repo)(draft("开始写论文")) is VoiceExecutionResult.Executed)
        assertTrue(executor(repo)(draft("开始跑步")) is VoiceExecutionResult.Fallback)
        assertNull(repo.records.values.single().endAtMillis)
    }
    @Test fun finishSleepOnlyEndsActiveSleepAndReevaluatesItsKind() = runBlocking {
        val repo = MemoryVoiceRepository()
        val start = now.minusSeconds(60 * 60).toEpochMilli()
        repo.records[7] = Session(id = 7, title = "睡眠", kind = SessionKind.NIGHT_SLEEP,
            temporalType = SessionTemporalType.INTERVAL, startAtMillis = start,
            createdAtMillis = start, updatedAtMillis = start)
        val finish = QwenToolCallMapper().map("finish_sleep", "{}", "刚睡醒", VoiceAgentContext(now, zone))
        assertEquals(7L, VoiceTargetResolver(repo, zone).targets(finish).single().id)
        val stages = mutableMapOf<String, String>()
        val result = ExecuteVoiceActionUseCase(repo, zone, trace = { stage, value -> stages[stage] = value },
            now = { now.toEpochMilli() })(finish, targetId = 7)
        assertTrue(result is VoiceExecutionResult.Executed)
        assertEquals(SessionKind.NAP, repo.records[7]?.kind)
        assertEquals(now.toEpochMilli(), repo.records[7]?.endAtMillis)
        assertEquals("SLEEP", stages["savedDomainType"])

        val activity = MemoryVoiceRepository().apply { records[8] = Session(id = 8, title = "看论文",
            startAtMillis = start, createdAtMillis = start, updatedAtMillis = start) }
        assertTrue(VoiceTargetResolver(activity, zone).targets(finish).isEmpty())
        assertTrue(executor(activity)(finish, targetId = 8) is VoiceExecutionResult.Fallback)
        assertNull(activity.records[8]?.endAtMillis)
        assertTrue(VoiceTargetResolver(MemoryVoiceRepository(), zone).targets(finish).isEmpty())
    }
    @Test fun eventCopyRetainsSourceId() = runBlocking {
        val repo = MemoryVoiceRepository()
        repo.events[9] = Event(id = 9, title = "选修课", dateEpochDay = now.atZone(zone).toLocalDate().toEpochDay(),
            startMinuteOfDay = 840, endMinuteOfDay = 900, location = "父亲楼", createdAtMillis = 0, updatedAtMillis = 0)
        assertTrue(executor(repo)(draft("把选修课记成记录"), 9) is VoiceExecutionResult.Executed)
        assertEquals(9L, repo.records.values.single().sourceEventId)
        assertEquals("父亲楼", repo.records.values.single().location)
        assertEquals(840, repo.events[9]!!.startMinuteOfDay)
    }

    @Test fun eventCopyWithoutActualTimeNeverWritesRecord() = runBlocking {
        val repo = MemoryVoiceRepository()
        repo.events[9] = Event(id = 9, title = "选修课",
            dateEpochDay = now.atZone(zone).toLocalDate().toEpochDay(),
            startMinuteOfDay = 840, endMinuteOfDay = 900, createdAtMillis = 0, updatedAtMillis = 0)
        val mapper = QwenToolCallMapper()
        val context = VoiceAgentContext(now, zone)
        val point = mapper.map("create_record_from_event",
            """{"event_query":"选修课","temporal_type":"POINT"}""", "记下选修课", context)
        val pointResult = executor(repo)(point, 9) as VoiceExecutionResult.Fallback
        assertTrue(pointResult.prepared.missing.contains(VoiceMissingField.Start))
        assertTrue(repo.records.isEmpty())

        val interval = mapper.map("create_record_from_event",
            """{"event_query":"选修课","temporal_type":"INTERVAL"}""", "记下选修课", context)
        val intervalResult = executor(repo)(interval, 9) as VoiceExecutionResult.Fallback
        assertTrue(intervalResult.prepared.missing.contains(VoiceMissingField.Start))
        assertTrue(intervalResult.prepared.missing.contains(VoiceMissingField.End))
        assertTrue(repo.records.isEmpty())
    }

    @Test fun eventCopyUsesOnlyExplicitActualBoundaries() = runBlocking {
        val repo = MemoryVoiceRepository()
        repo.events[9] = Event(id = 9, title = "选修课",
            dateEpochDay = now.atZone(zone).toLocalDate().toEpochDay(),
            startMinuteOfDay = 840, endMinuteOfDay = 900, createdAtMillis = 0, updatedAtMillis = 0)
        val command = QwenToolCallMapper().map("create_record_from_event", """{
            "event_query":"选修课","temporal_type":"INTERVAL",
            "actual_start_at":"2026-09-22T14:18:00+08:00",
            "actual_end_at":"2026-09-22T15:37:00+08:00"
        }""", "记下选修课", VoiceAgentContext(now, zone))
        assertTrue(executor(repo)(command, 9) is VoiceExecutionResult.Executed)
        val saved = repo.records.values.single()
        assertEquals(SessionTemporalType.INTERVAL, saved.temporalType)
        assertEquals(Instant.parse("2026-09-22T06:18:00Z").toEpochMilli(), saved.startAtMillis)
        assertEquals(Instant.parse("2026-09-22T07:37:00Z").toEpochMilli(), saved.endAtMillis)
        assertEquals(9L, saved.sourceEventId)
    }

    @Test fun sourceEventResolverFindsTodayYesterdayAndOlderDatesWithoutGuessing() = runBlocking {
        val repo = MemoryVoiceRepository()
        val today = now.atZone(zone).toLocalDate()
        listOf(today, today.minusDays(1), today.minusDays(30)).forEachIndexed { index, date ->
            val id = index + 1L
            repo.events[id] = Event(id = id, title = "组会", dateEpochDay = date.toEpochDay(),
                startMinuteOfDay = 840, endMinuteOfDay = 900, createdAtMillis = 0, updatedAtMillis = 0)
        }
        val broad = draft("把选修课记成记录").copy(targetText = "组会")
        assertEquals(3, VoiceTargetResolver(repo, zone).targets(broad).size)
        assertNull(VoiceTargetMatching.unique(VoiceTargetResolver(repo, zone).targets(broad)))
        val dated = broad.copy(targetDate = today.minusDays(30))
        assertEquals(3L, VoiceTargetResolver(repo, zone).targets(dated).single().id)
        assertEquals(2L, VoiceTargetResolver(repo, zone).targets(broad.copy(targetDate = today.minusDays(1))).single().id)
        assertEquals(1L, VoiceTargetResolver(repo, zone).targets(broad.copy(targetDate = today)).single().id)
        val missing = broad.copy(targetDate = today.minusYears(1))
        assertTrue(VoiceTargetResolver(repo, zone).targets(missing).isEmpty())
        assertTrue(executor(repo)(missing) is VoiceExecutionResult.Fallback)
        assertTrue(executor(repo)(broad) is VoiceExecutionResult.Fallback)
        assertTrue(repo.records.isEmpty())
    }

    @Test fun dateOnlyPastFactCannotWriteWithoutClockTime() = runBlocking {
        val repo = MemoryVoiceRepository()
        val d = QwenToolCallMapper().map("request_clarification", """{
            "reason":"RECORD_MISSING_TIME","record_title":"取快递","known_date":"2026-09-21"
        }""", "昨天取快递了", VoiceAgentContext(now, zone))
        assertEquals(VoiceClarification.RecordMissingTime,
            (VoiceIntentPolicy.evaluate(d, zone) as VoiceIntentDecision.NeedsClarification).reason)
        assertTrue(executor(repo)(d) is VoiceExecutionResult.Fallback)
        assertTrue(repo.records.isEmpty())
        assertTrue(repo.events.isEmpty())
    }

    @Test fun emptyAudioTextStillClassifiesFromHintAndMissingHintFallsBackToTitle() = runBlocking {
        val hintedRepo = MemoryVoiceRepository().apply {
            categories = listOf(Category(7, "科研", sortOrder = 0, createdAtMillis = 0, updatedAtMillis = 0))
            rules = listOf(KeywordRule(
                id = 11,
                keyword = "实验",
                categoryId = 7,
                createdAtMillis = 0,
                updatedAtMillis = 0,
            ))
        }
        val audioDraft = QwenToolCallMapper().map(
            "create_event",
            """{"title":"做实验","temporal_type":"DATE","date":"2026-09-23","category_hint":"实验"}""",
            "",
            VoiceAgentContext(now, zone),
        )
        assertTrue(executor(hintedRepo)(audioDraft) is VoiceExecutionResult.Executed)
        assertEquals(7L, hintedRepo.events.values.single().categoryId)

        val fallbackRepo = MemoryVoiceRepository().apply {
            categories = hintedRepo.categories
            rules = hintedRepo.rules.map { it.copy(keyword = "论文") }
        }
        val noHintDraft = QwenToolCallMapper().map(
            "create_event",
            """{"title":"写论文","temporal_type":"DATE","date":"2026-09-23"}""",
            "",
            VoiceAgentContext(now, zone),
        )
        assertTrue(executor(fallbackRepo)(noHintDraft) is VoiceExecutionResult.Executed)
        assertEquals(7L, fallbackRepo.events.values.single().categoryId)
    }

    @Test fun eventReminderKeepSetZeroClearAndTimeEditPreserveOtherFields() = runBlocking {
        val repo = MemoryVoiceRepository()
        val day = now.atZone(zone).toLocalDate().plusDays(1)
        repo.events[9] = Event(id = 9, title = "组会", dateEpochDay = day.toEpochDay(),
            startMinuteOfDay = 15 * 60, startReminderOffsetMinutes = 10,
            categoryId = 7, createdAtMillis = 0, updatedAtMillis = 0)
        val keep = executor(repo)(update("update_event",
            """{"target_query":"组会","start_at":"2026-09-23T16:00:00+08:00"}"""), 9)
        assertTrue(keep is VoiceExecutionResult.Executed)
        assertEquals(10, repo.events[9]?.startReminderOffsetMinutes)
        assertEquals(7L, repo.events[9]?.categoryId)
        assertEquals(16 * 60, repo.events[9]?.startMinuteOfDay)
        val originalTrigger = ReminderPlan.forEvent(repo.events[9]!!, zone).single().triggerMillis

        val set = executor(repo)(update("update_event",
            """{"target_query":"组会","reminder_offset_minutes":30}"""), 9)
        assertTrue(set is VoiceExecutionResult.Executed)
        assertTrue((set as VoiceExecutionResult.Executed).receipt.message.contains("提前30分钟"))
        assertEquals(30, repo.events[9]?.startReminderOffsetMinutes)
        assertNotEquals(originalTrigger, ReminderPlan.forEvent(repo.events[9]!!, zone).single().triggerMillis)
        assertEquals(7L, repo.events[9]?.categoryId)

        assertTrue(executor(repo)(update("update_event",
            """{"target_query":"组会","reminder_offset_minutes":0}"""), 9) is VoiceExecutionResult.Executed)
        assertEquals(0, repo.events[9]?.startReminderOffsetMinutes)
        assertEquals(ReminderPlan.forEvent(repo.events[9]!!, zone).single().anchorMillis,
            ReminderPlan.forEvent(repo.events[9]!!, zone).single().triggerMillis)

        val clear = executor(repo)(update("update_event",
            """{"target_query":"组会","clear_fields":["reminder"]}"""), 9)
        assertTrue(clear is VoiceExecutionResult.Executed)
        assertTrue((clear as VoiceExecutionResult.Executed).receipt.message.contains("已取消提醒"))
        assertNull(repo.events[9]?.startReminderOffsetMinutes)
        assertTrue(ReminderPlan.forEvent(repo.events[9]!!, zone).isEmpty())
        assertEquals(4, repo.eventWrites)
    }

    @Test fun updateCategoryRequiresUniqueExistingNameAndClearIsExplicit() = runBlocking {
        val repo = MemoryVoiceRepository().apply {
            categories = listOf(Category(7, "工作", createdAtMillis = 0, updatedAtMillis = 0),
                Category(8, "科研", createdAtMillis = 0, updatedAtMillis = 0))
            events[9] = Event(id = 9, title = "组会", dateEpochDay = now.atZone(zone).toLocalDate().plusDays(1).toEpochDay(),
                categoryId = 7, createdAtMillis = 0, updatedAtMillis = 0)
            records[10] = Session(id = 10, title = "实验", startAtMillis = now.minusSeconds(3600).toEpochMilli(),
                categoryId = 7, createdAtMillis = 0, updatedAtMillis = 0)
        }
        assertTrue(executor(repo)(update("update_event",
            """{"target_query":"组会","category_hint":"科研"}"""), 9) is VoiceExecutionResult.Executed)
        assertEquals(8L, repo.events[9]?.categoryId)
        assertTrue(executor(repo)(update("update_record",
            """{"target_query":"实验","category_hint":"科研"}"""), 10) is VoiceExecutionResult.Executed)
        assertEquals(8L, repo.records[10]?.categoryId)
        assertTrue(executor(repo)(update("update_event",
            """{"target_query":"组会","title":"新组会"}"""), 9) is VoiceExecutionResult.Executed)
        assertEquals(8L, repo.events[9]?.categoryId)
        assertTrue(executor(repo)(update("update_record",
            """{"target_query":"实验","title":"新实验"}"""), 10) is VoiceExecutionResult.Executed)
        assertEquals(8L, repo.records[10]?.categoryId)

        val absent = executor(repo)(update("update_event",
            """{"target_query":"新组会","category_hint":"不存在"}"""), 9)
        assertTrue(absent is VoiceExecutionResult.Fallback)
        assertEquals(VoiceClarification.CategoryUnresolved,
            (absent as VoiceExecutionResult.Fallback).prepared.clarification)
        assertEquals(8L, repo.events[9]?.categoryId)
        repo.categories = repo.categories + Category(11, " 科研 ", createdAtMillis = 0, updatedAtMillis = 0)
        val ambiguous = executor(repo)(update("update_record",
            """{"target_query":"新实验","category_hint":"科研"}"""), 10)
        assertTrue(ambiguous is VoiceExecutionResult.Fallback)
        assertEquals(VoiceClarification.CategoryUnresolved,
            (ambiguous as VoiceExecutionResult.Fallback).prepared.clarification)
        assertEquals(8L, repo.records[10]?.categoryId)

        assertTrue(executor(repo)(update("update_event",
            """{"target_query":"新组会","clear_fields":["category"]}"""), 9) is VoiceExecutionResult.Executed)
        assertNull(repo.events[9]?.categoryId)
        assertTrue(executor(repo)(update("update_record",
            """{"target_query":"新实验","clear_fields":["category"]}"""), 10) is VoiceExecutionResult.Executed)
        assertNull(repo.records[10]?.categoryId)
    }

    @Test fun failedEventWriteDoesNotProduceVoiceSuccessReceipt() = runBlocking {
        val repo = MemoryVoiceRepository().apply {
            rejectEventWrite = true
            events[9] = Event(id = 9, title = "组会", dateEpochDay = now.atZone(zone).toLocalDate().plusDays(1).toEpochDay(),
                startMinuteOfDay = 900, startReminderOffsetMinutes = 10,
                createdAtMillis = 0, updatedAtMillis = 0)
        }
        val result = executor(repo)(update("update_event",
            """{"target_query":"组会","reminder_offset_minutes":30}"""), 9)
        assertTrue(result is VoiceExecutionResult.Fallback)
        assertEquals(10, repo.events[9]?.startReminderOffsetMinutes)
    }
}

/** In-memory test boundary; production uses Room transactions and its existing validators. */
private class MemoryVoiceRepository : RidoCoreRepository {
    val events = mutableMapOf<Long, Event>()
    val records = mutableMapOf<Long, Session>()
    var eventWrites = 0
    var rejectEventWrite = false
    var rules = emptyList<KeywordRule>()
    var categories = emptyList<Category>()
    var ruleReads = 0
    override suspend fun <T> voiceTransaction(block: suspend () -> T): T {
        val e = events.toMap(); val r = records.toMap()
        return try { block() } catch (error: Throwable) {
            events.clear(); events.putAll(e); records.clear(); records.putAll(r); throw error
        }
    }
    override fun observeDataChanges() = flowOf(Unit)
    override fun observeActiveSession() = flowOf(records.values.singleOrNull {
        it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis == null })
    override suspend fun getEvent(eventId: Long) = events[eventId]
    override suspend fun getEventsByIds(eventIds: Set<Long>) =
        eventIds.mapNotNull(events::get).map { it }
    override suspend fun getSession(sessionId: Long) = records[sessionId]
    override suspend fun getEnabledKeywordRules(): List<KeywordRule> { ruleReads++; return rules }
    override suspend fun getCategories() = categories
    override suspend fun getNightSleeps() = emptyList<Session>()
    override suspend fun getSessions(rangeStartMillis: Long, rangeEndMillis: Long) = records.values.toList()
    override suspend fun getEvents(firstEpochDay: Long, lastEpochDay: Long, rangeStartMillis: Long, rangeEndMillis: Long) =
        events.values.filter { it.dateEpochDay in firstEpochDay..lastEpochDay }
    override suspend fun upsertEvent(event: Event): DomainResult<Long> {
        if (rejectEventWrite) return DomainResult.Failure(DomainError.DataIntegrity("测试写入失败"))
        val id = event.id.takeIf { it != 0L } ?: ((events.keys.maxOrNull() ?: 0) + 1)
        events[id] = event.copy(id = id); eventWrites++; return DomainResult.Success(id)
    }
    override suspend fun deleteEvent(eventId: Long) = DomainResult.Success(DeletedEventSnapshot(events.remove(eventId)!!))
    override suspend fun deleteSession(sessionId: Long): DomainResult<Unit> { records.remove(sessionId); return DomainResult.Success(Unit) }
    override suspend fun deleteSessionWithSnapshot(sessionId: Long) = DomainResult.Success(DeletedSessionSnapshot(records.remove(sessionId)!!))
    override suspend fun restoreEvent(snapshot: DeletedEventSnapshot): DomainResult<Unit> { events[snapshot.record.id] = snapshot.record; return DomainResult.Success(Unit) }
    override suspend fun restoreSession(snapshot: DeletedSessionSnapshot): DomainResult<Unit> { records[snapshot.record.id] = snapshot.record; return DomainResult.Success(Unit) }
    override suspend fun startSessionIfIdle(session: Session): DomainResult<Long> =
        if (records.values.any { it.endAtMillis == null }) DomainResult.Failure(DomainError.SessionOverlap) else startSession(session)
    override suspend fun startSession(session: Session): DomainResult<Long> = createPastSession(session)
    override suspend fun createPastSession(session: Session): DomainResult<Long> {
        val id = (records.keys.maxOrNull() ?: 0) + 1; records[id] = session.copy(id = id); return DomainResult.Success(id)
    }
    override suspend fun updateSession(session: Session): DomainResult<Unit> {
        records[session.id] = session; return DomainResult.Success(Unit)
    }
    override suspend fun stopActiveSession(endAtMillis: Long): DomainResult<Session> {
        val old = records.values.single { it.endAtMillis == null }
        val changed = old.copy(endAtMillis = endAtMillis); records[changed.id] = changed; return DomainResult.Success(changed)
    }
    override suspend fun stopConfirmedSession(id: Long, end: Long): DomainResult<Session> {
        val active = records[id] ?: return DomainResult.Failure(DomainError.NotFound("Session", id))
        if (active.endAtMillis != null)
            return DomainResult.Failure(DomainError.NotFound("Session", id))
        val stopped = active.copy(endAtMillis = end)
        records[id] = stopped
        return DomainResult.Success(stopped)
    }
    override suspend fun finishSleepCheckIn(endAtMillis: Long, zoneId: ZoneId): DomainResult<Session> {
        val active = records.values.singleOrNull {
            it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis == null
        } ?: return DomainResult.Failure(DomainError.NoActiveSession)
        if (active.kind == SessionKind.ACTIVITY)
            return DomainResult.Failure(DomainError.DataIntegrity("当前进行中的记录不是睡眠"))
        if (endAtMillis <= active.startAtMillis)
            return DomainResult.Failure(DomainError.InvalidTime)
        val kind = requireNotNull(SleepKindResolver.resolve(null, active.startAtMillis, endAtMillis, zoneId))
        val finished = active.copy(kind = kind, endAtMillis = endAtMillis)
        records[active.id] = finished
        return DomainResult.Success(finished)
    }
    override suspend fun finishConfirmedSleepCheckIn(id: Long, endAtMillis: Long, zoneId: ZoneId): DomainResult<Session> {
        val active = records.values.singleOrNull {
            it.temporalType == SessionTemporalType.INTERVAL && it.endAtMillis == null
        } ?: return DomainResult.Failure(DomainError.NoActiveSession)
        if (active.id != id)
            return DomainResult.Failure(DomainError.DataIntegrity("进行中的睡眠已改变"))
        return finishSleepCheckIn(endAtMillis, zoneId)
    }
}
