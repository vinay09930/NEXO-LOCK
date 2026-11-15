package com.vinay.nexolock.ui.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bitmap image viewer with pinch zoom/pan. Images are sampled to at most
 * 2048 px on the long edge so a large camera photo cannot exhaust the heap;
 * EXIF rotation is applied for JPEGs.
 */
@Composable
internal fun ImageViewer(path: File, displayName: String) {
    var load by remember(path) {
        mutableStateOf<PreviewState<Bitmap>>(PreviewState.Loading)
    }
    var scale by remember(path) { mutableFloatStateOf(1f) }
    var offset by remember(path) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(path) {
        load = PreviewState.Loading
        val result = withContext(Dispatchers.IO) {
            runCatching { decodeImage(path.absolutePath) }
        }
        load = result.fold(
            onSuccess = { PreviewState.Ready(it) },
            onFailure = {
                PreviewState.Failed(it.message ?: "The image could not be decoded.")
            },
        )
    }

    when (val current = load) {
        is PreviewState.Failed -> ViewerFailure(current.message)

        PreviewState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }

        is PreviewState.Ready -> Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 8f)
                        scale = next
                        offset = if (next <= 1f) Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap = current.value.asImageBitmap(),
                contentDescription = displayName,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
            if (scale > 1.01f) {
                TextButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(12.dp),
                ) { Text("Reset zoom") }
            }
        }
    }
}

private fun decodeImage(path: String, maxDimension: Int = 2048): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        error("The platform image decoder could not read this image (unsupported or damaged format).")
    }
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDimension ||
        bounds.outHeight / (sample * 2) >= maxDimension
    ) {
        sample *= 2
    }
    val decoded = BitmapFactory.decodeFile(
        path,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    ) ?: error("Not enough memory to display this image.")

    val rotation = runCatching { exifRotationDegrees(path) }.getOrDefault(0f)
    if (rotation == 0f) return decoded
    return runCatching {
        val matrix = Matrix().apply { postRotate(rotation) }
        val rotated = Bitmap.createBitmap(
            decoded,
            0,
            0,
            decoded.width,
            decoded.height,
            matrix,
            true,
        )
        if (rotated !== decoded) decoded.recycle()
        rotated
    }.getOrDefault(decoded)
}

private fun exifRotationDegrees(path: String): Float {
    val orientation = ExifInterface(path).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
    )
    return when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
}
