package com.vinay.nexolock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * The decoy lock experience is frozen for V1.1.3. These hashes pin the three
 * source files that implement the decoy UI, its state machine and its activity
 * transitions. Any future change to colours, text, layout, icons, animation,
 * navigation or timing fails this test and must be treated as a decoy-policy
 * change, not as a vault/import change.
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
            "0f0339c61a3373df63368de8beb20e6af8f24ae05004238daddd03d38db25194",
        ),
        FrozenFile(
            "src/main/java/com/vinay/nexolock/protection/LockActivity.kt",
            "c4e71a5cae29ae03e7a9fb05cc54207d59d596f192c791a0a95f15a890b5b759",
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
