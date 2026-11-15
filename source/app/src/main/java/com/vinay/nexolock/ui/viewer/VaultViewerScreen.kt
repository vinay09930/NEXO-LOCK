package com.vinay.nexolock.ui.viewer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vinay.nexolock.ui.NexoUiState
import com.vinay.nexolock.ui.NexoViewModel
import com.vinay.nexolock.ui.ViewerUiState
import com.vinay.nexolock.ui.ViewerLoad
import com.vinay.nexolock.ui.components.IconAction
import com.vinay.nexolock.ui.components.NexoIcons
import com.vinay.nexolock.ui.components.SigBanner
import com.vinay.nexolock.ui.components.SigEmptyState
import com.vinay.nexolock.ui.components.SigGhostButton
import com.vinay.nexolock.ui.components.SigPill
import com.vinay.nexolock.ui.components.SigPrimaryButton
import com.vinay.nexolock.ui.components.SigSecondaryButton
import com.vinay.nexolock.ui.components.SigTone
import com.vinay.nexolock.ui.components.SignatureBackdrop
import com.vinay.nexolock.ui.formatBytes
import com.vinay.nexolock.ui.theme.NexoPalette
import com.vinay.nexolock.ui.theme.NexoRadius
import com.vinay.nexolock.ui.theme.NexoSpace
import com.vinay.nexolock.vault.VaultKind
import java.io.File
import java.util.Locale

/**
 * In-app viewer route. Decrypts the stored container on demand and keeps the
 * plaintext in the app-private cache only while this screen is composed.
 *
 * FLAG_SECURE is applied centrally by the navigation host for this route, so
 * every preview, loading state and failure message is covered for as long as
 * the screen exists. [releaseViewer] deletes the decrypted preview on dispose.
 */
@Composable
fun VaultViewerScreen(
    state: NexoUiState,
    vm: NexoViewModel,
    kind: VaultKind?,
    fileId: String?,
    entryId: String?,
    onBack: () -> Unit,
) {
    val viewer = state.viewer
    val displayName = viewer.file?.displayName ?: viewer.displayName
    val mimeType = viewer.file?.mimeType ?: viewer.mimeType
    val sizeBytes = viewer.file?.sizeBytes ?: viewer.totalBytes

    LaunchedEffect(kind, fileId, entryId) {
        if (kind == null) return@LaunchedEffect
        when {
            fileId != null -> vm.prepareVaultFile(kind, fileId)
            entryId != null -> vm.prepareVaultEntry(kind, entryId)
        }
    }

    DisposableEffect(Unit) {
        onDispose { vm.releaseViewer() }
    }

    SignatureBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NexoSpace.xs, vertical = NexoSpace.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                    size = 44.dp,
                )
                Spacer(Modifier.width(NexoSpace.xs))
                Column(Modifier.weight(1f)) {
                    Text(
                        displayName ?: "Vault item",
                        style = MaterialTheme.typography.titleSmall,
                        color = NexoPalette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    mimeType?.let { mime ->
                        Text(
                            "${formatBytes(sizeBytes)} · $mime",
                            style = MaterialTheme.typography.labelSmall,
                            color = NexoPalette.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (viewer.cachePath != null) {
                    SigPill("VERIFIED", SigTone.SUCCESS)
                    Spacer(Modifier.width(NexoSpace.xs))
                }
                // Restoring a copy never changes the vault; the export decrypts
                // and verifies the stored container again before writing to the
                // target.
                viewer.file?.let { file ->
                    IconAction(
                        icon = NexoIcons.Export,
                        contentDescription = "Export a verified copy of ${file.displayName}",
                        onClick = { vm.requestExportFile(file.id) },
                        tint = NexoPalette.Silver,
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NexoPalette.Border),
            )

            Box(Modifier.weight(1f)) {
                when (val load = viewer.load) {
                    is ViewerLoad.Failed -> ViewerMessage(
                        title = "Cannot open this file",
                        message = load.message,
                        isError = true,
                        actionLabel = "Back to vault",
                        onAction = onBack,
                    )

                    is ViewerLoad.Ready -> ViewerContent(
                        displayName = displayName ?: "Vault item",
                        mimeType = mimeType ?: "application/octet-stream",
                        integritySha256 = viewer.file?.integritySha256.orEmpty(),
                        path = File(load.cachePath),
                        onExport = viewer.file?.let { file ->
                            { vm.requestExportFile(file.id) }
                        },
                        onBack = onBack,
                    )

                    is ViewerLoad.Preparing -> ViewerLoading(viewer)

                    ViewerLoad.Idle -> ViewerMessage(
                        title = "Preview closed",
                        message = "The decrypted preview is no longer available. " +
                            "Return to the vault and open the file again.",
                        isError = false,
                        actionLabel = "Back to vault",
                        onAction = onBack,
                    )
                }
            }
        }
    }
}

private enum class ViewerKind { IMAGE, VIDEO, AUDIO, PDF, TEXT, DOCX, UNSUPPORTED }

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")
private val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "mkv", "webm", "mov", "3gp", "ts")
private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "ogg", "opus", "wav", "flac", "amr")
private val TEXT_EXTENSIONS = setOf(
    "txt", "md", "markdown", "csv", "tsv", "json", "xml", "log", "ini", "cfg",
    "yaml", "yml", "toml", "html", "htm", "css", "js", "ts", "kt", "java",
    "properties", "sql", "sh", "py", "gradle", "pro",
)

