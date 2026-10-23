package io.github.peanutpersimmon.rilo.data.remote.qwen

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.github.peanutpersimmon.rilo.domain.voice.agent.VoiceAgentContext
import io.github.peanutpersimmon.rilo.domain.voice.MAX_CATEGORY_HINT_LENGTH
import java.util.Base64

object QwenAgentProtocol {
    const val MODEL = "qwen3.8-omni-flash-realtime"

    fun instructions(context: VoiceAgentContext): String = """
        Current local datetime:
        ${context.now.atZone(context.zone).toOffsetDateTime()}
        Timezone:
        ${context.zone.id}

        你是 RILO 的自然语言命令解释器。只调用一个最合适的工具；不闲聊，不输出推理。
        先按语义选择唯一工具：睡眠用 Sleep，已发生事实用 Record，计划或安排用 Event；确有歧义或不支持时澄清。时间字段的多少不决定对象类型。
        Event：明天体检→DATE；明天下午三点开会→POINT；明天下午三点到五点开会→INTERVAL。只有日期也是完整日程。
        Record：刚刚吃完饭→POINT；刚才三点到四点开会了→INTERVAL；今天下午去了医院→Record。现在开始看论文→start_record。刚刚、刚才等可用设备时间解析实际发生时刻；无法确定时保留 Record 意图并留空时间，供本地补全。
        Sleep 独立于普通 Record：睡了一会→create_sleep_record；下午一点到两点睡了一会→Sleep/Nap；昨晚一点睡到今天九点→Sleep/Night；我要睡了、现在睡觉→start_sleep；刚睡醒、当前睡眠中说“我醒了”→finish_sleep。sleep_type 不确定时省略，不要为了选择 Sleep 而猜 Nap/Night。
        “今天去医院”若上下文不能判断计划还是事实→request_clarification。RILO 没有 Task/due；“记得买牙膏”“周五前交论文”→UNSUPPORTED_RESPONSIBILITY，不创建 Event。标签请求→UNSUPPORTED_TAG，不当作分类。
        POINT Record 有实际 occurred_at 时提供；补录 INTERVAL 有实际边界时提供。不要把只有日期的事实编成午夜或正午。没有足够时间时仍调用对应 Record/Sleep 工具，交本地澄清或编辑器。
        Event 提醒仅在用户明确要求时设置：准时提醒→reminder_offset_minutes=0；提前 N 分钟/小时/天→换算为非负整数分钟；未要求提醒则省略。update_event 取消提醒→clear_fields 包含 reminder；只修改时间时不填提醒字段。只说“提前提醒”但没有提前多久→request_clarification，reason=REMINDER_OFFSET_UNRESOLVED，询问提前多久，不猜默认值。若已选择 Event 工具但提醒时长尚未解析，设置 reminder_offset_unresolved=true，交本地澄清。提醒不改变 Event 主类型。
        create_record_from_event 只预填并保留来源；两边后续修改互不自动同步。
        category_hint 仅用于已配置的分类，不决定对象类型；update_event/update_record 明确修改分类时填现有分类名称，取消分类时将 category 放入 clear_fields；未提分类时省略。Sleep 不填。
        结合设备时间和时区解析相对时间。具体时刻输出带时区偏移的 ISO-8601 datetime；只有日期时不编造时刻。
        title 只保留做什么，地点放入 location。音频输入把听到的原句写入 source_text。
    """.trimIndent()
    fun sessionUpdate(context: VoiceAgentContext, audioInput: Boolean = false): JsonObject {
        val session = obj(
            "modalities" to array("text"),
            "instructions" to instructions(context),
            "turn_detection" to null,
            "enable_search" to false,
            "tools" to tools(),
            "temperature" to 0.2,
            "max_tokens" to 2048,
        )
        if (audioInput) session.add("audio", obj("input" to obj("format" to obj(
            "type" to "pcm", "sample_rate" to 16000))))
        return obj("type" to "session.update", "session" to session)
    }

    fun userText(text: String): JsonObject = obj(
        "type" to "conversation.item.create",
        "item" to obj(
            "type" to "message",
            "role" to "user",
            "content" to array(obj("type" to "input_text", "text" to text)),
        ),
    )

    fun audioAppend(pcm: ByteArray): JsonObject = obj(
        "type" to "input_audio_buffer.append",
        "audio" to Base64.getEncoder().encodeToString(pcm),
    )
    fun audioCommit(): JsonObject = obj("type" to "input_audio_buffer.commit")
    fun audioClear(): JsonObject = obj("type" to "input_audio_buffer.clear")

    fun responseCreate(): JsonObject = obj("type" to "response.create")

    fun functionOutput(callId: String, output: String): JsonObject = obj(
        "type" to "conversation.item.create",
        "item" to obj("type" to "function_call_output", "call_id" to callId, "output" to output),
    )

