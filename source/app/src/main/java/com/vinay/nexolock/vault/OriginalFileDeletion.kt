package com.vinay.nexolock.vault

import android.app.RecoverableSecurityException
import android.content.ContentResolver
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Outcome of asking the owning document provider to delete the original file
 * that was just imported. Only [Deleted] proves that the provider removed it.
 */
sealed interface OriginalDeletionResult {
    data class Deleted(val via: String) : OriginalDeletionResult
    data class NeedsConsent(
        val intentSender: IntentSender,
        val uri: Uri,
    ) : OriginalDeletionResult

    data class Failed(val message: String) : OriginalDeletionResult
    data class Unsupported(val message: String) : OriginalDeletionResult
}

/**
 * Accumulates honest per-file outcomes for one "delete originals" batch.
 * Notes are bounded so a large selection cannot inflate the UI state.
 */
data class VaultDeletionReport(
    val requested: Int = 0,
    val deleted: Int = 0,
    val failed: Int = 0,
    val unsupported: Int = 0,
    val needsConsent: Int = 0,
    val notes: List<String> = emptyList(),
) {
    fun record(result: OriginalDeletionResult, name: String): VaultDeletionReport {
        val label = name.ifBlank { "Original file" }
        return when (result) {
            is OriginalDeletionResult.Deleted -> copy(
                deleted = deleted + 1,
                notes = notes.note("$label: deleted"),
            )

            is OriginalDeletionResult.Failed -> copy(
                failed = failed + 1,
                notes = notes.note("$label: ${result.message}"),
            )

            is OriginalDeletionResult.Unsupported -> copy(
                unsupported = unsupported + 1,
                notes = notes.note("$label: ${result.message}"),
            )

            is OriginalDeletionResult.NeedsConsent -> copy(
                needsConsent = needsConsent + 1,
                notes = notes.note("$label: waiting for system confirmation"),
            )
        }
    }

    fun summary(): String {
        if (requested == 0) return "No originals were scheduled for deletion."
        val parts = buildList {
            if (deleted > 0) add("$deleted deleted")
            if (needsConsent > 0) add("$needsConsent awaiting confirmation")
            if (failed > 0) add("$failed failed")
            if (unsupported > 0) add("$unsupported not supported")
        }
        return if (parts.isEmpty()) {
            "No originals were deleted."
        } else {
            "Originals: ${parts.joinToString(", ")}."
        }
    }

    /**
     * Replaces a previously counted [OriginalDeletionResult.NeedsConsent] with
     * the outcome that followed the system confirmation.
     */
    fun resolveConsent(result: OriginalDeletionResult, name: String): VaultDeletionReport {
        if (result is OriginalDeletionResult.NeedsConsent) return record(result, name)
        return copy(needsConsent = (needsConsent - 1).coerceAtLeast(0))
            .record(result, name)
    }

    private fun List<String>.note(value: String): List<String> =
        (this + value).takeLast(MAX_NOTES)

    private companion object {
        const val MAX_NOTES = 40
    }
}

/**
 * Pure gate for the opt-in "delete the original after import" feature. The
 * vault copy must be committed and independently verified before any deletion
 * request is sent to the provider.
 */
object OriginalFileDeletionPolicy {
    fun shouldDelete(
        enabled: Boolean,
        importSucceeded: Boolean,
        verified: Boolean,
        hasContentUri: Boolean,
    ): Boolean = enabled && importSucceeded && verified && hasContentUri
}

/**
 * Deletes a provider-owned original through the SAF/MediaStore APIs. A generic
 * filesystem path is never touched: without a `content://` grant there is no
 * lawful way to prove ownership, so the result is [OriginalDeletionResult.Unsupported].
 */
object OriginalFileDeletion {

    suspend fun delete(context: Context, uri: Uri): OriginalDeletionResult =
        withContext(Dispatchers.IO) {
            if (uri.scheme != ContentResolver.SCHEME_CONTENT) {
                return@withContext OriginalDeletionResult.Unsupported(
                    "Only documents opened through the system picker can be deleted automatically.",
                )
            }
            val resolver = context.contentResolver
            var firstError: Exception? = null

            if (runCatching { DocumentsContract.isDocumentUri(context, uri) }.getOrDefault(false)) {
                try {
                    if (DocumentsContract.deleteDocument(resolver, uri)) {
                        return@withContext OriginalDeletionResult.Deleted("document provider")
                    }
                    firstError = IllegalStateException("The document provider returned false.")
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (security: SecurityException) {
                    return@withContext consentOrFailure(context, uri, security)
                } catch (error: Exception) {
                    firstError = error
                }
            }

            try {
                if (resolver.delete(uri, null, null) > 0) {
                    return@withContext OriginalDeletionResult.Deleted("content provider")
                }
                if (firstError == null) {
                    firstError = IllegalStateException("The provider deleted no rows.")
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (security: SecurityException) {
                return@withContext consentOrFailure(context, uri, security)
            } catch (error: Exception) {
                firstError = error
            }

            OriginalDeletionResult.Failed(
                "The document provider refused to delete the original " +
                    "(${firstError?.javaClass?.simpleName ?: "no reason given"}). " +
                    "It may still exist.",
            )
        }

    private fun consentOrFailure(
        context: Context,
        uri: Uri,
        error: SecurityException,
    ): OriginalDeletionResult {
        // RecoverableSecurityException only exists on API 29+, so the class
        // reference lives in a method that is never invoked on older devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            recoverableConsent(error, uri)?.let { return it }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val request = runCatching {
                MediaStore.createDeleteRequest(context.contentResolver, listOf(uri))
            }.getOrNull()
            if (request != null) {
                return OriginalDeletionResult.NeedsConsent(
                    intentSender = request.intentSender,
                    uri = uri,
                )
            }
        }
        return OriginalDeletionResult.Failed(
            "The system did not grant permission to delete the original. It may still exist.",
        )
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private fun recoverableConsent(
        error: SecurityException,
        uri: Uri,
    ): OriginalDeletionResult? {
        val recoverable = error as? RecoverableSecurityException ?: return null
        return OriginalDeletionResult.NeedsConsent(
            intentSender = recoverable.userAction.actionIntent.intentSender,
            uri = uri,
        )
    }
}
