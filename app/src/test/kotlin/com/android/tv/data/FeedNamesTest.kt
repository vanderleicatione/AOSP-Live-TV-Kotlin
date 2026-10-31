package com.android.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedNamesTest {
    private fun map(vararg channels: Pair<String, List<String>>): Map<String, String> {
        val m = HashMap<String, String>()
        for ((id, names) in channels) for (k in FeedNames.keys(id.substringBefore('.'), names)) m.putIfAbsent(k, id)
        return m
    }

    private val feed = map(
        "antena.rbi-hd" to listOf("RBI HD"),
        "antena.rbi-sd" to listOf("RBI SD"),
        "channelplus.espn-2" to listOf("ESPN 2"),
        "opentv.adult-swim" to listOf("Adult Swim", "Adult Swin"),
        "opentv.a-e" to listOf("A&E"),
        "opentv.espn-3" to listOf("ESPN 3"),
    )
    private val internet = listOf("opentv", "channelplus", "antena")

    @Test fun keyIgnoresOnlyFormatting() {
        assertEquals("globo", FeedNames.key(".Globo HD"))
        assertEquals("globo", FeedNames.key("São Paulo/SP  Globo"))
        assertEquals("a e e", FeedNames.key("A&E")) // como o chave() do agregador
        assertEquals("pokemon", FeedNames.key("Pokémon FHD"))
        assertEquals("bloomberg tv+", FeedNames.key("Bloomberg TV+"))
    }

    @Test fun exactNameBeatsKey() {
        assertEquals("antena.rbi-sd", FeedNames.find(feed, listOf("antena"), "\u0001RBI SD"))
        assertEquals("antena.rbi-hd", FeedNames.find(feed, listOf("antena"), ".RBI HD"))
    }

    @Test fun matchesByNormalizedNameAndAlias() {
        assertEquals("opentv.a-e", FeedNames.find(feed, internet, "A & E"))
        assertEquals("opentv.adult-swim", FeedNames.find(feed, internet, "ADULT SWIN HD"))
    }

    @Test fun neverApproximate() {
        assertEquals("opentv.espn-3", FeedNames.find(feed, internet, "ESPN 3"))
        assertNull(FeedNames.find(feed, internet, "ESPN 4"))
        assertNull(FeedNames.find(feed, internet, "Adult Swimm"))
    }

    @Test fun sourceOrder() {
        // Antenne sucht nur in der eigenen Liste; Canais+ zuerst in der Masterliste, dann Channel+
        assertNull(FeedNames.find(feed, listOf("antena"), "ESPN 2"))
        assertEquals("channelplus.espn-2", FeedNames.find(feed, internet, "ESPN 2"))
    }
}
