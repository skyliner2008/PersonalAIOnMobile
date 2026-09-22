package com.skyliner2008.jarvis.ui.component.avatar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

actual val isRiveRuntimeSupported: Boolean = false

/**
 * iOS fallback implementation of RiveAvatarView.
 * Until native CocoaPods / SPM RiveRuntime is configured with -PenableIos=true,
 * this seamlessly renders the high-performance PetRobotHeadAvatar (Compose Canvas).
 */
@Composable
actual fun RiveAvatarView(
    modifier: Modifier,
    inputs: RiveAvatarInputs,
    fallbackState: AvatarState
) {
    PetRobotHeadAvatar(
        modifier = modifier,
        state = fallbackState,
        engineType = AvatarEngineType.COMPOSE_CANVAS
    )
}
