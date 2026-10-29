package com.android.tv.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Xml
import androidx.annotation.WorkerThread
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import org.xmlpull.v1.XmlPullParser

/**
 * Ersatz-Guide und -Logos aus dem eigenen Feed (epg-agregador, XMLTV gzip) für Kanäle, zu denen das TIF
 * nichts liefert: Antenne (keine EPG im TIF) und Logos aller Kanäle (ohne ACCESS_ALL_EPG_DATA liefert der
 * TvProvider fremde Logos nicht). Zuordnung über den Anzeigenamen (trim) je Quelle, nicht über die Nummer.
 * Daten liegen in einer eigenen SQLite-DB, Logos als PNG im App-Speicher.
 */
object FeedGuide {
    private const val TAG = "FeedGuide"
    private const val FEED_URL = "https://vanderleicatione.github.io/epg-agregador/guia.xml.gz"
    private val REFRESH_MS = TimeUnit.HOURS.toMillis(12)
    private val LOGO_MAX_AGE_MS = TimeUnit.DAYS.toMillis(7)
    private const val PREFS = "feed_guide"
    private const val KEY_LAST_SYNC = "last_sync"
    private const val WORK_NAME = "feed_guide"
    private const val INPUT_ANTENNA = "com.tcl.tvinput/.TDTVInputService"
    private const val INPUT_BROADCAST = "com.tcl.channelplus/"

    private class FeedChannel(val id: String, val logoUri: String?)

    private lateinit var appContext: Context
    private val db by lazy { Db(appContext) }
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val syncing = AtomicBoolean(false)
    private val listeners = LinkedHashSet<Runnable>()

    /** "quelle|name" → Feed-Kanal. Wird im Hintergrund ersetzt, gelesen vom Main-Thread. */
    @Volatile private var channels: Map<String, FeedChannel> = emptyMap()

