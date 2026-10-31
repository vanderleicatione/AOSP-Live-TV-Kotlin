package com.android.tv.data

import java.text.Normalizer
import java.util.Locale

/**
 * Namensabgleich TIF-Kanal ↔ Feed, wie chave() im epg-agregador (src/nome.ts): nur Formatierung wird ignoriert,
 * keine Ähnlichkeit. Lieber ohne Guide als mit dem falschen ("ESPN 2" ≠ "ESPN 3").
 */
object FeedNames {
    private val MARKS = Regex("\\p{Mn}+")
    private val CITY = Regex("^[^/]+/[a-z]{2}\\s+") // "São Paulo/SP  Globo" (epgshare BR1)
    private val NOISE = Regex("_local|\\(.*?\\)|ᴴᴰ|³")
    private val QUALITY = Regex("\\b(fhd|hd|sd|4k|uhd|h ?264|hd264|720p|1080p)\\b")
    private val OTHER = Regex("[^a-z0-9+]+")

    fun key(name: String): String {
        var n = MARKS.replace(Normalizer.normalize(name, Normalizer.Form.NFD), "").lowercase(Locale.ROOT)
        n = CITY.replace(n, "")
        n = QUALITY.replace(NOISE.replace(n, " ").replace("&", " e "), " ")
        return OTHER.replace(n, " ").trim()
    }

    /** Antennen-Namen beginnen im TIF mit einem Steuerzeichen, das die TV (und der Feed) als "." zeigt. */
    fun cleanName(name: String) = name.trim { it.isWhitespace() || it.isISOControl() || it == '.' }

    /** Schlüssel, unter denen ein Feed-Kanal von [source] mit diesen Anzeigenamen (Name + Aliasse) gefunden wird. */
    fun keys(source: String, names: List<String>): List<String> =
        names.take(1).map { "$source|${cleanName(it)}" } + names.map(::key).filter { it.isNotEmpty() }.map { "$source|k:$it" }

    /** Je Quelle in Reihenfolge: zuerst exakter Name ("RBI HD" ≠ "RBI SD"), dann der Schlüssel aus Name und Aliassen. */
    fun <T> find(map: Map<String, T>, sources: List<String>, name: String): T? {
        val clean = cleanName(name)
        val k = key(name)
        return sources.firstNotNullOfOrNull { map["$it|$clean"] ?: map["$it|k:$k"] }
    }
}
