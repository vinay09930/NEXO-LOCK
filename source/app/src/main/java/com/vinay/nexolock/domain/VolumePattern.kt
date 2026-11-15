package com.vinay.nexolock.domain

/**
 * Pure gesture model. A volume pattern is a sequence of UP/DOWN presses with a
 * fixed length between 4 and 8. The plain sequence is only held in memory while
 * the user is configuring or testing it; only a salted hash is persisted.
 */
enum class VolumePress { UP, DOWN }

data class VolumePattern(val presses: List<VolumePress>) {

    init {
        require(presses.size in MIN_LENGTH..MAX_LENGTH) {
            "A volume pattern must contain between $MIN_LENGTH and $MAX_LENGTH presses."
        }
    }

    val code: String get() = presses.joinToString("") { if (it == VolumePress.UP) "U" else "D" }

    fun describe(): String = presses.joinToString(" ") { if (it == VolumePress.UP) "Up" else "Down" }

    companion object {
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 8

        fun parse(code: String): VolumePattern? {
            if (code.length !in MIN_LENGTH..MAX_LENGTH) return null
            val presses = code.map { char ->
                when (char) {
                    'U', 'u' -> VolumePress.UP
                    'D', 'd' -> VolumePress.DOWN
                    else -> return null
                }
            }
            return VolumePattern(presses)
        }
    }
}

/**
 * Incremental capture with an inter-press timeout. Lives in memory only.
 */
class VolumeCapture(
    private val expectedLength: Int,
    private val gapTimeoutMs: Long = 2_500L,
) {

    init {
        require(expectedLength in VolumePattern.MIN_LENGTH..VolumePattern.MAX_LENGTH)
    }

    sealed interface Result {
        data class Progress(val count: Int, val expected: Int) : Result
        data class Complete(val pattern: VolumePattern) : Result
    }

    private val presses = ArrayList<VolumePress>(expectedLength)
    private var lastPressAt = 0L

    val count: Int get() = presses.size

    fun press(press: VolumePress, nowElapsedMs: Long): Result {
        if (lastPressAt != 0L && nowElapsedMs - lastPressAt > gapTimeoutMs) {
            presses.clear()
        }
        lastPressAt = nowElapsedMs
        presses += press
        return if (presses.size >= expectedLength) {
            val pattern = VolumePattern(presses.toList())
            presses.clear()
            lastPressAt = 0L
            Result.Complete(pattern)
        } else {
            Result.Progress(presses.size, expectedLength)
        }
    }

    fun reset() {
        presses.clear()
        lastPressAt = 0L
    }

    companion object {
        /** Returns null instead of throwing when persisted length data is invalid. */
        fun createOrNull(
            expectedLength: Int,
            gapTimeoutMs: Long = 2_500L,
        ): VolumeCapture? = if (expectedLength in VolumePattern.MIN_LENGTH..VolumePattern.MAX_LENGTH) {
            VolumeCapture(expectedLength, gapTimeoutMs)
        } else {
            null
        }
    }
}
