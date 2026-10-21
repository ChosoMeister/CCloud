package com.pira.ccloud.core.tmdb

import android.content.Context
import com.pira.ccloud.BuildConfig

/**
 * Process-wide TMDB repository. Credential precedence: user-entered token (Settings) first,
 * then the personal-build token injected from gitignored local.properties into BuildConfig.
 */
object TmdbProvider {
    @Volatile private var repo: TmdbRepository? = null
    @Volatile private var credentials: TmdbCredentials? = null

    fun credential(context: Context): String? =
        creds(context).value ?: BuildConfig.TMDB_TOKEN.takeIf { it.isNotBlank() }

    fun isEnabled(context: Context): Boolean = credential(context) != null

    /** Drop in-memory responses after a user changes or removes their credential. */
    fun invalidateRepository() {
        synchronized(this) { repo = null }
    }

    fun repository(context: Context): TmdbRepository = repo ?: synchronized(this) {
        repo ?: run {
            val app = context.applicationContext
            TmdbRepository(
                transport = OkHttpTmdbTransport({ credential(app) }),
                isEnabled = { isEnabled(app) },
            ).also { repo = it }
        }
    }

    private fun creds(context: Context): TmdbCredentials = credentials ?: synchronized(this) {
        credentials ?: TmdbCredentials(context).also { credentials = it }
    }
}