    /** Beim App-Start: gespeicherte Kopie laden, 12-h-Job planen, bei alter Kopie sofort laden. */
    fun init(context: Context) {
        appContext = context.applicationContext
        executor.execute { loadChannels() }
        WorkManager.getInstance(appContext).enqueueUniquePeriodicWork(
            WORK_NAME, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequest.Builder(SyncWorker::class.java, 12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
        refreshIfStale()
    }

    /** Beim Öffnen der App: Kopie älter als 12 h → im Hintergrund neu laden. */
    fun refreshIfStale() {
        val last = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_LAST_SYNC, 0)
        if (System.currentTimeMillis() - last > REFRESH_MS) executor.execute { sync() }
    }

    /** Main-Thread; wird nach jedem Laden/Aktualisieren des Feeds aufgerufen. */
    fun addListener(listener: Runnable) { listeners.add(listener) }

    private fun key(channel: Channel): String? {
        val source = when {
            channel.inputId.startsWith(INPUT_ANTENNA) -> "antena"
            channel.inputId.startsWith(INPUT_BROADCAST) -> "channelplus"
            else -> return null
        }
        return source + "|" + channel.displayName?.trim()
    }

    fun feedId(channel: Channel): String? = key(channel)?.let { channels[it]?.id }

    /** Logo aus dem Feed: lokale Datei, solange sie noch nicht geladen ist die URL. */
    fun logoUri(channel: Channel): String? = key(channel)?.let { channels[it]?.logoUri }

    // ---- Programme (DB-Thread) ----

    /** Laufende Programme zum Zeitpunkt [time] für die Kanäle in [feedIds] (Kanal-ID → Feed-ID). */
    @WorkerThread
    fun programsAt(time: Long, feedIds: Map<Long, String>): List<Program> =
        programsBetween(time, time + 1, feedIds).values.mapNotNull { it.firstOrNull() }

    /** Programme, die [start, end) berühren, je Kanal nach Startzeit sortiert. */
    @WorkerThread
    fun programsBetween(start: Long, end: Long, feedIds: Map<Long, String>): Map<Long, List<Program>> {
        if (feedIds.isEmpty() || channels.isEmpty()) return emptyMap()
        val byFeed = HashMap<String, MutableList<Long>>()
        for ((channelId, feedId) in feedIds) byFeed.getOrPut(feedId) { ArrayList() }.add(channelId)
        val result = HashMap<Long, ArrayList<Program>>()
        try {
            db.readableDatabase.rawQuery(
                "SELECT rowid, channel, start, stop, title, desc FROM programme WHERE stop > ? AND start < ? " +
                    "ORDER BY channel, start",
                arrayOf(start.toString(), end.toString()),
            ).use { c ->
                while (c.moveToNext()) {
                    val channelIds = byFeed[c.getString(1)] ?: continue
                    for (channelId in channelIds) {
                        result.getOrPut(channelId) { ArrayList() }.add(
                            ProgramImpl.Builder()
                                .setId(-c.getLong(0)) // negativ: kollidiert nicht mit TIF-IDs
                                .setChannelId(channelId)
                                .setStartTimeUtcMillis(c.getLong(2))
                                .setEndTimeUtcMillis(c.getLong(3))
                                .setTitle(c.getString(4))
                                .setDescription(c.getString(5))
                                .build(),
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Feed query failed", e)
        }
        return result
    }

    /** TIF zuerst: Feed-Programme nur in Zeiten, in denen das TIF nichts hat. Beide nach Start sortiert. */
    fun merge(tif: List<Program>, feed: List<Program>): ArrayList<Program> {
        val out = ArrayList(tif)
        if (feed.isEmpty()) return out
        var i = 0
        for (p in feed) {
            while (i < tif.size && tif[i].endTimeUtcMillis <= p.startTimeUtcMillis) i++
            if (i < tif.size && tif[i].startTimeUtcMillis < p.endTimeUtcMillis) continue
            out.add(p)
        }
        if (out.size > tif.size) out.sortBy { it.startTimeUtcMillis }
        return out
    }

    // ---- Laden ----

    @WorkerThread
    private fun loadChannels() {
        val logoDir = File(appContext.filesDir, "feed-logos")
        val map = HashMap<String, FeedChannel>()
        try {
            db.readableDatabase.rawQuery("SELECT id, name, logo FROM channel", null).use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val file = File(logoDir, "$id.png")
                    val logo = if (file.exists()) Uri.fromFile(file).toString() else c.getString(2)
                    map[id.substringBefore('.') + "|" + c.getString(1)] = FeedChannel(id, logo)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Feed channels not loaded", e)
        }
        channels = map
        Log.i(TAG, "Feed channels loaded: ${map.size}")
        mainHandler.post { listeners.toList().forEach { it.run() } }
    }

    /** Lädt Feed und fehlende Logos. Ohne Netz bleibt die letzte Kopie. */
    @WorkerThread
    fun sync(): Boolean {
        if (!syncing.compareAndSet(false, true)) return true
        try {
            val conn = URL(FEED_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            val logos = HashMap<String, String>()
            try {
                GZIPInputStream(conn.inputStream.buffered()).use { store(it, logos) }
            } finally {
                conn.disconnect()
            }
            downloadLogos(logos)
            appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_LAST_SYNC, System.currentTimeMillis()).apply()
            Log.i(TAG, "Feed synced: ${logos.size} channels")
            loadChannels()
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Feed sync failed, keeping last copy", e)
            return false
        } finally {
            syncing.set(false)
        }
    }

    /** Liest das XMLTV per Stream und ersetzt die DB in einer Transaktion (Fehler → alte Kopie bleibt). */
    private fun store(input: java.io.InputStream, logos: MutableMap<String, String>) {
        val time = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val p = Xml.newPullParser()
        p.setInput(input, "UTF-8")
        val w = db.writableDatabase
        w.beginTransaction()
        try {
            w.delete("channel", null, null)
            w.delete("programme", null, null)
            val row = ContentValues()
            var inChannel = false
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (p.name) {
                        "channel" -> {
                            inChannel = true
                            row.clear()
                            row.put("id", p.getAttributeValue(null, "id"))
                        }
                        "display-name" -> if (inChannel && !row.containsKey("name")) row.put("name", p.nextText().trim())
                        "icon" -> if (inChannel) row.put("logo", p.getAttributeValue(null, "src"))
                        "programme" -> {
                            row.clear()
                            row.put("channel", p.getAttributeValue(null, "channel"))
                            row.put("start", time.parse(p.getAttributeValue(null, "start"))!!.time)
                            row.put("stop", time.parse(p.getAttributeValue(null, "stop"))!!.time)
                        }
                        "title" -> if (!row.containsKey("title")) row.put("title", p.nextText())
                        "desc" -> if (!row.containsKey("desc")) row.put("desc", p.nextText())
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    when (p.name) {
                        "channel" -> {
                            inChannel = false
                            w.insert("channel", null, row)
                            row.getAsString("logo")?.let { logos[row.getAsString("id")] = it }
                        }
                        "programme" -> w.insert("programme", null, row)
                    }
                }
                event = p.next()
            }
            w.setTransactionSuccessful()
        } finally {
            w.endTransaction()
        }
    }

    /** Plattencache der Logos: fehlende oder über 7 Tage alte neu laden. */
    private fun downloadLogos(logos: Map<String, String>) {
        val dir = File(appContext.filesDir, "feed-logos").apply { mkdirs() }
        val now = System.currentTimeMillis()
        for ((id, url) in logos) {
            val file = File(dir, "$id.png")
            if (file.exists() && now - file.lastModified() < LOGO_MAX_AGE_MS) continue
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                val tmp = File(dir, "$id.tmp")
                try {
                    conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                } finally {
                    conn.disconnect()
                }
                tmp.renameTo(file)
            } catch (e: Exception) {
                Log.w(TAG, "Logo $id not downloaded", e)
            }
        }
    }

    private class Db(context: Context) : SQLiteOpenHelper(context, "feed_guide.db", null, 1) {
        // WAL: Lesen (Guide) läuft weiter, während eine Aktualisierung schreibt.
        init { setWriteAheadLoggingEnabled(true) }

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE channel (id TEXT PRIMARY KEY, name TEXT, logo TEXT)")
            db.execSQL("CREATE TABLE programme (channel TEXT, start INTEGER, stop INTEGER, title TEXT, desc TEXT)")
            db.execSQL("CREATE INDEX programme_time ON programme (stop, start)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    /** Aktualisierung alle 12 h über WorkManager. */
    class SyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result = if (sync()) Result.success() else Result.retry()
    }
}
