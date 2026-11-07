package dev.hien.bitstream.core.designsystem

import android.animation.ValueAnimator
import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext

/**
 * Respects the system "remove animations" setting for the cover spin and wave
 * drift. Compose does not expose its animation scale as a composition local.
 *
 * The setting is process-wide, so it gets one observer for the whole app.
 * Registering per cover was a synchronous binder call for every artwork on
 * screen, repeated on every screen entry and scroll.
 */
@Composable
fun rememberMotionEnabled(): Boolean {
    MotionSetting.ensureObserved(LocalContext.current)
    return MotionSetting.enabled
}

private object MotionSetting {
    private val enabledState = mutableStateOf(ValueAnimator.areAnimatorsEnabled())
    val enabled: Boolean get() = enabledState.value
    private var observedContext: Context? = null

    fun ensureObserved(context: Context) {
        val appContext = context.applicationContext
        if (observedContext === appContext) return
        observedContext = appContext
        appContext.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean) {
                    enabledState.value = ValueAnimator.areAnimatorsEnabled()
                }
            },
        )
    }
}

/** Expressive press feedback: 0.95 scale driven by the same spring as the theme. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Modifier.scaleOnPress(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.95f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
