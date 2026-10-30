package com.android.tv.guide

import android.util.ArraySet
import androidx.annotation.MainThread
import com.android.tv.data.ChannelDataManager
import com.android.tv.data.GenreItems
import com.android.tv.data.ProgramDataManager
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import com.android.tv.util.TvInputManagerHelper
import com.android.tv.util.Utils
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Datenmodell der Programmübersicht: Kanäle (mit Genre-Filter), Zeitfenster und die Einträge je
 * Kanal (Sendungen, Lücken, gesperrte Kanäle).
 */
@MainThread
class ProgramManager(
    private val tvInputManagerHelper: TvInputManagerHelper,
    private val channelDataManager: ChannelDataManager,
    private val programDataManager: ProgramDataManager,
) {
    interface Listener {
        fun onGenresUpdated()
        fun onChannelsUpdated()
        fun onTimeRangeUpdated()
    }

    fun interface TableEntriesUpdatedListener {
        /** [channelId] null: alle Kanäle. */
        fun onTableEntriesUpdated(channelId: Long?)
    }

    fun interface TableEntryChangedListener {
        fun onTableEntryChanged(entry: TableEntry)
    }

    open class ListenerAdapter : Listener {
        override fun onGenresUpdated() {}
        override fun onChannelsUpdated() {}
        override fun onTimeRangeUpdated() {}
    }

    var startTime = 0L
        private set
    private var endUtcMillis = 0L
    var fromUtcMillis = 0L
        private set
    var toUtcMillis = 0L
        private set
    private var channels: List<Channel> = ArrayList()
    private val channelIdEntriesMap = HashMap<Long, MutableList<TableEntry>>()
    private val genreChannelList = ArrayList<List<Channel>>()
    val filteredGenreIds = ArrayList<Int>()
    var selectedGenreId = GenreItems.ID_ALL_CHANNELS
        private set
    private var filteredChannels: List<Channel> = channels
    private var channelDataLoaded = false
    private val listeners = ArraySet<Listener>()
    private val tableEntriesUpdatedListeners = ArraySet<TableEntriesUpdatedListener>()
    private val tableEntryChangedListeners = ArraySet<TableEntryChangedListener>()

    private val channelDataManagerListener = object : ChannelDataManager.Listener {
        override fun onLoadFinished() {
            channelDataLoaded = true
            updateChannels(false)
        }

        override fun onChannelListUpdated() = updateChannels(false)
        override fun onChannelBrowsableChanged() = updateChannels(false)
    }

    private val programDataManagerCallback = object : ProgramDataManager.Callback {
        override fun onProgramUpdated() = updateTableEntries(true)
        override fun onChannelUpdated(channelId: Long) {
            // Nur den nachgeladenen Kanal neu bauen: alle 181 Kanäle neu zu bauen und alle Zeilen neu zu binden
            // ruckelte bei jedem neuen Kanal, der beim Scrollen ins Bild kam
            if (updateChannelEntries(channelId)) return
            updateTableEntriesWithoutNotification(false)
            notifyTableEntriesUpdated()
        }
    }

    /** Beim Öffnen/Schließen der Programmübersicht an-/abmelden (und Prefetch pausieren). */
    internal fun programGuideVisibilityChanged(visible: Boolean) {
        programDataManager.setPauseProgramUpdate(visible)
        if (visible) {
            channelDataManager.addListener(channelDataManagerListener)
            programDataManager.addCallback(programDataManagerCallback)
        } else {
            channelDataManager.removeListener(channelDataManagerListener)
            programDataManager.removeCallback(programDataManagerCallback)
        }
    }

    internal fun addListener(l: Listener) { listeners.add(l) }
    internal fun addTableEntriesUpdatedListener(l: TableEntriesUpdatedListener) { tableEntriesUpdatedListeners.add(l) }
    internal fun addTableEntryChangedListener(l: TableEntryChangedListener) { tableEntryChangedListeners.add(l) }
    internal fun removeListener(l: Listener) { listeners.remove(l) }
    internal fun removeTableEntriesUpdatedListener(l: TableEntriesUpdatedListener) { tableEntriesUpdatedListeners.remove(l) }
    internal fun removeTableEntryChangedListener(l: TableEntryChangedListener) { tableEntryChangedListeners.remove(l) }

    /** Kanalliste nach Genre filtern. */
    internal fun resetChannelListWithGenre(genreId: Int) {
        if (genreId == selectedGenreId) return
        filteredChannels = genreChannelList.getOrNull(genreId) ?: throw IllegalStateException("Genre filter isn't ready.")
        selectedGenreId = genreId
        notifyChannelsUpdated()
    }

    /** Anfangs-Zeitfenster setzen und alles neu aufbauen. */
    internal fun updateInitialTimeRange(startUtcMillis: Long, endUtcMillis: Long) {
        startTime = startUtcMillis
        if (endUtcMillis > this.endUtcMillis) this.endUtcMillis = endUtcMillis
        programDataManager.setPrefetchTimeRange(startTime)
        updateChannels(true)
        setTimeRange(startUtcMillis, endUtcMillis)
    }

    /** Zeitfenster verschieben, begrenzt auf [startTime, Ende]. */
    internal fun shiftTime(timeMillisToScroll: Long) {
        var from = fromUtcMillis + timeMillisToScroll
        var to = toUtcMillis + timeMillisToScroll
        if (from < startTime) {
            to += startTime - from
            from = startTime
        }
        if (to > endUtcMillis) {
            from -= to - endUtcMillis
            to = endUtcMillis
        }
        setTimeRange(from, to)
    }

    internal val shiftedTime: Long get() = fromUtcMillis - startTime

    internal fun getProgramIdIndex(channelId: Long, entryId: Long): Int =
        channelIdEntriesMap[channelId]?.indexOfFirst { it.id == entryId } ?: -1

    internal fun getProgramIndexAtTime(channelId: Long, time: Long): Int =
        channelIdEntriesMap[channelId]?.indexOfFirst { it.entryStartUtcMillis <= time && time < it.entryEndUtcMillis } ?: -1

    internal val channelCount: Int get() = filteredChannels.size

    internal fun getChannel(channelIndex: Int): Channel? = filteredChannels.getOrNull(channelIndex)
    internal fun getChannelIndex(channel: Channel?): Int = filteredChannels.indexOf(channel)
    internal fun getChannelIndex(channelId: Long): Int = getChannelIndex(channelDataManager.getChannel(channelId))

    /** Bugfix: unbekannter Kanal lieferte im Original einen NPE. */
    internal fun getTableEntryCount(channelId: Long): Int = channelIdEntriesMap[channelId]?.size ?: 0

    internal fun getTableEntry(channelId: Long, index: Int): TableEntry {
        programDataManager.prefetchChannel(channelId, index)
        return channelIdEntriesMap[channelId]!![index]
    }

    private fun updateChannels(clearPreviousTableEntries: Boolean) {
        channels = channelDataManager.getBrowsableChannelList()
        selectedGenreId = GenreItems.ID_ALL_CHANNELS
        filteredChannels = channels
        updateTableEntriesWithoutNotification(clearPreviousTableEntries)
        // Reihenfolge wichtig: erst Kanäle, dann Einträge melden
        notifyChannelsUpdated()
        notifyTableEntriesUpdated()
        buildGenreFilters()
    }

    internal fun setChannels(channels: List<Channel>) {
        this.channels = ArrayList(channels)
        selectedGenreId = GenreItems.ID_ALL_CHANNELS
        filteredChannels = this.channels
        buildGenreFilters()
    }

    private fun updateTableEntries(clear: Boolean) {
        updateTableEntriesWithoutNotification(clear)
        notifyTableEntriesUpdated()
        buildGenreFilters()
    }

    /** Einträge je Kanal neu bauen und bis zum gemeinsamen Ende mit Lücken auffüllen. */
    private fun updateTableEntriesWithoutNotification(clear: Boolean) {
        if (clear) channelIdEntriesMap.clear()
        val parentalControlsEnabled = tvInputManagerHelper.isParentalControlsEnabled()
        for (channel in channels) {
            val entries = createProgramEntries(channel.id, parentalControlsEnabled)
            channelIdEntriesMap[channel.id] = entries
            val last = entries.lastOrNull() ?: continue
            if (endUtcMillis < last.entryEndUtcMillis && last.entryEndUtcMillis != Long.MAX_VALUE) {
                endUtcMillis = last.entryEndUtcMillis
            }
        }
        if (endUtcMillis <= startTime) return
        for (channel in channels) fillToEnd(channel.id, channelIdEntriesMap[channel.id] ?: continue)
    }

    /** Nur ein Kanal; false, wenn er das gemeinsame Ende verschiebt (dann müssen alle Kanäle neu). */
    private fun updateChannelEntries(channelId: Long): Boolean {
        if (channels.none { it.id == channelId }) return true
        val entries = createProgramEntries(channelId, tvInputManagerHelper.isParentalControlsEnabled())
        val last = entries.lastOrNull()
        if (last != null && last.entryEndUtcMillis != Long.MAX_VALUE && last.entryEndUtcMillis > endUtcMillis) return false
        if (endUtcMillis > startTime) fillToEnd(channelId, entries)
        channelIdEntriesMap[channelId] = entries
        tableEntriesUpdatedListeners.toList().forEach { it.onTableEntriesUpdated(channelId) }
        return true
    }

    private fun fillToEnd(channelId: Long, entries: MutableList<TableEntry>) {
        if (entries.isEmpty()) {
            entries.add(TableEntry(channelId, null, startTime, endUtcMillis, false))
            return
        }
        val last = entries.last()
        if (endUtcMillis > last.entryEndUtcMillis) {
            entries.add(TableEntry(channelId, null, last.entryEndUtcMillis, endUtcMillis, false))
        } else if (last.entryEndUtcMillis == Long.MAX_VALUE) {
            // Gesperrter Kanal: bis zum Ende strecken
            entries.removeAt(entries.size - 1)
            entries.add(TableEntry(last.channelId, last.program, last.entryStartUtcMillis, endUtcMillis, last.isBlocked))
        }
    }

    private fun buildGenreFilters() {
        val lists = ArrayList<MutableList<Channel>>()
        repeat(GenreItems.getGenreCount()) { lists.add(ArrayList()) }
        for (channel in channels) {
            programDataManager.getCurrentProgram(channel.id)?.canonicalGenres?.forEach { genre ->
                lists[GenreItems.getId(genre)].add(channel)
            }
        }
        genreChannelList.clear()
        genreChannelList.addAll(lists)
        genreChannelList[GenreItems.ID_ALL_CHANNELS] = channels
        filteredGenreIds.clear()
        filteredGenreIds.add(0)
        for (i in 1 until GenreItems.getGenreCount()) if (genreChannelList[i].isNotEmpty()) filteredGenreIds.add(i)
        selectedGenreId = GenreItems.ID_ALL_CHANNELS
        filteredChannels = channels
        notifyGenresUpdated()
    }


    private fun setTimeRange(fromUtcMillis: Long, toUtcMillis: Long) {
        if (this.fromUtcMillis != fromUtcMillis || this.toUtcMillis != toUtcMillis) {
            this.fromUtcMillis = fromUtcMillis
            this.toUtcMillis = toUtcMillis
            notifyTimeRangeUpdated()
        }
    }

    /** Sendungen ab Startzeit, Lücken als Einträge ohne Sendung; zu kurzer erster Eintrag wird verschmolzen. */
    private fun createProgramEntries(channelId: Long, parentalControlsEnabled: Boolean): MutableList<TableEntry> {
        val entries = ArrayList<TableEntry>()
        // Bugfix: Kanal kann inzwischen fehlen (Original: NPE)
        val channelLocked = parentalControlsEnabled && channelDataManager.getChannel(channelId)?.isLocked == true
        if (channelLocked) {
            entries.add(TableEntry(channelId, null, startTime, Long.MAX_VALUE, true))
        } else {
            var lastProgramEndTime = startTime
            for (program in programDataManager.getPrograms(channelId, startTime)) {
                if (program.channelId == INVALID_ID) continue // Platzhalter
                val programStartTime = max(program.startTimeUtcMillis, startTime)
                val programEndTime = program.endTimeUtcMillis
                if (programStartTime > lastProgramEndTime) {
                    entries.add(TableEntry(channelId, null, lastProgramEndTime, programStartTime, false))
                    lastProgramEndTime = programStartTime
                }
                if (programEndTime > lastProgramEndTime) {
                    entries.add(TableEntry(channelId, program, lastProgramEndTime, programEndTime, false))
                    lastProgramEndTime = programEndTime
                }
            }
        }
        if (entries.size > 1) {
            val second = entries[1]
            if (second.entryStartUtcMillis < startTime + FIRST_ENTRY_MIN_DURATION) {
                entries.removeAt(0)
                entries[0] = TableEntry(second.channelId, second.program, startTime,
                    second.entryEndUtcMillis, second.isBlocked)
            }
        }
        return entries
    }

    private fun notifyGenresUpdated() = listeners.toList().forEach { it.onGenresUpdated() }
    private fun notifyChannelsUpdated() = listeners.toList().forEach { it.onChannelsUpdated() }
    private fun notifyTimeRangeUpdated() = listeners.toList().forEach { it.onTimeRangeUpdated() }
    private fun notifyTableEntriesUpdated() = tableEntriesUpdatedListeners.toList().forEach { it.onTableEntriesUpdated(null) }
    private fun notifyTableEntryUpdated(entry: TableEntry) = tableEntryChangedListeners.toList().forEach { it.onTableEntryChanged(entry) }

    /** Eintrag im Raster: Sendung oder Lücke, ggf. gesperrt. */
    class TableEntry internal constructor(
        @JvmField val channelId: Long,
        @JvmField val program: Program?,
        @JvmField val entryStartUtcMillis: Long,
        @JvmField val entryEndUtcMillis: Long,
        val isBlocked: Boolean,
    ) {
        /** Lücken bekommen eine negative ID aus der Endzeit. */
        val id: Long get() = program?.id ?: -entryEndUtcMillis
        val isGap: Boolean get() = !Program.isProgramValid(program)
        val isCurrentProgram: Boolean
            get() {
                val now = System.currentTimeMillis()
                return entryStartUtcMillis <= now && entryEndUtcMillis > now
            }

        fun hasGenre(genreId: Int): Boolean = !isGap && program!!.hasGenre(genreId)
        val width: Int get() = GuideUtils.convertMillisToPixel(entryStartUtcMillis, entryEndUtcMillis)

        override fun toString() = "TableEntry{hashCode=${hashCode()}, channelId=$channelId, program=$program, " +
            "startTime=${Utils.toTimeString(entryStartUtcMillis)}, endTimeTime=${Utils.toTimeString(entryEndUtcMillis)}}"
    }

    companion object {
        private const val TAG = "ProgramManager"
        internal val FIRST_ENTRY_MIN_DURATION = TimeUnit.MINUTES.toMillis(1)
        private const val INVALID_ID = -1L
    }
}
