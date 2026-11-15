package com.vinay.nexolock

import com.vinay.nexolock.vault.VaultImportErrors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException

/** Import failures stay honest and never expose a stack trace or URI. */
class VaultImportErrorsTest {

    @Test
    fun insufficientStorageIsReportedAsSuch() {
        val message = VaultImportErrors.messageFor(
            IOException("write failed: ENOSPC (No space left on device)"),
        )
        assertTrue(message.contains("not enough free storage", ignoreCase = true))
        assertTrue(message.contains("removed", ignoreCase = true))
    }

    @Test
    fun ordinaryIoAndPermissionFailuresAreDistinct() {
        assertEquals(
            "The selected file could not be read or stored.",
            VaultImportErrors.messageFor(IOException("broken pipe")),
        )
        assertEquals(
            "Access to the selected file was denied by its provider.",
            VaultImportErrors.messageFor(SecurityException("denied")),
        )
        assertEquals(
            "The selected file is no longer available.",
            VaultImportErrors.messageFor(FileNotFoundException("gone")),
        )
    }

    @Test
    fun unknownFailuresDoNotLeakDetails() {
        val message = VaultImportErrors.messageFor(IllegalStateException("secret path /data/x"))
        assertTrue(message.startsWith("The file could not be imported"))
        assertTrue(!message.contains("/data/x"))
    }
}