    fun tools(): JsonArray = array(
        tool("create_event", "创建明确安排的日程；DATE 仅日期，POINT 开始时刻，INTERVAL 起止时刻", listOf("title", "temporal_type"),
            "title" to string(), "temporal_type" to enumString("DATE", "POINT", "INTERVAL"), "date" to date(),
            "start_at" to iso(), "end_at" to iso(), "location" to string(), "note" to string(),
            "category_hint" to categoryHint(), "reminder_offset_minutes" to nonNegativeInteger(),
            "reminder_offset_unresolved" to boolean()),
        tool("update_event", "按标题和原时间查询并更新日程", listOf("target_query"),
            "target_query" to string(), "target_date" to date(), "target_start_at" to iso(),
            "title" to string(), "date" to date(), "start_at" to iso(), "end_at" to iso(), "location" to string(), "note" to string(),
            "category_hint" to categoryHint(), "reminder_offset_minutes" to nonNegativeInteger(),
            "reminder_offset_unresolved" to boolean(),
            "clear_fields" to enumArray("start", "end", "location", "note", "reminder", "category")),
        tool("create_record", "创建已发生事实；缺实际时间也保持 Record，交本地补全；POINT 用 occurred_at，INTERVAL 用实际起止", listOf("title", "temporal_type"),
            "title" to string(), "temporal_type" to enumString("POINT", "INTERVAL"), "occurred_at" to iso(),
            "start_at" to iso(), "end_at" to iso(), "location" to string(), "note" to string(),
            "category_hint" to categoryHint()),
        tool("create_sleep_record", "补记睡眠；sleep_type 和时间可暂缺，由本地补全或澄清", emptyList(),
            "sleep_type" to enumString("nap", "night_sleep", "unresolved"), "title" to string(),
            "start_at" to iso(), "end_at" to iso(), "location" to string(), "note" to string()),
        tool("update_record", "按文字查询并更新记录；temporal_type 仅在明确改变形态时提供", listOf("target_query"),
            "target_query" to string(), "target_start_at" to iso(), "title" to string(),
            "temporal_type" to enumString("POINT", "INTERVAL"), "occurred_at" to iso(), "start_at" to iso(), "end_at" to iso(),
            "location" to string(), "note" to string(), "category_hint" to categoryHint(),
            "clear_fields" to enumArray("end", "location", "note", "category")),
        tool("start_record", "仅在用户明确说开始记录或开始计时时立即开始真实记录", listOf("title"),
            "title" to string(), "location" to string(), "note" to string(), "category_hint" to categoryHint()),
        tool("start_sleep", "明确现在开始睡眠；sleep_type 可暂缺，结束后按完整区间确定", emptyList(),
            "sleep_type" to enumString("nap", "night_sleep", "unresolved"), "title" to string(), "start_at" to iso()),
        tool("finish_sleep", "只结束当前进行中的睡眠；醒来时 end_at 可省略并使用现在", emptyList(),
            "end_at" to iso()),
        tool("stop_record", "只停止当前或指定的进行中记录", emptyList(), "target_query" to string()),
        tool("create_record_from_event", "从已有日程预填记录并保留来源；缺少实际时间时交编辑器确认", listOf("event_query", "temporal_type"),
            "event_query" to string(), "event_date" to date(), "temporal_type" to enumString("POINT", "INTERVAL"),
            "actual_at" to iso(), "actual_start_at" to iso(), "actual_end_at" to iso()),
        tool("request_clarification", "不支持、确有歧义或数据冲突时询问用户，不创建数据", listOf("reason"),
            "reason" to enumString("AMBIGUOUS_PLAN_OR_FACT", "EVENT_MISSING_TIME", "REMINDER_OFFSET_UNRESOLVED", "RECORD_MISSING_TIME", "SLEEP_MISSING_START", "SLEEP_MISSING_END", "SLEEP_KIND_UNRESOLVED", "NO_ACTIVE_SLEEP", "UNSUPPORTED_RESPONSIBILITY", "UNSUPPORTED_TAG", "INVALID_FUTURE_FACT", "INVALID_TIME_RANGE", "TARGET_NOT_FOUND", "MULTIPLE_TARGETS", "OBJECT_TYPE_AMBIGUOUS", "INTERVAL_MISSING_BOUNDARY", "TARGET_NOT_UNIQUE", "MULTIPLE_PRIMARY_OBJECTS", "TIME_SEMANTIC_CONFLICT"),
            "question" to string(), "known_date" to date(), "record_title" to string()),
    )
    private fun tool(name: String, description: String, required: List<String>,
        vararg properties: Pair<String, JsonObject>): JsonObject {
        val props = JsonObject().apply {
            properties.forEach { (key, value) -> add(key, value) }
            add("source_text", string())
        }
        return obj("type" to "function", "function" to obj(
            "name" to name, "description" to description,
            "parameters" to obj("type" to "object", "properties" to props,
                "required" to JsonArray().apply { required.forEach { add(it) } }, "additionalProperties" to false),
        ))
    }

    private fun string() = obj("type" to "string")
    private fun categoryHint() = obj(
        "type" to "string",
        "maxLength" to MAX_CATEGORY_HINT_LENGTH,
        "description" to "分类词或短语，不是分类 ID",
    )
    private fun boolean() = obj("type" to "boolean")
    private fun nonNegativeInteger() = obj("type" to "integer", "minimum" to 0)
    private fun date() = obj("type" to "string", "description" to "YYYY-MM-DD")
    private fun iso() = obj("type" to "string", "description" to "ISO-8601 datetime with UTC offset")
    private fun enumString(vararg values: String) = obj("type" to "string", "enum" to array(*values))
    private fun enumArray(vararg values: String) = obj("type" to "array",
        "items" to obj("type" to "string", "enum" to array(*values)))
    fun obj(vararg values: Pair<String, Any?>): JsonObject = JsonObject().apply {
        values.forEach { (key, value) -> when (value) {
            null -> add(key, null)
            is String -> addProperty(key, value)
            is Number -> addProperty(key, value)
            is Boolean -> addProperty(key, value)
            is JsonObject -> add(key, value)
            is JsonArray -> add(key, value)
            else -> error("Unsupported JSON value")
        } }
    }

    fun array(vararg values: Any): JsonArray = JsonArray().apply {
        values.forEach { value -> when (value) {
            is String -> add(value)
            is JsonObject -> add(value)
            else -> error("Unsupported JSON array value")
        } }
    }
}
