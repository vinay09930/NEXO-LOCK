package com.vinay.nexolock.protection

import android.view.KeyEvent
import com.vinay.nexolock.domain.VolumeKey
import com.vinay.nexolock.domain.VolumeKeyAction
import com.vinay.nexolock.domain.VolumeKeyFilter
import com.vinay.nexolock.domain.VolumeKeyInput
import com.vinay.nexolock.domain.VolumePress

/**
 * Routes hardware volume keys to whichever screen is actively capturing a
 * gesture. Returning false when no capture is active is essential: it keeps
 * normal volume control working everywhere else in the app.
 *
 * Accessibility is not involved. The owning Activity receives these events
 * through dispatchKeyEvent() only while its window is focused. Monitoring is a
 * separate responsibility handled by [NexoAccessibilityService].
 *
 * Ownership is token-based so a screen that is being disposed cannot clear a
 * newer screen's capture.
 */
class VolumeCaptureController {

    private val filter = VolumeKeyFilter()
    private var owner: Any? = null
    private var listener: ((VolumePress) -> Unit)? = null

    @Synchronized
    fun begin(owner: Any, listener: (VolumePress) -> Unit) {
        this.owner = owner
        this.listener = listener
        filter.reset()
    }

    @Synchronized
    fun end(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            listener = null
            filter.reset()
        }
    }

    @Synchronized
    fun setListener(listener: ((VolumePress) -> Unit)?) {
        if (listener == null) {
            this.owner = null
            this.listener = null
            filter.reset()
        } else {
            begin(listener, listener)
        }
    }

    @Synchronized
    fun clear() {
        owner = null
        listener = null
        filter.reset()
    }

    /** @return true when the event was consumed by an active capture. */
    fun onKeyEvent(event: KeyEvent): Boolean {
        val key = when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> VolumeKey.UP
            KeyEvent.KEYCODE_VOLUME_DOWN -> VolumeKey.DOWN
            else -> return false
        }
        val action = when (event.action) {
            KeyEvent.ACTION_DOWN -> VolumeKeyAction.DOWN
            KeyEvent.ACTION_UP -> VolumeKeyAction.UP
            else -> VolumeKeyAction.OTHER
        }
        return onInput(
            VolumeKeyInput(
                key = key,
                action = action,
                repeatCount = event.repeatCount,
                eventTimeMs = event.eventTime,
            ),
        )
    }

    fun onInput(input: VolumeKeyInput): Boolean {
        val current = synchronized(this) { listener } ?: return false
        val decision = synchronized(this) { filter.onEvent(input) }
        decision.press?.let(current)
        return decision.consume
    }
}
