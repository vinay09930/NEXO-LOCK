package com.vinay.nexolock.ui.viewer

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * Video playback through Media3 ExoPlayer. The media source is the verified
 * decrypted preview in app-private cache; nothing is exposed to other apps.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
internal fun VideoViewer(path: File, displayName: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var failure by remember(path) { mutableStateOf<String?>(null) }

    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(path)))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                failure = buildString {
                    append("Playback failed (")
                    append(error.errorCodeName)
                    append(")")
                    error.message?.let { append(": $it") }
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val message = failure
    if (message != null) {
        ViewerFailure(message)
        return
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    contentDescription = displayName
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Explicit audio transport state. `playing`, `ended` and `failure` used to be
 * independent flags, so "playing and ended" or "ended and failed" were
 * representable. One state makes the transport mutually exclusive.
 */
private sealed interface AudioTransport {
    data object Paused : AudioTransport
    data object Playing : AudioTransport
    data object Ended : AudioTransport
    data class Failed(val message: String) : AudioTransport
}

/**
 * Audio playback through Media3 ExoPlayer with a Compose transport bar.
 * PlayerView is video-centric, so audio gets explicit position/duration UI.
 */
@Composable
internal fun AudioViewer(path: File, displayName: String) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var transport by remember(path) {
        mutableStateOf<AudioTransport>(AudioTransport.Paused)
    }
    var durationMs by remember(path) { mutableLongStateOf(0L) }
    var positionMs by remember(path) { mutableLongStateOf(0L) }
    var dragging by remember(path) { mutableStateOf(false) }
    var dragPositionMs by remember(path) { mutableLongStateOf(0L) }

    val player = remember(path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(path)))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                transport = when {
                    isPlaying -> AudioTransport.Playing
                    transport is AudioTransport.Playing -> AudioTransport.Paused
                    else -> transport
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> {
                        durationMs = player.duration.coerceAtLeast(0L)
                        if (transport is AudioTransport.Ended) {
                            transport = AudioTransport.Paused
                        }
                    }
                    Player.STATE_ENDED -> {
                        positionMs = player.duration.coerceAtLeast(0L)
                        transport = AudioTransport.Ended
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                transport = AudioTransport.Failed(
                    buildString {
                        append("Playback failed (")
                        append(error.errorCodeName)
                        append(")")
                        error.message?.let { append(": $it") }
                    },
                )
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(transport) {
        while (transport is AudioTransport.Playing) {
            positionMs = player.currentPosition
            delay(300)
        }
    }

    val current = transport
    if (current is AudioTransport.Failed) {
        ViewerFailure(current.message)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "AUDIO",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            displayName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(26.dp))
        Text(
            "${formatDuration(if (dragging) dragPositionMs else positionMs)} / ${formatDuration(durationMs)}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(
            value = (if (dragging) dragPositionMs else positionMs).toFloat(),
            onValueChange = {
                dragging = true
                dragPositionMs = it.toLong()
            },
            onValueChangeFinished = {
                player.seekTo(dragPositionMs)
                positionMs = dragPositionMs
                dragging = false
            },
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (transport is AudioTransport.Ended) {
                    player.seekTo(0)
                    positionMs = 0L
                    transport = AudioTransport.Paused
                }
                if (player.isPlaying) player.pause() else player.play()
            },
        ) {
            Text(
                when (transport) {
                    AudioTransport.Playing -> "Pause"
                    AudioTransport.Ended -> "Play again"
                    else -> "Play"
                },
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Media3 ExoPlayer · decrypted preview deleted when you leave",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private fun formatDuration(milliseconds: Long): String {
    val totalSeconds = (milliseconds.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
