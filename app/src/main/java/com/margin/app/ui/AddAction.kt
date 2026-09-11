package com.margin.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The add button beside the tab bar does what the current screen means by "add": a task on
 * Tasks, an event on Plan, and the general add sheet elsewhere. Screens register their action
 * while they are on screen; the button falls back to the add sheet when nothing is registered.
 */
@Stable
class AddActionHolder {
    var action: (() -> Unit)? by mutableStateOf(null)
}

val LocalAddAction = staticCompositionLocalOf { AddActionHolder() }

@Composable
fun RegisterAddAction(action: () -> Unit) {
    val holder = LocalAddAction.current
    val current by rememberUpdatedState(action)
    DisposableEffect(holder) {
        val registered: () -> Unit = { current() }
        holder.action = registered
        onDispose {
            // During a transition the incoming screen registers before the outgoing one is
            // disposed; only clear the slot if it is still ours.
            if (holder.action === registered) holder.action = null
        }
    }
}
