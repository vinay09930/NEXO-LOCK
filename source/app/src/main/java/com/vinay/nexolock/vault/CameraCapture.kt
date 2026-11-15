package com.vinay.nexolock.vault

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Size
import android.view.Surface
import android.view.WindowManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Minimal front-camera JPEG capture built on the framework Camera2 API.
 *
 * No new dependency is added. Capture is deliberately short-lived: open, take
 * one frame, close. Every failure path returns a [Result.failure] so the
 * Intruder Vault can record a visible failure state instead of pretending a
 * photo exists.
 */
object CameraCapture {

    sealed interface CaptureResult {
        data class Success(val jpeg: ByteArray) : CaptureResult
        data class Failure(val status: CaptureStatus, val message: String) : CaptureResult
    }

    suspend fun captureFront(context: Context, timeoutMs: Long = 9_000L): CaptureResult {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return CaptureResult.Failure(CaptureStatus.PERMISSION_MISSING, "Camera permission is not granted.")
        }
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            ?: return CaptureResult.Failure(CaptureStatus.CAMERA_UNAVAILABLE, "Camera service is unavailable.")
        val cameraId = runCatching {
            manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            }
        }.getOrNull()
            ?: return CaptureResult.Failure(CaptureStatus.CAMERA_UNAVAILABLE, "No front camera was found.")

        val result = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<CaptureResult> { continuation ->
                val thread = HandlerThread("nexo-intruder-camera").apply { start() }
                val handler = Handler(thread.looper)
                // Never assume a fixed resolution: some front cameras do not
                // expose 960x1280 JPEG. Query the device's stream configuration
                // and use a supported size, falling back to the closest match.
                val jpegSize = supportedJpegSize(manager, cameraId)
                val reader = runCatching {
                    ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 1)
                }.getOrNull()
                if (reader == null) {
                    thread.quitSafely()
                    continuation.resume(
                        CaptureResult.Failure(CaptureStatus.FAILED, "The JPEG reader could not be created."),
                    )
                    return@suspendCancellableCoroutine
                }

                var device: CameraDevice? = null
                var session: CameraCaptureSession? = null
                var resumed = false

                fun finish(value: CaptureResult) {
                    if (resumed) return
                    resumed = true
                    runCatching { session?.close() }
                    runCatching { device?.close() }
                    runCatching { reader.close() }
                    thread.quitSafely()
                    if (continuation.isActive) continuation.resume(value)
                }

                reader.setOnImageAvailableListener({ source ->
                    val image = runCatching { source.acquireLatestImage() }.getOrNull()
                    if (image == null) {
                        finish(CaptureResult.Failure(CaptureStatus.FAILED, "The camera returned no frame."))
                        return@setOnImageAvailableListener
                    }
                    val bytes = runCatching {
                        val buffer = image.planes[0].buffer
                        ByteArray(buffer.remaining()).also { buffer.get(it) }
                    }.getOrNull()
                    image.close()
                    if (bytes == null || bytes.isEmpty()) {
                        finish(CaptureResult.Failure(CaptureStatus.FAILED, "The camera frame was empty."))
                    } else if (bytes.size < 4 ||
                        bytes[0] != 0xFF.toByte() ||
                        bytes[1] != 0xD8.toByte()
                    ) {
                        // A CAPTURED record must contain a real JPEG, never a
                        // raw buffer that the gallery could not decode.
                        finish(
                            CaptureResult.Failure(
                                CaptureStatus.FAILED,
                                "The camera returned a frame that is not a JPEG.",
                            ),
                        )
                    } else {
                        finish(CaptureResult.Success(bytes))
                    }
                }, handler)

                val stateCallback = object : CameraDevice.StateCallback() {
                    override fun onOpened(camera: CameraDevice) {
                        device = camera
                        try {
                            val surface: Surface = reader.surface
                            @Suppress("DEPRECATION")
                            val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                            request.addTarget(surface)
                            request.set(
                                CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE,
                            )
                            request.set(CaptureRequest.JPEG_ORIENTATION, orientation(context, manager, cameraId))
                            camera.createCaptureSession(
                                listOf(surface),
                                object : CameraCaptureSession.StateCallback() {
                                    override fun onConfigured(ready: CameraCaptureSession) {
                                        session = ready
                                        runCatching {
                                            ready.capture(request.build(), null, handler)
                                        }.onFailure {
                                            finish(
                                                CaptureResult.Failure(
                                                    CaptureStatus.FAILED,
                                                    "The capture request was rejected.",
                                                ),
                                            )
                                        }
                                    }

                                    override fun onConfigureFailed(failed: CameraCaptureSession) {
                                        finish(
                                            CaptureResult.Failure(
                                                CaptureStatus.FAILED,
                                                "The camera session could not be configured.",
                                            ),
                                        )
                                    }
                                },
                                handler,
                            )
                        } catch (error: CameraAccessException) {
                            finish(
                                CaptureResult.Failure(
                                    CaptureStatus.CAMERA_BUSY,
                                    "Camera access failed: ${error.reason}.",
                                ),
                            )
                        } catch (error: Exception) {
                            finish(
                                CaptureResult.Failure(
                                    CaptureStatus.FAILED,
                                    "Camera setup failed: ${error.javaClass.simpleName}.",
                                ),
                            )
                        }
                    }

                    override fun onDisconnected(camera: CameraDevice) {
                        finish(CaptureResult.Failure(CaptureStatus.CAMERA_BUSY, "The camera was disconnected."))
                    }

                    override fun onError(camera: CameraDevice, error: Int) {
                        finish(
                            CaptureResult.Failure(
                                CaptureStatus.FAILED,
                                "Camera error $error.",
                            ),
                        )
                    }
                }

                try {
                    manager.openCamera(cameraId, stateCallback, handler)
                } catch (error: SecurityException) {
                    finish(CaptureResult.Failure(CaptureStatus.PERMISSION_MISSING, "Camera permission was revoked."))
                } catch (error: CameraAccessException) {
                    finish(CaptureResult.Failure(CaptureStatus.CAMERA_BUSY, "The camera is in use or unavailable."))
                } catch (error: Exception) {
                    finish(CaptureResult.Failure(CaptureStatus.FAILED, "The camera could not be opened."))
                }

                continuation.invokeOnCancellation {
                    handler.post { finish(CaptureResult.Failure(CaptureStatus.FAILED, "Capture was cancelled.")) }
                }
            }
        }
        return result ?: CaptureResult.Failure(CaptureStatus.FAILED, "The camera did not respond in time.")
    }

    /**
     * Pick a JPEG output size the camera actually advertises.
     *
     * 960x1280 was previously assumed unconditionally (V5 audit D11). Most
     * devices support it, but not all; a truthful capture failure caused by an
     * unsupported size is avoidable. Choose the advertised size closest to the
     * intended ~1.2 MP frame, with the largest available size as a fallback.
     */
    private fun supportedJpegSize(manager: CameraManager, cameraId: String): Size {
        val sizes = runCatching {
            manager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.JPEG)
                ?.filter { it.width > 0 && it.height > 0 }
                .orEmpty()
        }.getOrDefault(emptyList())
        if (sizes.isEmpty()) return Size(960, 1280)
        val targetArea = 960L * 1280L
        return sizes.minByOrNull { size ->
            abs(size.width.toLong() * size.height.toLong() - targetArea)
        } ?: sizes.maxByOrNull { it.width.toLong() * it.height.toLong() } ?: Size(960, 1280)
    }

    private fun orientation(context: Context, manager: CameraManager, cameraId: String): Int {
        val sensor = runCatching {
            manager.getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
        }.getOrDefault(0)
        val facing = runCatching {
            manager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.LENS_FACING)
        }.getOrNull()
        @Suppress("DEPRECATION")
        val displayRotation = runCatching {
            when (context.getSystemService(Context.WINDOW_SERVICE)
                .let { it as WindowManager }.defaultDisplay.rotation
            ) {
                Surface.ROTATION_90 -> 90
                Surface.ROTATION_180 -> 180
                Surface.ROTATION_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
        return if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
            (sensor + displayRotation) % 360
        } else {
            (sensor - displayRotation + 360) % 360
        }
    }
}
