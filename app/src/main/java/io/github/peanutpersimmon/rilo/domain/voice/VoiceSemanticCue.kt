package io.github.peanutpersimmon.rilo.domain.voice

/** Unsupported responsibility wording only; the model owns primary type selection. */
object VoiceSemanticCue {
    /** Responsibility and deadline phrases require a concrete schedule before becoming an Event. */
    fun isUnscheduledResponsibility(text: String): Boolean =
        listOf("记得", "别忘", "不要忘", "截止", "之前", "前交", "前完成", "前写完").any(text::contains)
}
