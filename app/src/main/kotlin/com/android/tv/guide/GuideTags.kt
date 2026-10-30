package com.android.tv.guide

import android.media.tv.TvContract.Channels
import android.media.tv.TvTrackInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.android.tv.MainActivity
import com.android.tv.R
import com.android.tv.data.StreamInfo
import com.android.tv.util.Utils

/**
 * TCL-Etiketten (element_tcl_layout_tag): Auflösung, CC, Ton – nur aus echten Daten. Auflösung aus dem TIF
 * (Sendung, sonst Videoformat des Kanals); bei der laufenden Sendung des eingestellten Kanals aus dem Stream,
 * dort auch Untertitel- und Tonspur. Der Feed liefert nur Genres (<category>), keine Technik-Angaben.
 */
internal object GuideTags {

    fun of(activity: MainActivity, entry: ProgramManager.TableEntry): List<String> {
        val program = entry.program
        if (entry.isGap || program == null) return emptyList()
        val tags = ArrayList<String>(3)
        val stream = activity.tvView
        val live = entry.isCurrentProgram && entry.channelId == activity.currentChannelId
        var level = if (live) stream.videoDefinitionLevel else StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN
        if (level == StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN) level = Utils.getVideoDefinitionLevelFromSize(program.videoWidth, program.videoHeight)
        if (level == StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN) level = levelOf(activity.channelDataManager.getChannel(entry.channelId)?.videoFormat)
        LEVEL_LABELS[level]?.let { tags.add(it) }
        if (live) {
            if (stream.hasClosedCaption()) tags.add(CC)
            val audioId = stream.getSelectedTrack(TvTrackInfo.TYPE_AUDIO)
            val audio = stream.getTracks(TvTrackInfo.TYPE_AUDIO)?.firstOrNull { it.id == audioId }
            val encoding = audio?.encoding.orEmpty()
            if (DOLBY.any { encoding.contains(it, ignoreCase = true) }) {
                tags.add(DOLBY_LABEL)
            } else {
                Utils.getAudioChannelString(activity, stream.audioChannelCount).takeIf { it.isNotEmpty() }
                    ?.let { tags.add(it.replaceFirstChar(Char::titlecase)) }
            }
        }
        return tags
    }

    /** Etiketten in [container] zeigen; vorhandene TextViews werden wiederverwendet (kein Inflate je Fokus). */
    fun bind(container: ViewGroup, tags: List<String>) {
        while (container.childCount < tags.size) {
            LayoutInflater.from(container.context).inflate(R.layout.tcl_chip, container, true)
        }
        for (i in 0 until container.childCount) {
            val tag = container.getChildAt(i) as TextView
            if (i < tags.size) {
                tag.text = tags[i]
                tag.visibility = View.VISIBLE
            } else {
                tag.visibility = View.GONE
            }
        }
        container.visibility = if (tags.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun levelOf(videoFormat: String?): Int = when (videoFormat?.let { Channels.getVideoResolution(it) }) {
        Channels.VIDEO_RESOLUTION_UHD -> StreamInfo.VIDEO_DEFINITION_LEVEL_ULTRA_HD
        Channels.VIDEO_RESOLUTION_FHD -> StreamInfo.VIDEO_DEFINITION_LEVEL_FULL_HD
        Channels.VIDEO_RESOLUTION_HD -> StreamInfo.VIDEO_DEFINITION_LEVEL_HD
        Channels.VIDEO_RESOLUTION_SD, Channels.VIDEO_RESOLUTION_ED -> StreamInfo.VIDEO_DEFINITION_LEVEL_SD
        else -> StreamInfo.VIDEO_DEFINITION_LEVEL_UNKNOWN
    }

    // Kurzformen wie bei der TCL
    private val LEVEL_LABELS = mapOf(
        StreamInfo.VIDEO_DEFINITION_LEVEL_ULTRA_HD to "UHD",
        StreamInfo.VIDEO_DEFINITION_LEVEL_FULL_HD to "FHD",
        StreamInfo.VIDEO_DEFINITION_LEVEL_HD to "HD",
        StreamInfo.VIDEO_DEFINITION_LEVEL_SD to "SD",
    )
    private const val CC = "CC"
    private const val DOLBY_LABEL = "Dolby"
    private val DOLBY = listOf("ac3", "ac4", "dolby")
}
