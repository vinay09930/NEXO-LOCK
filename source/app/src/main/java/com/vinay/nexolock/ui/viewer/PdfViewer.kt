package com.vinay.nexolock.ui.viewer

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.sqrt

/**
 * Platform [PdfRenderer] viewer. Pages are rendered one at a time at the
 * available width (or 2× for the zoom toggle) with a hard pixel budget, so a
 * large document cannot exhaust the heap. Page navigation is explicit; PDF text
 * selection and reflow are not supported.
 */
@Composable
internal fun PdfViewer(path: File) {
    var document by remember(path) {
        mutableStateOf<PreviewState<Int>>(PreviewState.Loading)
    }
    var pageIndex by remember(path) { mutableIntStateOf(0) }
    var zoom by remember(path) { mutableStateOf(false) }
    var page by remember(path, pageIndex, zoom) {
        mutableStateOf<PreviewState<Bitmap>>(PreviewState.Loading)
    }

    LaunchedEffect(path) {
        document = PreviewState.Loading
        val result = withContext(Dispatchers.IO) { runCatching { pdfPageCount(path) } }
        document = result.fold(
            onSuccess = { count ->
                if (count == 0) {
                    PreviewState.Failed("This PDF contains no pages.")
                } else {
                    PreviewState.Ready(count)
                }
            },
            onFailure = { PreviewState.Failed(pdfFailureMessage(it)) },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val availableWidth = with(density) { maxWidth.toPx() }.toInt()
        val documentState = document
        val pageCount = (documentState as? PreviewState.Ready)?.value ?: 0

        LaunchedEffect(path, pageIndex, zoom, availableWidth, documentState) {
            if (documentState !is PreviewState.Ready || availableWidth <= 0) return@LaunchedEffect
            page = PreviewState.Loading
            val target = if (zoom) availableWidth * 2 else availableWidth
            val result = withContext(Dispatchers.IO) {
                runCatching { renderPdfPage(path, pageIndex, target) }
            }
            page = result.fold(
                onSuccess = { PreviewState.Ready(it) },
                onFailure = { PreviewState.Failed(pdfFailureMessage(it)) },
            )
        }

        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF1B1B1F)),
                contentAlignment = Alignment.Center,
            ) {
                val pageState = page
                when {
                    documentState is PreviewState.Failed ->
                        ViewerFailure(documentState.message)

                    pageState is PreviewState.Failed -> ViewerFailure(pageState.message)

                    pageState is PreviewState.Ready && zoom -> {
                        val bitmap = pageState.value
                        val horizontal = rememberScrollState()
                        val vertical = rememberScrollState()
                        Box(
                            Modifier
                                .fillMaxSize()
                                .horizontalScroll(horizontal)
                                .verticalScroll(vertical),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "PDF page ${pageIndex + 1}",
                                modifier = Modifier
                                    .width(with(density) { bitmap.width.toDp() })
                                    .height(with(density) { bitmap.height.toDp() }),
                            )
                        }
                    }

                    pageState is PreviewState.Ready -> Image(
                        bitmap = pageState.value.asImageBitmap(),
                        contentDescription = "PDF page ${pageIndex + 1}",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> CircularProgressIndicator()
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val pageReady = page is PreviewState.Ready
                TextButton(
                    enabled = pageIndex > 0 && pageReady,
                    onClick = { pageIndex -= 1 },
                ) { Text("Previous") }
                Spacer(Modifier.weight(1f))
                Text(
                    if (pageCount > 0) "Page ${pageIndex + 1} of $pageCount" else "Loading…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(
                    enabled = pageCount > 0 && pageIndex + 1 < pageCount && pageReady,
                    onClick = { pageIndex += 1 },
                ) { Text("Next") }
                TextButton(
                    enabled = pageCount > 0,
                    onClick = { zoom = !zoom },
                ) { Text(if (zoom) "Fit" else "2×") }
            }
        }
    }
}

private fun pdfPageCount(path: File): Int {
    ParcelFileDescriptor.open(path, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer -> return renderer.pageCount }
    }
}

private fun renderPdfPage(path: File, index: Int, targetWidth: Int): Bitmap {
    ParcelFileDescriptor.open(path, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            if (index !in 0 until renderer.pageCount) {
                error("Page ${index + 1} is outside this PDF.")
            }
            renderer.openPage(index).use { page ->
                var width = targetWidth.coerceIn(320, 2200)
                var height = (width.toFloat() * page.height.toFloat() / page.width.toFloat())
                    .toInt()
                    .coerceAtLeast(1)
                val pixels = width.toLong() * height.toLong()
                if (pixels > MAX_PAGE_PIXELS) {
                    val scale = sqrt(MAX_PAGE_PIXELS.toDouble() / pixels.toDouble())
                    width = (width * scale).toInt().coerceAtLeast(1)
                    height = (height * scale).toInt().coerceAtLeast(1)
                }
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(AndroidColor.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }
    }
}

private fun pdfFailureMessage(error: Throwable): String = when (error) {
    is SecurityException ->
        "This PDF is encrypted or password-protected; the platform renderer cannot open it."
    is IOException ->
        "The PDF could not be read: ${error.message ?: "damaged or unsupported file"}."
    else ->
        "The PDF could not be rendered (${error.javaClass.simpleName})."
}

private const val MAX_PAGE_PIXELS = 4_000_000L
