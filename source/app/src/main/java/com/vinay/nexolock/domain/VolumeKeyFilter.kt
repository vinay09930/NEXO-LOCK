package com.vinay.nexolock.domain

/**
 * Pure key-event state for the hardware volume keys.
 *
 * Android delivers one ACTION_DOWN followed by one ACTION_UP for a normal
 * press, additional ACTION_DOWN events with repeatCount > 0 while the key is
 * held, and occasionally an ACTION_MULTIPLE. OEMs can also duplicate the first
 * ACTION_DOWN. This filter converts that noisy stream into exactly one
 * [VolumePress] per physical press and never lets a release create a press.
 *
 * The filter is deliberately free of Android types so the security-relevant
 * rules can be tested on the JVM.
 */
enum class VolumeKey { UP, DOWN }

enum class VolumeKeyAction { DOWN, UP, OTHER }

data class VolumeKeyInput(
    val key: VolumeKey?,
    val action: VolumeKeyAction,
    val repeatCount: Int = 0,
    /** Monotonic event time in milliseconds; 0 means "unknown". */
    val eventTimeMs: Long = 0L,
)

data class VolumeKeyDecision(
    /** True while a capture owns the keys, so the system volume panel stays hidden. */
    val consume: Boolean,
    /** Non-null only on the first DOWN edge of a physical press. */
    val press: VolumePress?,
)

class VolumeKeyFilter(
    /**
     * A same-key DOWN that arrives within this window after its UP is treated
     * as an OEM duplicate. Human double-presses are slower than this, so rapid
     * intentional presses are still counted.
     */
    private val duplicateWindowMs: Long = 40L,
) {

    private val pressed = mutableSetOf<VolumeKey>()
    private val lastReleaseAt = mutableMapOf<VolumeKey, Long>()

    fun onEvent(input: VolumeKeyInput): VolumeKeyDecision {
        val key = input.key ?: return VolumeKeyDecision(consume = false, press = null)
        return when (input.action) {
            VolumeKeyAction.DOWN -> onDown(key, input)
            VolumeKeyAction.UP -> {
                pressed -= key
                if (input.eventTimeMs > 0L) lastReleaseAt[key] = input.eventTimeMs
                VolumeKeyDecision(consume = true, press = null)
            }
            // ACTION_MULTIPLE / ACTION_CANCEL: consume while capture is active,
            // but never count them as a new press.
            VolumeKeyAction.OTHER -> VolumeKeyDecision(consume = true, press = null)
        }
    }

    private fun onDown(key: VolumeKey, input: VolumeKeyInput): VolumeKeyDecision {
        if (input.repeatCount > 0) {
            // Long-press repeat: the physical press was already counted.
            return VolumeKeyDecision(consume = true, press = null)
        }
        if (key in pressed) {
            // Duplicate DOWN without an intervening UP.
            return VolumeKeyDecision(consume = true, press = null)
        }
        val releasedAt = lastReleaseAt[key]
        if (input.eventTimeMs > 0L && releasedAt != null && releasedAt > 0L) {
            val sinceRelease = input.eventTimeMs - releasedAt
            if (sinceRelease in 0 until duplicateWindowMs) {
                return VolumeKeyDecision(consume = true, press = null)
            }
        }
        pressed += key
        return VolumeKeyDecision(
            consume = true,
            press = if (key == VolumeKey.UP) VolumePress.UP else VolumePress.DOWN,
        )
    }

    fun reset() {
        pressed.clear()
        lastReleaseAt.clear()
    }
}
