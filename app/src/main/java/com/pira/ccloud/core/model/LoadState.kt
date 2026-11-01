package com.pira.ccloud.core.model

import com.pira.ccloud.core.network.FailureKind

/** Explicit screen/section states. `Partial` = usable data plus a non-fatal failure. */
sealed interface LoadState<out T> {
    data object Loading : LoadState<Nothing>
    data object Empty : LoadState<Nothing>
    data class Ready<T>(val data: T, val stale: Boolean = false) : LoadState<T>
    data class Partial<T>(val data: T, val failure: FailureKind) : LoadState<T>
    data class Failed(val failure: FailureKind) : LoadState<Nothing>
}

/** Optional providers are capability-gated; core browse/playback never depends on them. */
data class FeatureFlags(
    val tmdbEnabled: Boolean = false,
    val cinematicHome: Boolean = true,
    val reducedMotion: Boolean = false,
) {
    companion object {
        /** TMDB is only enabled when the user supplied a token; never embedded in the APK. */
        fun resolve(userTmdbToken: String?, cinematicHome: Boolean = true, reducedMotion: Boolean = false) =
            FeatureFlags(tmdbEnabled = !userTmdbToken.isNullOrBlank(), cinematicHome = cinematicHome, reducedMotion = reducedMotion)
    }
}
