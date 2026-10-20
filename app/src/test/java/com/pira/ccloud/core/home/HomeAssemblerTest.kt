package com.pira.ccloud.core.home

import com.pira.ccloud.core.model.Availability
import com.pira.ccloud.core.model.CatalogRef
import com.pira.ccloud.core.model.CatalogTitle
import com.pira.ccloud.core.model.ContentType
import com.pira.ccloud.core.network.FailureKind
import com.pira.ccloud.core.playback.ProgressEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeAssemblerTest {
    private fun t(id: Int, backdrop: String? = "b", synopsis: String = "s",
                  avail: Availability = Availability.Playable(1, listOf("720p")), type: ContentType = ContentType.MOVIE) =
        CatalogTitle(CatalogRef(id, type), "T$id", 2020, "p", backdrop, synopsis, emptyList(), emptyList(), emptyList(), avail)

    @Test fun emptyRailsAreOmitted() {
        val c = HomeAssembler.assemble(mapOf(RailKind.NEW_MOVIES to RailResult.Ok(listOf(t(1)))), emptyList(), emptyList())
        assertEquals(listOf(RailKind.NEW_MOVIES), c.rails.map { it.kind })
    }

    @Test fun failuresAreReportedButOtherRailsShow() {
        val c = HomeAssembler.assemble(mapOf(
            RailKind.NEW_MOVIES to RailResult.Err(FailureKind.TIMEOUT),
            RailKind.NEW_SERIES to RailResult.Ok(listOf(t(2, type = ContentType.SERIES, avail = Availability.EpisodesOnDemand))),
        ), emptyList(), emptyList())
        assertEquals(FailureKind.TIMEOUT, c.failedRails[RailKind.NEW_MOVIES])
        assertEquals(listOf(RailKind.NEW_SERIES), c.rails.map { it.kind })
        assertEquals(2, c.hero!!.ref.catalogId)
    }

    @Test fun heroSkipsUnplayableAndImagelessTitles() {
        val hero = HomeAssembler.pickHero(listOf(t(1, backdrop = null), t(2, avail = Availability.NoStreams), t(3)))
        assertEquals(3, hero!!.ref.catalogId)
    }

    @Test fun heroPrefersTitlesWithSynopsis() {
        assertEquals(2, HomeAssembler.pickHero(listOf(t(1, synopsis = " "), t(2)))!!.ref.catalogId)
        assertEquals(1, HomeAssembler.pickHero(listOf(t(1, synopsis = "")))!!.ref.catalogId)
    }

    @Test fun noHeroWhenNothingQualifies() = assertNull(HomeAssembler.pickHero(listOf(t(1, backdrop = null))))

    @Test fun railsAreDedupedAndCapped() {
        val many = (1..30).map { t(it) } + t(1)
        val c = HomeAssembler.assemble(mapOf(RailKind.NEW_MOVIES to RailResult.Ok(many)), emptyList(), emptyList())
        assertEquals(HomeAssembler.MAX_RAIL_ITEMS, c.rails.single().items.size)
    }

    @Test fun continueWatchingComesFirst() {
        val p = ProgressEntry("m9", 9, null, null, 1000, 2000, 1)
        val c = HomeAssembler.assemble(mapOf(RailKind.NEW_MOVIES to RailResult.Ok(listOf(t(1)))),
            listOf(ContinueItem(t(9), p)), listOf(t(5)))
        assertEquals(listOf(RailKind.CONTINUE_WATCHING, RailKind.NEW_MOVIES, RailKind.MY_LIST), c.rails.map { it.kind })
        assertTrue(c.failedRails.isEmpty())
    }
}
