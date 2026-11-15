package com.vinay.nexolock

import com.vinay.nexolock.domain.VolumeCapture
import com.vinay.nexolock.domain.VolumePattern
import com.vinay.nexolock.domain.VolumePress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VolumePatternTest {

    @Test
    fun parseAcceptsUpDownCodes() {
        val pattern = VolumePattern.parse("UDUD")
        assertEquals(listOf(VolumePress.UP, VolumePress.DOWN, VolumePress.UP, VolumePress.DOWN), pattern?.presses)
        assertEquals("UDUD", pattern?.code)
        assertNull(VolumePattern.parse("UDU"))
        assertNull(VolumePattern.parse("UDXUD"))
        assertNull(VolumePattern.parse("UDUDUDUDU"))
    }

    @Test
    fun captureCompletesAtExpectedLength() {
        val capture = VolumeCapture(expectedLength = 4)
        assertTrue(capture.press(VolumePress.UP, 1_000) is VolumeCapture.Result.Progress)
        assertTrue(capture.press(VolumePress.DOWN, 1_400) is VolumeCapture.Result.Progress)
        assertTrue(capture.press(VolumePress.UP, 1_800) is VolumeCapture.Result.Progress)
        val result = capture.press(VolumePress.DOWN, 2_200)
        assertTrue(result is VolumeCapture.Result.Complete)
        assertEquals("UDUD", (result as VolumeCapture.Result.Complete).pattern.code)
        assertEquals(0, capture.count)
    }

    @Test
    fun slowPressesResetTheSequence() {
        val capture = VolumeCapture(expectedLength = 4, gapTimeoutMs = 1_000)
        capture.press(VolumePress.UP, 1_000)
        capture.press(VolumePress.UP, 1_500)
        val afterGap = capture.press(VolumePress.DOWN, 5_000)
        assertTrue(afterGap is VolumeCapture.Result.Progress)
        assertEquals(1, (afterGap as VolumeCapture.Result.Progress).count)
    }

    @Test
    fun resetClearsPartialInput() {
        val capture = VolumeCapture(expectedLength = 5)
        capture.press(VolumePress.DOWN, 1_000)
        capture.press(VolumePress.DOWN, 1_100)
        assertEquals(2, capture.count)
        capture.reset()
        assertEquals(0, capture.count)
        val next = capture.press(VolumePress.UP, 2_000)
        assertEquals(1, (next as VolumeCapture.Result.Progress).count)
    }

    @Test
    fun patternRejectsTooShortOrTooLongSequences() {
        try {
            VolumePattern(listOf(VolumePress.UP))
            org.junit.Assert.fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
        try {
            VolumePattern(List(9) { VolumePress.UP })
            org.junit.Assert.fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}
