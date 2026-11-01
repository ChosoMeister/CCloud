@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.pira.ccloud.core.designsystem

import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pira.ccloud.utils.DeviceUtils

/** Observe the clickable/button's existing focus target; never add a second target. */
fun Modifier.tvFocusIndicator(): Modifier = composed {
    if (!DeviceUtils.isTv(LocalContext.current)) return@composed this
    var focused by remember { mutableStateOf(false) }
    this.onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(10.dp)) else Modifier)
}

/** Restore a card only after returning from its details; consume the request once. */
@androidx.compose.runtime.Composable
fun rememberGridReturnFocus(key: String, restoreKey: String?, onRestored: () -> Unit): Modifier {
    val requester = remember { androidx.compose.ui.focus.FocusRequester() }
    val isTv = DeviceUtils.isTv(LocalContext.current)
    val inputMode = androidx.compose.ui.platform.LocalInputModeManager.current
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (isTv && key == restoreKey) {
            inputMode.requestInputMode(androidx.compose.ui.input.InputMode.Keyboard)
            requester.requestFocus()
            onRestored()
        }
    }
    return Modifier.focusRequester(requester)
}

/** Constrain tall popup menus as well as the underlying TV page to an overscan-safe height. */
fun Modifier.tvSafeMenuHeight(): Modifier = composed {
    if (DeviceUtils.isTv(LocalContext.current)) heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.60f).dp)
    else this
}
