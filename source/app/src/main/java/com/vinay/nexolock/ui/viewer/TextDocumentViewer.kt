package com.vinay.nexolock.ui.viewer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vinay.nexolock.ui.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.util.zip.ZipException
import java.util.zip.ZipInputStream

private const val TEXT_PREVIEW_LIMIT = 2 * 1024 * 1024
private const val DOCX_XML_LIMIT = 8 * 1024 * 1024
private const val DOCX_TEXT_LIMIT = 500_000

private data class TextPreview(val text: String, val truncated: Boolean, val binary: Boolean)

private data class DocxText(val text: String, val truncated: Boolean)

/**
 * Read-only plain-text preview (UTF-8) with selectable text. The first 2 MB
 * are shown; larger files say so explicitly instead of pretending to be whole.
 */
@Composable
internal fun TextDocumentViewer(path: File, byteSize: Long) {
    var load by remember(path) {
        mutableStateOf<PreviewState<TextPreview>>(PreviewState.Loading)
    }

    LaunchedEffect(path) {
        load = PreviewState.Loading
        val result = withContext(Dispatchers.IO) {
            runCatching { readTextPreview(path) }
        }
        load = result.fold(
            onSuccess = { PreviewState.Ready(it) },
            onFailure = {
                PreviewState.Failed(it.message ?: "The text file could not be read.")
            },
        )
    }

    when (val current = load) {
        is PreviewState.Failed -> ViewerFailure(current.message)

        PreviewState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is PreviewState.Ready -> {
            val body = current.value
            if (body.binary) {
                ViewerFailure(
                    "This file contains binary data, not UTF-8 text. It needs a format-specific viewer.",
                )
            } else {
                Column(Modifier.fillMaxSize()) {
                    Text(
                        buildString {
                            append("Read-only text preview · decoded as UTF-8")
                            if (body.truncated) {
                                append(" · showing the first ")
                                append(formatBytes(TEXT_PREVIEW_LIMIT.toLong()))
                                append(" of ")
                                append(formatBytes(byteSize))
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    SelectionContainer {
                        Text(
                            text = body.text,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = 17.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

/**
 * DOCX text extraction from `word/document.xml`. Paragraph breaks, tabs and
 * line breaks are preserved; images, tables, styling and embedded objects are
 * not rendered. This is explicitly labelled in the UI.
 */
@Composable
internal fun DocxViewer(path: File) {
    var load by remember(path) {
        mutableStateOf<PreviewState<DocxText>>(PreviewState.Loading)
    }

    LaunchedEffect(path) {
        load = PreviewState.Loading
        val result = withContext(Dispatchers.IO) {
            runCatching { extractDocxText(path) }
        }
        load = result.fold(
            onSuccess = { PreviewState.Ready(it) },
            onFailure = { PreviewState.Failed(docxFailureMessage(it)) },
        )
    }

    when (val current = load) {
        is PreviewState.Failed -> ViewerFailure(current.message)

        PreviewState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is PreviewState.Ready -> {
            val document = current.value
            Column(Modifier.fillMaxSize()) {
                Text(
                    buildString {
                        append("DOCX text view · formatting, tables and images are not rendered")
                        if (document.truncated) {
                            append(" · showing the first ")
                            append(DOCX_TEXT_LIMIT)
                            append(" characters")
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                SelectionContainer {
                    Text(
                        text = document.text,
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun readTextPreview(file: File): TextPreview {
    val bytes = FileInputStream(file).use { input ->
        readBounded(input, TEXT_PREVIEW_LIMIT + 1)
    }
    val truncated = bytes.size > TEXT_PREVIEW_LIMIT
    val visible = minOf(bytes.size, TEXT_PREVIEW_LIMIT)
    if ((0 until minOf(visible, 8192)).any { bytes[it] == 0.toByte() }) {
        return TextPreview(text = "", truncated = truncated, binary = true)
    }
    return TextPreview(
        text = String(bytes, 0, visible, Charsets.UTF_8),
        truncated = truncated,
        binary = false,
    )
}

private fun extractDocxText(file: File): DocxText {
    ZipInputStream(FileInputStream(file).buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (entry.name == "word/document.xml") {
                val xmlBytes = readBounded(zip, DOCX_XML_LIMIT + 1)
                if (xmlBytes.size > DOCX_XML_LIMIT) {
                    error("The Word document body is too large to preview.")
                }
                val text = docxXmlToText(String(xmlBytes, Charsets.UTF_8))
                if (text.isBlank()) error("The document contains no extractable text.")
                return DocxText(
                    text = text.take(DOCX_TEXT_LIMIT),
                    truncated = text.length > DOCX_TEXT_LIMIT,
                )
            }
            zip.closeEntry()
        }
    }
    error("This DOCX has no word/document.xml part; it may be damaged or not a Word document.")
}

private fun docxXmlToText(xml: String): String {
    val withBreaks = xml
        .replace("</w:p>", "\n")
        .replace("<w:tab/>", "\t")
        .replace("<w:tab />", "\t")
        .replace("<w:br/>", "\n")
        .replace("<w:br />", "\n")
    return decodeXmlEntities(withBreaks.replace(Regex("<[^>]+>"), ""))
        .replace("\r\n", "\n")
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun decodeXmlEntities(value: String): String {
    var text = value
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
    text = Regex("&#x([0-9a-fA-F]+);").replace(text) { match ->
        val code = match.groupValues[1].toIntOrNull(16)
        if (code != null && code in 0..0x10FFFF) String(Character.toChars(code)) else match.value
    }
    text = Regex("&#(\\d+);").replace(text) { match ->
        val code = match.groupValues[1].toIntOrNull()
        if (code != null && code in 0..0x10FFFF) String(Character.toChars(code)) else match.value
    }
    return text.replace("&amp;", "&")
}

private fun readBounded(input: InputStream, limit: Int): ByteArray {
    val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
    val buffer = ByteArray(16 * 1024)
    var total = 0
    while (total < limit) {
        val read = input.read(buffer, 0, minOf(buffer.size, limit - total))
        if (read < 0) break
        output.write(buffer, 0, read)
        total += read
    }
    return output.toByteArray()
}

private fun docxFailureMessage(error: Throwable): String = when (error) {
    is ZipException -> "This file is not a valid DOCX (ZIP) container."
    is IllegalStateException -> error.message ?: "The DOCX could not be read."
    else -> "The DOCX could not be read (${error.javaClass.simpleName})."
}
