package io.github.peanutpersimmon.rilo.ui.theme

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

/** Global interaction indication. Intentionally draws nothing: Material ripple is forbidden. */
object NoRippleIndication : Indication, IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = NoRippleNode()

    override fun hashCode(): Int = javaClass.hashCode()

    override fun equals(other: Any?): Boolean = other === this

    private class NoRippleNode : Modifier.Node()
}

