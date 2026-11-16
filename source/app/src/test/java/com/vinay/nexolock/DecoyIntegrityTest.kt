package com.vinay.nexolock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The decoy lock experience is frozen. These hashes pin the three source files
 * that implement the decoy UI, its state machine and its activity transitions.
 * Any future change to colours, text, layout, icons, animation, navigation or
 * timing fails this test and must be treated as a decoy-policy change, not as
 * a vault/import change.
 *
 * LockViewModel.kt and LockActivity.kt were re-frozen for 1.2.0 build 14 only
 * to bind recovery completion to repository-verified factors, clamp a corrupt
 * persisted PIN length, and annotate the existing key-event override for lint.
 * The decoy UI, text and timings were not changed.
 */
class DecoyIntegrityTest {

    private data class FrozenFile(val path: String, val sha256: String)

    private val frozen = listOf(
        FrozenFile(
            "src/main/java/com/vinay/nexolock/ui/lock/LockScreen.kt",
            "ac595425f04490ac127b12a28fda140ef116f4319db2e5b0db76cd64f2b374b5",
        ),
        FrozenFile(
            "src/main/java/com/vinay/nexolock/ui/lock/LockViewModel.kt",
            "70c5afeaace78b29da7c7c8e9e5593f2633f725d7b3e4e1146dc594f3eb0c380",
        ),
        FrozenFile(
            "src/main/java/com/vinay/nexolock/protection/LockActivity.kt",
            "5c568e94158c334b7c58ada5feef17680371baa9e441b4c422e0548e7269bf5e",
        ),
    )

    @Test
    fun decoySourcesAreByteForByteFrozen() {
        frozen.forEach { frozenFile ->
            val file = resolve(frozenFile.path)
            assertTrue("Missing decoy source: ${frozenFile.path}", file.isFile)
            assertEquals(
                "Decoy source changed: ${frozenFile.path}",
                frozenFile.sha256,
                sha256(file),
            )
        }
    }

    private fun resolve(relative: String): File {
        val working = File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            File(working, relative),
            File(working, "app/$relative"),
            File(working.parentFile, relative),
            File(working.parentFile, "app/$relative"),
        )
        return candidates.firstOrNull { it.isFile } ?: candidates.first()
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
