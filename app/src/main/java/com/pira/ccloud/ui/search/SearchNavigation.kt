package com.pira.ccloud.ui.search

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pira.ccloud.core.model.ContentType
import com.pira.ccloud.core.tmdb.TmdbGenre

data class TmdbPersonFilter(val id: Int, val name: String)

data class PendingSearchFilter(
    val type: ContentType,
    val genre: TmdbGenre? = null,
    val person: TmdbPersonFilter? = null,
)

/** One-shot handoff from title details to the shared Search destination. */
object SearchNavigation {
    var pendingFilter by mutableStateOf<PendingSearchFilter?>(null)
}
