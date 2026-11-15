package com.vinay.nexolock

import android.view.KeyEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vinay.nexolock.core.crypto.KeystoreCipher
import com.vinay.nexolock.data.LogType
import com.vinay.nexolock.data.SecurityLogRepository
import com.vinay.nexolock.domain.VolumePress
import com.vinay.nexolock.protection.VolumeCaptureController
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Device-level checks. These require an emulator or a physical device and are
 * not part of the JVM unit-test suite.
 */
@RunWith(AndroidJUnit4::class)
class NexoInstrumentedTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun cleanUp() {
        runBlocking { SecurityLogRepository(context, KeystoreCipher()).clear() }
    }

    @Test
    fun keystoreCipherRoundTripsAndRejectsTampering() {
        val cipher = KeystoreCipher()
        val blob = cipher.encryptString("nexo-secret-value")
        assertEquals("nexo-secret-value", cipher.decryptString(blob))

        val tampered = blob.copyOf()
        tampered[tampered.lastIndex] = (tampered[tampered.lastIndex].toInt() xor 0x01).toByte()
        var rejected = false
        try {
            cipher.decryptString(tampered)
        } catch (expected: Exception) {
            rejected = true
        }
        assertTrue("AES-GCM must reject modified ciphertext", rejected)
    }

    @Test
    fun volumeControllerOnlyConsumesWhileCaptureIsRegistered() {
        val controller = VolumeCaptureController()
        val up = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_VOLUME_UP)
        assertFalse(controller.onKeyEvent(up))

        val presses = ArrayList<VolumePress>()
        controller.setListener { presses += it }
        assertTrue(controller.onKeyEvent(up))
        assertEquals(listOf(VolumePress.UP), presses)

        // Key-up and repeats must not be counted twice.
        assertTrue(
            controller.onKeyEvent(
                KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_VOLUME_UP),
            ),
        )
        assertEquals(1, presses.size)

        controller.clear()
        assertFalse(controller.onKeyEvent(up))
        assertEquals(1, presses.size)
    }

    @Test
    fun securityLogPersistsEncryptedHashChainedEntries() {
        val repository = SecurityLogRepository(context, KeystoreCipher())
        runBlocking {
            repository.clear()
            repository.log(LogType.APP_UNLOCKED, "device test unlock", "Main PIN")
            repository.log(LogType.MAIN_PIN_FAILED, "device test failure")
            val read = repository.read()
            assertEquals(2, read.entries.size)
            assertEquals(0, read.tamperedCount)
            assertEquals(0, read.corruptedRecords)
            assertTrue(read.entries.any { it.type == LogType.APP_UNLOCKED })
        }
    }
}
