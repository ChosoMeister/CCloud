package com.pira.ccloud.core.catalog

import android.content.Context
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import com.pira.ccloud.data.model.Movie
import com.pira.ccloud.data.model.Series
import com.pira.ccloud.data.model.Poster
import com.pira.ccloud.utils.StorageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Persist metadata off the UI thread, then navigate only if the origin is still current. */
object CatalogNavigation {
    fun openMovie(context: Context, nav: NavController, movie: Movie) = persist(nav, "single_movie/${movie.id}") {
        StorageUtils.saveMovieToFile(context, movie)
    }
    fun openSeries(context: Context, nav: NavController, series: Series) = persist(nav, "single_series/${series.id}") {
        StorageUtils.saveSeriesToFile(context, series)
    }
    fun openPoster(context: Context, nav: NavController, poster: Poster) {
        if (poster.isSeries()) openSeries(context, nav, poster.toSeries())
        else if (poster.isMovie()) openMovie(context, nav, poster.toMovie())
    }
    private fun persist(nav: NavController, route: String, write: () -> Unit) {
        val origin = nav.currentBackStackEntry ?: return
        origin.lifecycleScope.launch {
            withContext(Dispatchers.IO) { write() }
            if (nav.currentBackStackEntry == origin) nav.navigate(route) { launchSingleTop = true }
        }
    }
}
