package com.looker.kenko.ui.addSet.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.looker.kenko.ui.components.DpRange
import com.looker.kenko.ui.components.rangeTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.launch

private const val OFFSET_DAMPING = 0.998F

@Stable
class DragState(
    val events: DragEvents,
    constraints: DragConstraints,
    scope: CoroutineScope,
) {

    val offset: Animatable<Float, AnimationVector1D> = Animatable(0F)

    private val highBouncySpring = spring<Float>(Spring.DampingRatioHighBouncy)
    val mediumBouncySpring = spring<Float>(Spring.DampingRatioMediumBouncy)

    @Stable
    val state: DraggableState = DraggableState { delta ->
        val targetOffset = offset.value + delta
        val adjustedOffset = OFFSET_DAMPING * targetOffset
        scope.launch {
            offset.animateTo(adjustedOffset, highBouncySpring)
        }
    }

    private val isOutside: Flow<Boolean> =
        snapshotFlow { offset.value !in constraints.noIncrementZone }

    init {
        scope.launch {
            isOutside.collectIndexed { index, isOutsideBounds ->
                // for some reasons the flow is initiated multi times
                if (index > 0) {
                    events.onStop()
                    if (isOutsideBounds) {
                        events.onHold(offset.value > 0)
                    }
                }
            }
        }
        offset.updateBounds(
            constraints.containerBounds.start,
            constraints.containerBounds.endInclusive,
        )
    }
}

@Composable
fun rememberDraggableTextFieldState(
    events: DragEvents,
    constraints: DragConstraints = DragConstraints(
        density = LocalDensity.current,
        swipeRange = (-48).dp..96.dp,
        noIncrementRange = (-36).dp..42.dp,
    ),
) = DragState(
    events = events,
    constraints = constraints,
    scope = rememberCoroutineScope(),
)

@Immutable
class DragConstraints(
    density: Density,
    swipeRange: DpRange,
    noIncrementRange: DpRange,
) {

    val containerBounds: ClosedFloatingPointRange<Float> = with(density) {
        swipeRange.start.toPx()..swipeRange.end.toPx()
    }

    val noIncrementZone: ClosedFloatingPointRange<Float> = with(density) {
        noIncrementRange.start.toPx()..noIncrementRange.end.toPx()
    }
}

@Stable
interface DragEvents {

    fun onHold(isRight: Boolean)

    fun onStop()
}
