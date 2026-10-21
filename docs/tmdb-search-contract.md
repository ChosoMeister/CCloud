# TMDB Search filters

- Text search with no genre uses the existing CCloud catalog search, optionally filtered by Movies/Series.
- Movies and Series load separate official TMDB genre lists from `GET /3/genre/movie/list` and `/3/genre/tv/list`; IDs are not interchangeable.
- Browsing a type/genre uses `GET /3/discover/{movie|tv}` with `with_genres`, `include_adult=false`, `language`, `page`, and `sort_by=popularity.desc`.
- Text + genre uses `GET /3/search/{movie|tv}` with `query`; `genre_ids` in its results are filtered locally because search does not support `with_genres`.
- Selecting a cast member uses `GET /3/person/{person_id}/combined_credits`; CCloud keeps only `cast` titles for the selected Movie/Series type, and applies an optional genre and text query locally. Person IDs are part of the browse cache key.
- Results without a backdrop are retained. TMDB candidates are matched to the existing CCloud catalog by title, type and year before being offered as available. TMDB does not supply video streams.
- Genre browsing matches titles two TMDB pages at a time and loads the next batch near the end of the grid until TMDB's response `total_pages` boundary; there is no app-imposed page ceiling. TMDB itself accepts page numbers 1–500. If a batch has no CCloud catalog matches, scanning continues until it finds available titles or reaches that boundary, avoiding an empty-grid pagination dead end. Cast credits remain a single unpaged response. Catalog matching is limited to four concurrent requests. Changing query/type cancels obsolete work; obsolete results cannot replace the latest request.
- TMDB credentials and transport are reused. No token is added to repository files. Missing credentials or HTTP failure is visible instead of silently showing the old country flags.
- `TmdbBrowseHttpTest` runs the real OkHttp transport against local MockWebServer contract responses. It does not contact TMDB or use credentials.

References: https://developer.themoviedb.org/reference/genre-movie-list, https://developer.themoviedb.org/reference/genre-tv-list, https://developer.themoviedb.org/reference/discover-movie, https://developer.themoviedb.org/reference/search-movie, https://developer.themoviedb.org/reference/person-combined-credits

## Details matching

- Title matching keeps type/year/confidence safeguards. If article folding makes several candidates tie (for example, `Sacrifice` and `The Sacrifice` in 2026), a unique literal title among those candidates wins. Multiple identical literal titles remain unmatched.
- Matcher version 2 invalidates old hit/miss cache keys. Details are fetched by the resolved TMDB ID; no film-specific ID is hardcoded.
- Details matching sends movie years with TMDB `primary_release_year`, which prioritizes results without filtering out regional release-year variants; TV uses `first_air_date_year`. It retries without the year only when the year-prioritized result set is empty. Non-empty but ambiguous or year-mismatched results stay unmatched. This avoids guessing for short titles such as `Hope` and collisions such as the two Chinese `God of War` movies.
- `TmdbBrowseHttpTest.sacrificeSearchCollisionLoadsTheLiteralMoviesDetails` replays the relevant live search fields over real local HTTP and verifies `/movie/1284186`, enrichment and caching. `koreanHopeRetriesWithoutCatalogYearAndLoadsCorrectMovie` covers the Korean `Hope` (TMDB 1058424, 2026) when the catalog year is 2025; `tenzingRetriesWithoutCatalogYearAndLoadsCorrectMovie` covers Tenzing (TMDB 1288069, 2026); `localizedGodOfWarDoesNotOverrideDifferentCatalogYear` ensures the Chinese title collision is refused. Matcher tests cover stale-year recovery, remake rejection, and duplicate-title ambiguity.
