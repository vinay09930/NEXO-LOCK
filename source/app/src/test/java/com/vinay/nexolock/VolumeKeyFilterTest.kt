package com.vinay.nexolock

import com.vinay.nexolock.domain.VolumeCapture
import com.vinay.nexolock.domain.VolumeKey
import com.vinay.nexolock.domain.VolumeKeyAction
import com.vinay.nexolock.domain.VolumeKeyFilter
import com.vinay.nexolock.domain.VolumeKeyInput
import com.vinay.nexolock.domain.VolumePattern
import com.vinay.nexolock.domain.VolumePress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumeKeyFilterTest {

    private fun down(key: VolumeKey, time: Long, repeat: Int = 0) =
        VolumeKeyInput(key, VolumeKeyAction.DOWN, repeat, time)

    private fun up(key: VolumeKey, time: Long) =
        VolumeKeyInput(key, VolumeKeyAction.UP, 0, time)

    @Test
    fun onePhysicalPressProducesOneVolumePress() {
        val filter = VolumeKeyFilter()
        assertEquals(VolumePress.UP, filter.onEvent(down(VolumeKey.UP, 1_000)).press)
        assertNull(filter.onEvent(up(VolumeKey.UP, 1_050)).press)
        assertEquals(VolumePress.DOWN, filter.onEvent(down(VolumeKey.DOWN, 1_200)).press)
        assertNull(filter.onEvent(up(VolumeKey.DOWN, 1_250)).press)
    }

    @Test
    fun longPressRepeatsAreIgnoredButConsumed() {
        val filter = VolumeKeyFilter()
        assertEquals(VolumePress.UP, filter.onEvent(down(VolumeKey.UP, 1_000)).press)
        val repeat = filter.onEvent(down(VolumeKey.UP, 1_400, repeat = 1))
        assertTrue(repeat.consume)
        assertNull(repeat.press)
    }

    @Test
    fun duplicateDownWithoutUpIsCountedOnce() {
        val filter = VolumeKeyFilter()
        assertEquals(VolumePress.DOWN, filter.onEvent(down(VolumeKey.DOWN, 1_000)).press)
        assertNull(filter.onEvent(down(VolumeKey.DOWN, 1_020)).press)
        assertNull(filter.onEvent(up(VolumeKey.DOWN, 1_100)).press)
    }

    @Test
    fun upWithoutDownNeverCreatesAPress() {
        val filter = VolumeKeyFilter()
        assertNull(filter.onEvent(up(VolumeKey.UP, 1_000)).press)
        assertNull(filter.onEvent(up(VolumeKey.DOWN, 1_010)).press)
    }

    @Test
    fun rapidDifferentKeysAreBothCounted() {
        val filter = VolumeKeyFilter()
        assertEquals(VolumePress.UP, filter.onEvent(down(VolumeKey.UP, 1_000)).press)
        filter.onEvent(up(VolumeKey.UP, 1_010))
        assertEquals(VolumePress.DOWN, filter.onEvent(down(VolumeKey.DOWN, 1_020)).press)
    }

    @Test
    fun rapidSameKeyAfterReleaseIsCountedOnceTheDuplicateWindowPasses() {
        val filter = VolumeKeyFilter(duplicateWindowMs = 40)
        assertEquals(VolumePress.UP, filter.onEvent(down(VolumeKey.UP, 1_000)).press)
        filter.onEvent(up(VolumeKey.UP, 1_010))
        // 10 ms after release: treated as an OEM duplicate.
        assertNull(filter.onEvent(down(VolumeKey.UP, 1_020)).press)
        filter.onEvent(up(VolumeKey.UP, 1_030))
        // 100 ms after the last release: a genuine second press.
        assertEquals(VolumePress.UP, filter.onEvent(down(VolumeKey.UP, 1_130)).press)
    }

    @Test
    fun unrelatedKeyIsNotConsumed() {
        val filter = VolumeKeyFilter()
        val decision = filter.onEvent(
            VolumeKeyInput(null, VolumeKeyAction.DOWN, 0, 1_000),
        )
        assertFalse(decision.consume)
        assertNull(decision.press)
    }

    @Test
    fun resetForgetsPressedKeys() {
        val filter = VolumeKeyFilter()
        filter.onEvent(down(VolumeKey.UP, 1_000))
        filter.reset()
        assertEquals(VolumePress.UP, filter.onEvent(down(VolumeKey.UP, 1_100)).press)
    }

    @Test
    fun repeatedEventsCannotCompleteAPattern() {
        val capture = VolumeCapture(expectedLength = 4)
        val filter = VolumeKeyFilter()
        val presses = mutableListOf<VolumePress>()
        listOf(
            down(VolumeKey.UP, 1_000),
            down(VolumeKey.UP, 1_100, repeat = 1),
            down(VolumeKey.UP, 1_200, repeat = 2),
            up(VolumeKey.UP, 1_300),
        ).forEach { input -> filter.onEvent(input).press?.let(presses::add) }
        presses.forEachIndexed { index, press ->
            capture.press(press, 1_300L + index * 100L)
        }
        assertEquals(1, capture.count)
    }

    @Test
    fun partialPatternNeverCompletes() {
        val capture = VolumeCapture(expectedLength = 4)
        capture.press(VolumePress.UP, 1_000)
        capture.press(VolumePress.UP, 1_200)
        capture.press(VolumePress.DOWN, 1_400)
        assertEquals(3, capture.count)
    }

    @Test
    fun invalidPersistedLengthReturnsNullInsteadOfThrowing() {
        assertNull(VolumeCapture.createOrNull(0))
        assertNull(VolumeCapture.createOrNull(3))
        assertNull(VolumeCapture.createOrNull(9))
        assertTrue(VolumeCapture.createOrNull(4) is VolumeCapture)
        assertEquals(
            VolumePattern.MIN_LENGTH,
            VolumePattern.parse("UDUD")?.presses?.size,
        )
    }
}
