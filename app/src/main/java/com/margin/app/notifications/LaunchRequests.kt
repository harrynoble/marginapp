package com.margin.app.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A notification that needs a real answer ("when will you be back?") opens the app straight
 * into the right sheet. The activity drops the request here; the UI picks it up and clears it.
 */
object LaunchRequests {
    const val EXTRA_TARGET = "com.margin.app.extra.TARGET"

    const val TARGET_OUT = "out"
    const val TARGET_CHECK_IN = "checkin"
    const val TARGET_BUILD = "build"
    const val TARGET_LEARN = "learn"

    private val pendingTarget = MutableStateFlow<String?>(null)
    val pending: StateFlow<String?> = pendingTarget

    fun request(target: String?) {
        if (!target.isNullOrBlank()) pendingTarget.value = target
    }

    fun consume() {
        pendingTarget.value = null
    }
}
