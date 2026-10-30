package com.android.tv.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.android.tv.R
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import com.android.tv.util.images.ImageLoader

/**
 * Design-System-Komponente TclChannelRow (docs/design-system.md): Logo, Nummer, Name, laufende Sendung und
 * "läuft"-Marke. Bindet tcl_channel_row (Liste) und tcl_channel_row_compact (Banner, Kanal davor/danach);
 * was das Layout nicht hat, wird übersprungen.
 */
class TclChannelRow(val view: View) {
    private val logo: ImageView? = view.findViewById(R.id.tcl_row_logo)
    private val number: TextView? = view.findViewById(R.id.tcl_row_number)
    private val name: TextView? = view.findViewById(R.id.tcl_row_name)
    private val playing: View? = view.findViewById(R.id.tcl_row_playing)
    private val program: TextView? = view.findViewById(R.id.tcl_row_program)

    fun bind(channel: Channel, program: Program?, isPlaying: Boolean) {
        val res = view.resources
        number?.text = channel.displayNumber
        name?.text = channel.displayName
        playing?.visibility = if (isPlaying) View.VISIBLE else View.GONE
        this.program?.let {
            val lp = it.layoutParams as ViewGroup.MarginLayoutParams
            lp.marginStart = res.getDimensionPixelSize(
                if (isPlaying) R.dimen.tcl_channel_row_text_start_playing else R.dimen.tcl_channel_row_text_start)
            it.layoutParams = lp
            it.text = programTitle(view.context, program)
        }
        logo?.let { loadLogo(it, channel) }
    }

    companion object {
        /** Die eine Regel für Sendungen ohne Titel: "Sem informações" (Banner, Liste, EPG, Menü). */
        fun titleOrNoInformation(res: Resources, title: String?): String =
            if (title.isNullOrEmpty()) res.getString(R.string.tcl_no_information) else title

        /** Titel mit Folge ("Titel: Folge"), wie in Liste und EPG-Kopf. */
        fun programTitle(context: Context, program: Program?): String {
            val title = program?.title
            if (program == null || title.isNullOrEmpty()) return titleOrNoInformation(context.resources, null)
            val episode = program.getEpisodeDisplayTitle(context)
            return if (episode.isNullOrEmpty()) title else "$title: $episode"
        }

        /**
         * TclLogoCard: lädt das Logo (TIF, sonst Feed) in der Größe der Karte. Recycelte Views: das Bild wird
         * nur gesetzt, wenn die Karte noch denselben Kanal zeigt.
         */
        fun loadLogo(view: ImageView, channel: Channel) {
            view.setImageDrawable(null)
            view.tag = channel.id
            if (!channel.channelLogoExists()) return
            val lp = view.layoutParams
            channel.loadBitmap(view.context, Channel.LOAD_IMAGE_TYPE_CHANNEL_LOGO, lp.width, lp.height,
                object : ImageLoader.ImageLoaderCallback<ImageView>(view) {
                    override fun onBitmapLoaded(referent: ImageView, bitmap: Bitmap?) {
                        if (referent.tag == channel.id) referent.setImageBitmap(bitmap)
                    }
                })
        }
    }
}
