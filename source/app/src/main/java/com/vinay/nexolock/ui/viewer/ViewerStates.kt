package com.vinay.nexolock.ui.viewer

/**
 * One explicit state for a local preview load.
 *
 * The previous viewers kept a nullable payload plus independent `loading` and
 * `failure` flags, so "loading with a decoded bitmap" and "failed with a
 * bitmap" were representable. Here the payload, the in-flight state and the
 * failure are mutually exclusive by construction.
 */
internal sealed interface PreviewState<out T> {
    data object Loading : PreviewState<Nothing>
    data class Ready<T>(val value: T) : PreviewState<T>
    data class Failed(val message: String) : PreviewState<Nothing>
}
