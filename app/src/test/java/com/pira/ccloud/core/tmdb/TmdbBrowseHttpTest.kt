package com.pira.ccloud.core.tmdb

import com.pira.ccloud.core.model.ContentType
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

/** Exercise the real HTTP client against TMDB contract examples, with no real token/network. */
class TmdbBrowseHttpTest {
    private fun repository(server: MockWebServer) = TmdbRepository(
        OkHttpTmdbTransport({ "test-key" }, baseUrl = server.url("/3").toString()), { true })

    @Test fun sacrificeSearchCollisionLoadsTheLiteralMoviesDetails() = runBlocking {
        MockWebServer().use { server ->
            // Relevant fields of the live Sacrifice search response (2026-10-21).
            server.enqueue(MockResponse().setBody("""{"results":[
                {"id":1284186,"title":"Sacrifice","original_title":"Sacrifice","release_date":"2026-04-09"},
                {"id":1706393,"title":"The Sacrifice","original_title":"Offeret","release_date":"2026-06-04"},
                {"id":1666745,"title":"The Sacrifice","release_date":"2026-04-17"}
            ]}"""))
            server.enqueue(MockResponse().setBody("""{"id":1284186,"title":"Sacrifice","overview":"TMDB overview","runtime":97}"""))
            val repo = repository(server)
            val r = repo.enrich("movie:sacrifice", listOf("Sacrifice"), 2026, ContentType.MOVIE)
            assertTrue(r is EnrichmentResult.Ready)
            assertEquals(1284186, (r as EnrichmentResult.Ready).enrichment.tmdbId)
            assertEquals("TMDB overview", r.enrichment.overview)
            assertEquals("/3/search/movie", server.takeRequest().requestUrl!!.encodedPath)
            val details = server.takeRequest().requestUrl!!
            assertEquals("/3/movie/1284186", details.encodedPath)
            assertTrue(details.queryParameter("append_to_response")!!.contains("credits"))
            repo.enrich("movie:sacrifice", listOf("Sacrifice"), 2026, ContentType.MOVIE)
            assertEquals(2, server.requestCount)
        }
    }