private fun viewerKind(mimeType: String, displayName: String): ViewerKind {
    val mime = mimeType.lowercase(Locale.US)
    val extension = displayName.substringAfterLast('.', "").lowercase(Locale.US)
    return when {
        mime.startsWith("image/") || extension in IMAGE_EXTENSIONS -> ViewerKind.IMAGE
        mime.startsWith("video/") || extension in VIDEO_EXTENSIONS -> ViewerKind.VIDEO
        mime.startsWith("audio/") || extension in AUDIO_EXTENSIONS -> ViewerKind.AUDIO
        mime == "application/pdf" || extension == "pdf" -> ViewerKind.PDF
        mime ==
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            extension == "docx" -> ViewerKind.DOCX
        mime.startsWith("text/") || extension in TEXT_EXTENSIONS -> ViewerKind.TEXT
        else -> ViewerKind.UNSUPPORTED
    }
}

@Composable
private fun ViewerContent(
    displayName: String,
    mimeType: String,
    integritySha256: String,
    path: File,
    onExport: (() -> Unit)?,
    onBack: () -> Unit,
) {
    when (viewerKind(mimeType, displayName)) {
        ViewerKind.IMAGE -> ImageViewer(path = path, displayName = displayName)
        ViewerKind.VIDEO -> VideoViewer(path = path, displayName = displayName)
        ViewerKind.AUDIO -> AudioViewer(path = path, displayName = displayName)
        ViewerKind.PDF -> PdfViewer(path = path)
        ViewerKind.TEXT -> TextDocumentViewer(path = path, byteSize = path.length())
        ViewerKind.DOCX -> DocxViewer(path = path)
        ViewerKind.UNSUPPORTED -> ViewerUnsupported(
            displayName = displayName,
            mimeType = mimeType,
            integritySha256 = integritySha256,
            onExport = onExport,
            onBack = onBack,
        )
    }
}

/**
 * Honest fallback for formats without a licensed in-app renderer. The vault
 * copy is already decrypted and verified; exporting it again re-verifies the
 * stored container, so the user can open it in an app that supports the format.
 */
@Composable
private fun ViewerUnsupported(
    displayName: String,
    mimeType: String,
    integritySha256: String,
    onExport: (() -> Unit)?,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexoSpace.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.widthIn(max = 480.dp)) {
            SigEmptyState(
                icon = NexoIcons.Document,
                title = "No in-app viewer for this format",
                message = buildString {
                    append("\"$displayName\" ($mimeType) is stored and verified, ")
                    append("but NEXO has no renderer for this format.\n\n")
                    append("Supported in-app: images, video and audio (Media3), PDF, plain ")
                    append("text and DOCX text. XLS/XLSX/PPT/PPTX are not rendered.")
                    if (integritySha256.isNotBlank()) {
                        append("\n\nSHA-256: ")
                        append(integritySha256.take(24))
                        append("…")
                    }
                },
            )
            if (onExport != null) {
                SigPrimaryButton(
                    text = "Export a verified copy",
                    onClick = onExport,
                    icon = NexoIcons.Export,
                )
                Spacer(Modifier.height(NexoSpace.sm))
            }
            SigGhostButton(
                text = "Back to vault",
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NexoSpace.lg))
        }
    }
}

@Composable
private fun ViewerLoading(viewer: ViewerUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NexoSpace.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.widthIn(max = 440.dp)) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(26.dp),
                color = NexoPalette.Silver,
                trackColor = NexoPalette.SurfaceAlt,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.height(NexoSpace.lg))
            Text(
                "Decrypting and verifying " +
                    (viewer.file?.displayName ?: viewer.displayName ?: "the selected file") +
                    "…",
                style = MaterialTheme.typography.titleSmall,
                color = NexoPalette.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(NexoSpace.md))
            val total = viewer.totalBytes
            if (total > 0L) {
                LinearProgressIndicator(
                    progress = { (viewer.progressBytes.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = NexoPalette.Silver,
                    trackColor = NexoPalette.SurfaceAlt,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = NexoPalette.Silver,
                    trackColor = NexoPalette.SurfaceAlt,
                )
            }
            Spacer(Modifier.height(NexoSpace.xs))
            Text(
                if (total > 0L) {
                    "${formatBytes(viewer.progressBytes)} of ${formatBytes(total)}"
                } else {
                    "${formatBytes(viewer.progressBytes)} decrypted"
                },
                style = MaterialTheme.typography.labelSmall,
                color = NexoPalette.TextMuted,
            )
            Spacer(Modifier.height(NexoSpace.lg))
            SigBanner(
                text = "The temporary preview is written only to this app's private " +
                    "cache and is deleted when you leave this screen or lock the vault.",
                tone = SigTone.INFO,
            )
        }
    }
}

@Composable
internal fun ViewerMessage(
    title: String,
    message: String,
    isError: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = NexoSpace.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(Modifier.widthIn(max = 440.dp)) {
            SigEmptyState(
                icon = if (isError) Icons.Filled.Warning else NexoIcons.LockOpen,
                title = title,
                message = message,
            )
            SigSecondaryButton(text = actionLabel, onClick = onAction)
            Spacer(Modifier.height(NexoSpace.lg))
        }
    }
}

/** Inline failure used by media/document panes; the top bar still offers Back. */
@Composable
internal fun ViewerFailure(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = NexoSpace.screenHorizontal),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(NexoRadius.card))
                .background(NexoPalette.Error.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = NexoPalette.Error,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.height(NexoSpace.md))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = NexoPalette.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 440.dp),
        )
    }
}
