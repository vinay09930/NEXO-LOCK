package com.vinay.nexolock.vault

import java.io.FileNotFoundException
import java.io.IOException

/**
 * User-facing import failure messages. Kept pure so the wording is unit-tested
 * and no exception stack trace, URI query or secret ever reaches the UI.
 */
object VaultImportErrors {

    fun messageFor(error: Throwable): String = when (error) {
        is VaultImportException -> error.message ?: "The file could not be imported."
        is SecurityException -> "Access to the selected file was denied by its provider."
        is FileNotFoundException -> "The selected file is no longer available."
        is IOException -> {
            val text = (error.message ?: "").lowercase()
            if ("enospc" in text || "no space left" in text || "not enough space" in text) {
                "There is not enough free storage to finish the import. " +
                    "The unfinished file was removed."
            } else {
                "The selected file could not be read or stored."
            }
        }
        is IllegalArgumentException -> "The selected file name or type is not supported."
        else -> "The file could not be imported (${error.javaClass.simpleName})."
    }
}
