package com.pira.ccloud.core.network

import java.io.IOException
import java.net.SocketTimeoutException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

/** Typed failure classes so the UI never shows raw API errors. */
enum class FailureKind { OFFLINE, TIMEOUT, UNAUTHORIZED, FORBIDDEN, NOT_FOUND, RATE_LIMITED, SERVER, INVALID_DATA, UNKNOWN }

object FailureClassifier {
    fun fromHttp(code: Int): FailureKind = when (code) {
        401 -> FailureKind.UNAUTHORIZED
        403 -> FailureKind.FORBIDDEN
        404 -> FailureKind.NOT_FOUND
        408 -> FailureKind.TIMEOUT
        429 -> FailureKind.RATE_LIMITED
        in 500..599 -> FailureKind.SERVER
        else -> FailureKind.UNKNOWN
    }

    fun fromThrowable(t: Throwable): FailureKind = when (t) {
        is SocketTimeoutException -> FailureKind.TIMEOUT
        is java.net.UnknownHostException, is java.net.ConnectException -> FailureKind.OFFLINE
        is kotlinx.serialization.SerializationException, is IllegalArgumentException -> FailureKind.INVALID_DATA
        is IOException -> FailureKind.OFFLINE
        else -> FailureKind.UNKNOWN
    }

    fun isTransient(kind: FailureKind): Boolean =
        kind == FailureKind.TIMEOUT || kind == FailureKind.RATE_LIMITED || kind == FailureKind.SERVER || kind == FailureKind.OFFLINE
}

/** Exponential backoff with full jitter, honoring Retry-After, capped. */
class RetryPolicy(
    val maxAttempts: Int = 3,
    private val baseDelayMs: Long = 500,
    private val maxDelayMs: Long = 8_000,
    private val random: Random = Random.Default,
) {
    /** Delay before retry number [attempt] (1-based), or null if no retry should happen. */
    fun delayFor(attempt: Int, kind: FailureKind, retryAfterHeader: String? = null,
                 nowMs: Long = System.currentTimeMillis()): Long? {
        if (attempt >= maxAttempts || !FailureClassifier.isTransient(kind)) return null
        // Respect the server: if it asks for longer than we are willing to wait, don't retry early.
        parseRetryAfter(retryAfterHeader, nowMs)?.let { return if (it <= maxDelayMs * 4) it else null }
        val exp = (baseDelayMs shl (attempt - 1).coerceAtMost(16)).coerceAtMost(maxDelayMs)
        return random.nextLong(exp / 2, exp + 1)
    }

    companion object {
        /** Supports delta-seconds and HTTP-date forms. */
        /** API-24 safe (no java.time). */
        fun parseRetryAfter(value: String?, nowMs: Long = System.currentTimeMillis()): Long? {
            val v = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            v.toLongOrNull()?.let { return if (it >= 0) it * 1000 else null }
            return try {
                val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT"); isLenient = false
                }
                val at = fmt.parse(v)?.time ?: return null
                (at - nowMs).coerceAtLeast(0)
            } catch (_: Exception) { null }
        }
    }
}
