package com.pira.ccloud.core.matching

import com.pira.ccloud.core.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentMatcherTest {
    private fun cand(id: Int, title: String, year: Int?, type: ContentType = ContentType.MOVIE,
                     orig: String? = null, imdb: String? = null) =
        MatchCandidate(id, type, title, orig, year, imdb)

    @Test fun normalizesPersianArabicVariantsAndDigits() {
        // Arabic yeh/kaf vs Persian, ZWNJ, Persian digits
        assertEquals(TitleNormalizer.normalize("مي\u200Cخواهم ك ۲۰۲۰"), TitleNormalizer.normalize("می خواهم ک 2020"))
    }

    @Test fun stripsDiacriticsArticlesAndPunctuation() {
        assertEquals("amelie", TitleNormalizer.normalize("Amélie"))
        assertEquals("matrix", TitleNormalizer.normalize("The Matrix!"))
        assertEquals("fast and furious", TitleNormalizer.normalize("Fast & Furious"))
    }

    @Test fun stripYearKeepsTitleWhenOnlyYear() {
        assertEquals("Dune", TitleNormalizer.stripYear("Dune (2021)"))
        assertEquals("1917", TitleNormalizer.stripYear("1917"))
    }

    @Test fun exactTitleAndYearMatches() {
        val r = ContentMatcher.match(MatchQuery(listOf("Inception"), 2010, ContentType.MOVIE),
            listOf(cand(27205, "Inception", 2010), cand(1, "Inception: The Cobol Job", 2010)))
        assertTrue(r is MatchResult.Matched)
        assertEquals(27205, (r as MatchResult.Matched).candidate.providerId)
    }

    @Test fun remakeInDifferentYearIsRejected() {
        // Dune (2021) must not match Dune (1984)
        val r = ContentMatcher.match(MatchQuery(listOf("Dune"), 2021, ContentType.MOVIE), listOf(cand(841, "Dune", 1984)))
        assertTrue(r is MatchResult.Unmatched)
    }

    @Test fun picksCorrectYearAmongSameTitles() {
        val r = ContentMatcher.match(MatchQuery(listOf("Dune"), 2021, ContentType.MOVIE),
            listOf(cand(841, "Dune", 1984), cand(438631, "Dune", 2021)))
        assertEquals(438631, (r as MatchResult.Matched).candidate.providerId)
    }

    @Test fun movieNeverMatchesSeries() {
        val r = ContentMatcher.match(MatchQuery(listOf("Fargo"), 1996, ContentType.MOVIE),
            listOf(cand(60622, "Fargo", 1996, ContentType.SERIES)))
        assertTrue(r is MatchResult.Unmatched)
    }

    @Test fun seriesToleratesSmallYearDriftButNotLarge() {
        val q = MatchQuery(listOf("The Office"), 2005, ContentType.SERIES)
        assertTrue(ContentMatcher.match(q, listOf(cand(2316, "The Office", 2005, ContentType.SERIES))) is MatchResult.Matched)
        assertTrue(ContentMatcher.match(q, listOf(cand(2996, "The Office", 2001, ContentType.SERIES))) is MatchResult.Unmatched)
    }

    @Test fun ambiguousEqualCandidatesStayUnmatched() {
        val r = ContentMatcher.match(MatchQuery(listOf("Crash"), 2004, ContentType.MOVIE),
            listOf(cand(1, "Crash", 2004), cand(2, "Crash", 2004)))
        assertEquals(MatchResult.Reason.AMBIGUOUS, (r as MatchResult.Unmatched).reason)
    }

    @Test fun imdbIdWinsOverTitle() {
        val r = ContentMatcher.match(MatchQuery(listOf("Something else"), null, ContentType.MOVIE, "tt1375666"),
            listOf(cand(27205, "Inception", 2010, imdb = "tt1375666")))
        assertTrue((r as MatchResult.Matched).byExternalId)
    }

    @Test fun matchesOnOriginalTitleWithLocalizedQuery() {
        val r = ContentMatcher.match(MatchQuery(listOf("انگل", "Parasite"), 2019, ContentType.MOVIE),
            listOf(cand(496243, "Parasite", 2019, orig = "기생충")))
        assertTrue(r is MatchResult.Matched)
    }

    @Test fun lowSimilarityUnmatched() {
        val r = ContentMatcher.match(MatchQuery(listOf("Interstellar"), 2014, ContentType.MOVIE),
            listOf(cand(1, "Interstellar Wars", 2016)))
        assertTrue(r is MatchResult.Unmatched)
    }

    @Test fun unknownYearRequiresNearExactTitle() {
        val q = MatchQuery(listOf("Heat"), null, ContentType.MOVIE)
        assertTrue(ContentMatcher.match(q, listOf(cand(949, "Heat", 1995))) is MatchResult.Unmatched) // 0.9 < 0.95
    }

    @Test fun resultCarriesAlgorithmVersion() {
        val r = ContentMatcher.match(MatchQuery(listOf("X"), 2000, ContentType.MOVIE), emptyList())
        assertEquals(ContentMatcher.ALGORITHM_VERSION, r.algorithmVersion)
    }
}