    @Test fun koreanHopeUsesCatalogCountryToResolveLanguageCollision() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"results":[
                {"id":1357302,"title":"Hope","original_title":"Hope","release_date":"2025-05-17"},
                {"id":1547213,"title":"HOPE","original_title":"HOPE (希望)","release_date":"2025-03-20"}
            ]}"""))
            server.enqueue(MockResponse().setBody("""{"results":[
                {"id":1058424,"title":"Hope","original_title":"호프","original_language":"ko","release_date":"2026-07-15"},
                {"id":1357302,"title":"Hope","original_title":"Hope","original_language":"en","release_date":"2025-05-17"}
            ]}"""))
            server.enqueue(MockResponse().setBody("""{"id":1058424,"title":"Hope","overview":"Korean TMDB overview","runtime":0}"""))
            val repo = repository(server)
            val result = repo.enrich("movie:hope", listOf("Hope"), 2025, ContentType.MOVIE, countries = listOf("South Korea"))
            assertTrue(result is EnrichmentResult.Ready)
            assertEquals(1058424, (result as EnrichmentResult.Ready).enrichment.tmdbId)

            val filtered = server.takeRequest().requestUrl!!
            assertEquals("2025", filtered.queryParameter("primary_release_year"))
            assertNull(filtered.queryParameter("year"))
            val localized = server.takeRequest().requestUrl!!
            assertEquals("Hope", localized.queryParameter("query"))
            assertNull(localized.queryParameter("primary_release_year"))
            val details = server.takeRequest().requestUrl!!
            assertEquals("/3/movie/1058424", details.encodedPath)
        }
    }

    @Test fun tenzingRetriesWithoutCatalogYearAndLoadsCorrectMovie() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"results":[]}"""))
            server.enqueue(MockResponse().setBody("""{"results":[
                {"id":1288069,"title":"Tenzing","original_title":"Tenzing","release_date":"2026-10-09"},
                {"id":1743260,"title":"Hillary and Tenzing - Everest and After","original_title":"Hillary and Tenzing - Everest and After","release_date":"1997-01-01"},
                {"id":245052,"title":"Hillary and Tenzing: Climbing to the Roof of World","original_title":"Hillary and Tenzing: Climbing to the Roof of World","release_date":"1997-11-10"}
            ]}"""))
            server.enqueue(MockResponse().setBody("""{"id":1288069,"title":"Tenzing","overview":"Tenzing TMDB overview","runtime":0}"""))

            val repo = repository(server)
            val result = repo.enrich("movie:tenzing", listOf("Tenzing"), 2025, ContentType.MOVIE)
            assertTrue(result is EnrichmentResult.Ready)
            val enrichment = (result as EnrichmentResult.Ready).enrichment
            assertEquals(1288069, enrichment.tmdbId)
            assertEquals("Tenzing TMDB overview", enrichment.overview)

            val filtered = server.takeRequest().requestUrl!!
            assertEquals("2025", filtered.queryParameter("primary_release_year"))
            assertNull(filtered.queryParameter("year"))
            val fallback = server.takeRequest().requestUrl!!
            assertNull(fallback.queryParameter("primary_release_year"))
            assertNull(fallback.queryParameter("year"))
            assertEquals("Tenzing", fallback.queryParameter("query"))
            val details = server.takeRequest().requestUrl!!
            assertEquals("/3/movie/1288069", details.encodedPath)
        }
    }

    @Test fun localizedGodOfWarDoesNotOverrideDifferentCatalogYear() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"results":[
                {"id":455043,"title":"God of War","original_title":"荡寇风云","release_date":"2017-05-27"},
                {"id":770449,"title":"God of War","original_title":"赵子龙","release_date":"2021-08-31"}
            ]}"""))
            server.enqueue(MockResponse().setBody("""{"results":[
                {"id":455043,"title":"God of War","original_title":"荡寇风云","release_date":"2017-05-27"},
                {"id":770449,"title":"God of War","original_title":"赵子龙","release_date":"2021-08-31"}
            ]}"""))
            val result = repository(server).enrich("movie:god-of-war", listOf("God of War"), 2024, ContentType.MOVIE)
            assertTrue(result is EnrichmentResult.NotMatched)
            val filtered = server.takeRequest().requestUrl!!
            assertEquals("2024", filtered.queryParameter("primary_release_year"))
            assertNull(filtered.queryParameter("year"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun movieAndTvGenresUseSeparateOfficialListsAndCache() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"genres":[{"id":28,"name":"Action"}]}"""))
            server.enqueue(MockResponse().setBody("""{"genres":[{"id":10759,"name":"Action & Adventure"}]}"""))
            val repo = repository(server)
            assertEquals(28, repo.genres(ContentType.MOVIE).single().id)
            assertEquals(10759, repo.genres(ContentType.SERIES).single().id)
            assertEquals(28, repo.genres(ContentType.MOVIE).single().id)
            assertEquals(2, server.requestCount)
            assertEquals("/3/genre/movie/list", server.takeRequest().requestUrl!!.encodedPath)
            assertEquals("/3/genre/tv/list", server.takeRequest().requestUrl!!.encodedPath)
        }
    }

    @Test fun genreSearchFiltersResponseWithoutUnsupportedSearchParameterOrBackdropRequirement() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"results":[
                {"id":1,"title":"Action title","genre_ids":[28],"poster_path":"/p.jpg"},
                {"id":2,"title":"Drama title","genre_ids":[18]}]}"""))
            val results = repository(server).browse(ContentType.MOVIE, 28, "title", pages = 1)
            assertEquals(listOf(1), results.map { it.tmdbId })
            assertNull(results.single().backdropUrl)
            val url = server.takeRequest().requestUrl!!
            assertEquals("/3/search/movie", url.encodedPath)
            assertEquals("title", url.queryParameter("query"))
            assertNull(url.queryParameter("with_genres"))
            assertEquals("false", url.queryParameter("include_adult"))
        }
    }

    @Test fun discoverTvUsesGenreAndDoesNotSendTextQuery() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"results":[{"id":3,"name":"A show","genre_ids":[18]}]}"""))
            assertEquals(ContentType.SERIES, repository(server).browse(ContentType.SERIES, 18, pages = 1).single().type)
            val url = server.takeRequest().requestUrl!!
            assertEquals("/3/discover/tv", url.encodedPath)
            assertEquals("18", url.queryParameter("with_genres"))
            assertNull(url.queryParameter("query"))
        }
    }

    @Test fun browseCanRequestLaterPagesIndependently() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"page":21,"total_pages":31,"results":[{"id":4,"title":"Later page","genre_ids":[16]}]}"""))
            val result = repository(server).browse(ContentType.MOVIE, genreId = 16, pages = 1, startPage = 21)
            assertEquals(listOf(4), result.map { it.tmdbId })
            val url = server.takeRequest().requestUrl!!
            assertEquals("21", url.queryParameter("page"))
        }
    }

    @Test fun browsePageReturnsTheApiReportedPaginationBoundary() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"page":21,"total_pages":31,"results":[{"id":4,"title":"Later page","genre_ids":[16]}]}"""))
            val result = repository(server).browsePage(ContentType.MOVIE, genreId = 16, page = 21)
            assertEquals(21, result.page)
            assertEquals(31, result.totalPages)
            assertEquals(listOf(4), result.titles.map { it.tmdbId })
            assertEquals("21", server.takeRequest().requestUrl!!.queryParameter("page"))
        }
    }

    @Test fun castFilterUsesCombinedCreditsAndCachesByPerson() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"cast":[
                {"id":1,"media_type":"movie","title":"Actor Movie","genre_ids":[28]},
                {"id":2,"media_type":"tv","name":"Actor Series","genre_ids":[18]}]}"""))
            val repo = repository(server)
            val result = repo.browse(ContentType.SERIES, genreId = 18, personId = 42)
            assertEquals(listOf(2), result.map { it.tmdbId })
            val url = server.takeRequest().requestUrl!!
            assertEquals("/3/person/42/combined_credits", url.encodedPath)
            assertEquals("en-US", url.queryParameter("language"))
            assertEquals(result, repo.browse(ContentType.SERIES, genreId = 18, personId = 42))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun authorizationFailureIsReportedWithoutRetryOrMisleadingEmptySuccess() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(401).setBody("{}"))
            try { repository(server).genres(ContentType.MOVIE); fail("Expected HTTP failure") }
            catch (e: TmdbHttpException) { assertEquals(401, e.code) }
            assertEquals(1, server.requestCount)
        }
    }
}
