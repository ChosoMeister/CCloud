package com.pira.ccloud.core.matching

import com.pira.ccloud.core.model.ContentType
import java.text.Normalizer

/**
 * Deterministic title normalization for cross-provider matching.
 *
 * Adapted (not copied verbatim) from the token-overlap approach in
 * msharifi99/30nama-tizenos `tizen-app/js/tmdb.js` (MIT), extended with Persian/Arabic
 * character folding, Persian digits, ZWNJ handling and article stripping.
 */
object TitleNormalizer {
    private val combiningMarks = Regex("\\p{Mn}+")
    private val nonWord = Regex("[^a-z0-9\\u0600-\\u06FF]+")
    private val trailingYear = Regex("\\s*[(\\[]?\\b(19|20)\\d{2}[)\\]]?\\s*$")
    private val leadingArticle = Regex("^(the|a|an) ")

    /** Arabic → Persian letter variants, tatweel, and diacritics (harakat). */
    private fun foldArabicScript(s: String): String {
        val sb = StringBuilder(s.length)
        for (ch in s) {
            when (ch) {
                'ي', 'ى', 'ئ' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ة' -> sb.append('ه')
                'أ', 'إ', 'آ', 'ٱ' -> sb.append('ا')
                'ؤ' -> sb.append('و')
                '\u0640' -> Unit // tatweel
                '\u200C', '\u200D', '\u200F', '\u200E' -> sb.append(' ') // ZWNJ/ZWJ/bidi marks
                in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
                in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
                in '\u064B'..'\u065F', '\u0670' -> Unit // harakat
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun normalize(raw: String?, stripLeadingArticle: Boolean = true): String {
        if (raw.isNullOrBlank()) return ""
        var s = foldArabicScript(raw).lowercase()
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replace(combiningMarks, "")
        s = s.replace("&", " and ")
        s = s.replace(nonWord, " ").trim().replace(Regex("\\s+"), " ")
        return if (stripLeadingArticle) s.replace(leadingArticle, "") else s
    }

    fun stripYear(raw: String): String {
        val stripped = raw.replace(trailingYear, "").trim()
        return stripped.ifEmpty { raw.trim() }
    }

    /**
     * Similarity in [0,1]: 1 for exact normalized equality, otherwise Dice coefficient over
     * tokens, with a prefix bonus (e.g. "Dune" vs "Dune Part One" ⇒ ≥ 0.82 like the reference).
     */
    fun similarity(a: String?, b: String?): Double {
        val na = normalize(a); val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na == nb) return 1.0
        val ta = na.split(' '); val tb = nb.split(' ').toSet()
        val inter = ta.count { it in tb }
        var d = 2.0 * inter / (ta.size + tb.size)
        if (na.startsWith("$nb ") || nb.startsWith("$na ")) d = maxOf(d, 0.82)
        return d
    }
}

/** A candidate from a metadata provider (e.g. one TMDB search result). */
data class MatchCandidate(
    val providerId: Int,
    val type: ContentType,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val imdbId: String? = null,
    val originalLanguage: String? = null,
)

data class MatchQuery(
    val titles: List<String>,
    val year: Int?,
    val type: ContentType,
    val imdbId: String? = null,
)

sealed interface MatchResult {
    val algorithmVersion: Int
    data class Matched(val candidate: MatchCandidate, val confidence: Double, val byExternalId: Boolean,
                       override val algorithmVersion: Int) : MatchResult
    data class Unmatched(val reason: Reason, override val algorithmVersion: Int) : MatchResult
    enum class Reason { NO_CANDIDATES, LOW_CONFIDENCE, AMBIGUOUS }
}

/**
 * Deterministic scored matcher. Low-confidence or ambiguous results stay unmatched so that wrong
 * cast/reviews are never shown.
 */
object ContentMatcher {
    /** Bump when scoring changes; cached mappings with a different version are invalidated. */
    const val ALGORITHM_VERSION = 2
    const val MIN_TITLE_SIMILARITY = 0.8
    const val ACCEPT_THRESHOLD = 0.95
    private const val AMBIGUITY_MARGIN = 0.05

    private fun typeCompatible(query: ContentType, cand: ContentType): Boolean = when (query) {
        ContentType.MOVIE -> cand == ContentType.MOVIE
        // Anime may be either a film or a series in metadata providers; series must be series/anime.
        ContentType.SERIES -> cand == ContentType.SERIES || cand == ContentType.ANIME
        ContentType.ANIME -> true
    }

    fun score(query: MatchQuery, cand: MatchCandidate): Double? {
        if (!typeCompatible(query.type, cand.type)) return null
        val titles = query.titles.flatMap { listOf(it, TitleNormalizer.stripYear(it)) }.distinct()
        val sim = titles.maxOfOrNull { t ->
            maxOf(TitleNormalizer.similarity(t, cand.title),
                cand.originalTitle?.let { TitleNormalizer.similarity(t, it) } ?: 0.0)
        } ?: 0.0
        if (sim < MIN_TITLE_SIMILARITY) return null
        var s = sim
        val qy = query.year; val cy = cand.year
        if (qy != null && cy != null) {
            val d = kotlin.math.abs(qy - cy)
            val isSeries = query.type != ContentType.MOVIE
            s += when {
                d == 0 -> 0.25
                d == 1 -> 0.10
                // Series release years drift between providers (first air vs. season year).
                isSeries && d <= 3 -> -0.20
                else -> return null // remakes / same-title films in different years
            }
        } else {
            s -= 0.10 // unknown year: require a near-exact title
        }
        return s
    }

    fun match(query: MatchQuery, candidates: List<MatchCandidate>): MatchResult {
        val qImdb = query.imdbId?.trim()?.lowercase()?.takeIf { IMDB_ID.matches(it) }
        if (qImdb != null) {
            candidates.firstOrNull { it.imdbId?.lowercase() == qImdb && typeCompatible(query.type, it.type) }
                ?.let { return MatchResult.Matched(it, 1.0, true, ALGORITHM_VERSION) }
        }
        if (candidates.isEmpty()) return MatchResult.Unmatched(MatchResult.Reason.NO_CANDIDATES, ALGORITHM_VERSION)
        val scored = candidates.mapNotNull { c -> score(query, c)?.let { c to it } }
            .sortedWith(compareByDescending<Pair<MatchCandidate, Double>> { it.second }.thenBy { it.first.providerId })
        val best = scored.firstOrNull()
        if (best == null) return MatchResult.Unmatched(MatchResult.Reason.LOW_CONFIDENCE, ALGORITHM_VERSION)
        if (best.second < ACCEPT_THRESHOLD) return MatchResult.Unmatched(MatchResult.Reason.LOW_CONFIDENCE, ALGORITHM_VERSION)
        val runnerUp = scored.getOrNull(1)
        if (runnerUp != null && best.second - runnerUp.second < AMBIGUITY_MARGIN) {
            // Article folding helps "Matrix" find "The Matrix", but must not make
            // "Sacrifice" indistinguishable from unrelated "The Sacrifice" films.
            // Only disambiguate already-high-confidence contenders using a unique
            // literal title; identical literal names still remain ambiguous.
            val literalQueries = query.titles.map {
                TitleNormalizer.normalize(TitleNormalizer.stripYear(it), stripLeadingArticle = false)
            }.filter { it.isNotBlank() }.toSet()
            val literalMatches = scored.takeWhile { best.second - it.second < AMBIGUITY_MARGIN }
                .filter { (candidate, candidateScore) -> candidateScore >= ACCEPT_THRESHOLD && listOfNotNull(candidate.title, candidate.originalTitle).any {
                    TitleNormalizer.normalize(TitleNormalizer.stripYear(it), stripLeadingArticle = false) in literalQueries
                } }.map { it.first }.distinctBy { it.providerId }.singleOrNull()
            if (literalMatches != null) {
                return MatchResult.Matched(literalMatches, (score(query, literalMatches) ?: 0.95).coerceAtMost(1.0), false, ALGORITHM_VERSION)
            }
            return MatchResult.Unmatched(MatchResult.Reason.AMBIGUOUS, ALGORITHM_VERSION)
        }
        return MatchResult.Matched(best.first, best.second.coerceAtMost(1.0), false, ALGORITHM_VERSION)
    }

    private val IMDB_ID = Regex("tt\\d{6,10}")
}
