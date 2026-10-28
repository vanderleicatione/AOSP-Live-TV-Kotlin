package com.android.tv.ui

import android.graphics.Bitmap
import android.media.tv.TvInputInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import com.android.tv.ChannelTuner
import com.android.tv.MainActivity
import com.android.tv.R
import com.android.tv.data.ProgramDataManager
import com.android.tv.data.api.Channel
import com.android.tv.ui.hideable.AutoHideScheduler
import com.android.tv.util.TvInputManagerHelper
import com.android.tv.util.images.ImageLoader

/**
 * Kanalliste links im Stil von TCL Live TV (activity_channellist/item_channel), mit OK geöffnet:
 * Kategorieleiste (alle / Streaming / digital) und die sichtbaren Kanäle mit Logo, Nummer, Name und
 * laufender Sendung. OK schaltet um; OK auf dem laufenden Kanal oder Zurück schließt die Liste.
 */
class ChannelListOverlay(
    private val activity: MainActivity,
    private val channelTuner: ChannelTuner,
    private val programDataManager: ProgramDataManager,
    private val inputManager: TvInputManagerHelper,
    private val onShown: Runnable,
    private val onHidden: Runnable,
) {
    private val container: View = activity.findViewById(R.id.channel_list_overlay)
    private val typeRail: LinearLayout = container.findViewById(R.id.channel_list_types)
    private val grid: VerticalGridView = container.findViewById(R.id.channel_list_grid)
    private val adapter = ChannelAdapter()
    private val autoHideScheduler = AutoHideScheduler(activity) { hide() }
    private val res = activity.resources
    private val logoWidth = res.getDimensionPixelSize(R.dimen.tcl_channel_list_logo_width)
    private val logoHeight = res.getDimensionPixelSize(R.dimen.tcl_channel_list_logo_height)
    private val programStart = res.getDimensionPixelSize(R.dimen.tcl_channel_list_program_start)
    private val programStartPlaying = res.getDimensionPixelSize(R.dimen.tcl_channel_list_program_start_playing)
    private var allChannels: List<Channel> = emptyList()
    private var category = Category.ALL
    private var selectedRailItemId = View.NO_ID

    var isActive = false
        private set

    init {
        grid.adapter = adapter
        // Ohne Änderungsanimation bleibt der Fokus beim Umschalten auf der Zeile.
        grid.itemAnimator = null
    }

    fun show() {
        if (isActive) return
        allChannels = channelTuner.getBrowsableChannelList()
        category = Category.ALL
        buildRail()
        adapter.channels = allChannels
        container.visibility = View.VISIBLE
        isActive = true
        onShown.run()
        grid.selectedPosition = adapter.indexOf(channelTuner.currentChannelId).coerceAtLeast(0)
        grid.requestFocus()
        scheduleHide()
    }

    fun hide() {
        if (!isActive) return
        autoHideScheduler.cancel()
        isActive = false
        container.visibility = View.GONE
        onHidden.run()
    }

    fun scheduleHide() = autoHideScheduler.schedule(SHOW_DURATION_MILLIS)

    private fun Channel.category(): Category =
        if (inputManager.getTvInputInfo(inputId)?.type == TvInputInfo.TYPE_TUNER) Category.DIGITAL else Category.STREAMING

    /** Wie bei TCL nur Kategorien mit Kanälen; "Alle" immer. */
    private fun buildRail() {
        typeRail.removeAllViews()
        val present = allChannels.mapTo(HashSet()) { it.category() }
        val inflater = LayoutInflater.from(activity)
        for (c in Category.entries) {
            if (c != Category.ALL && c !in present) continue
            val item = inflater.inflate(R.layout.tcl_channel_type_item, typeRail, false)
            item.id = View.generateViewId()
            item.tag = c
            item.contentDescription = activity.getString(c.labelRes)
            item.findViewById<ImageView>(R.id.channel_type_icon).setImageResource(c.iconRes)
            item.nextFocusRightId = R.id.channel_list_grid
            item.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) selectCategory(c) }
            item.setOnClickListener { grid.requestFocus() }
            typeRail.addView(item)
        }
        updateRail()
    }

    private fun updateRail() {
        for (i in 0 until typeRail.childCount) {
            val item = typeRail.getChildAt(i)
            val selected = item.tag == category
            if (selected) selectedRailItemId = item.id
            // TCL: gewählte Kategorie voll deckend, die anderen mit 60 % ("withalpha").
            item.findViewById<View>(R.id.channel_type_icon).alpha = if (selected) 1f else 0.6f
        }
    }

    private fun selectCategory(c: Category) {
        scheduleHide()
        if (c == category) return
        category = c
        updateRail()
        adapter.channels = if (c == Category.ALL) allChannels else allChannels.filter { it.category() == c }
        grid.selectedPosition = adapter.indexOf(channelTuner.currentChannelId).coerceAtLeast(0)
    }

    private fun onChannelClicked(channel: Channel) {
        val previous = adapter.indexOf(channelTuner.currentChannelId)
        if (channel.id == channelTuner.currentChannelId) {
            hide()
            return
        }
        activity.tuneToChannel(channel)
        if (previous >= 0) adapter.notifyItemChanged(previous)
        adapter.notifyItemChanged(adapter.indexOf(channel.id))
        scheduleHide()
    }

    private fun programText(channel: Channel): String {
        val program = programDataManager.getCurrentProgram(channel.id)
        val title = program?.title
        if (program == null || title.isNullOrEmpty()) return activity.getString(R.string.tcl_no_information)
        val episode = program.getEpisodeDisplayTitle(activity)
        return if (episode.isNullOrEmpty()) title else "$title: $episode"
    }

    private enum class Category(val iconRes: Int, val labelRes: Int) {
        ALL(R.drawable.tcl_ic_type_all, R.string.tcl_channel_type_all),
        STREAMING(R.drawable.tcl_ic_type_streaming, R.string.tcl_channel_type_streaming),
        DIGITAL(R.drawable.tcl_ic_type_digital, R.string.tcl_channel_type_digital),
    }

    private class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val logo: ImageView = view.findViewById(R.id.channel_logo)
        val number: TextView = view.findViewById(R.id.channel_number)
        val name: TextView = view.findViewById(R.id.channel_name)
        val playing: View = view.findViewById(R.id.channel_playing)
        val program: TextView = view.findViewById(R.id.channel_program)
    }

    private inner class ChannelAdapter : RecyclerView.Adapter<ViewHolder>() {
        var channels: List<Channel> = emptyList()
            set(value) {
                field = value
                notifyDataSetChanged()
            }

        fun indexOf(channelId: Long) = channels.indexOfFirst { it.id == channelId }

        override fun getItemCount() = channels.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.tcl_channel_list_item, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val channel = channels[position]
            holder.number.text = channel.displayNumber
            holder.name.text = channel.displayName
            val playing = channel.id == channelTuner.currentChannelId
            holder.playing.visibility = if (playing) View.VISIBLE else View.GONE
            val lp = holder.program.layoutParams as ViewGroup.MarginLayoutParams
            lp.marginStart = if (playing) programStartPlaying else programStart
            holder.program.layoutParams = lp
            holder.program.text = programText(channel)
            holder.logo.setImageDrawable(null)
            holder.logo.tag = channel.id
            if (channel.channelLogoExists()) {
                channel.loadBitmap(activity, Channel.LOAD_IMAGE_TYPE_CHANNEL_LOGO, logoWidth, logoHeight,
                    LogoCallback(holder.logo, channel.id))
            }
            holder.itemView.nextFocusLeftId = selectedRailItemId
            holder.itemView.setOnClickListener { onChannelClicked(channel) }
        }
    }

    /** Recycelte Zeilen: Logo nur setzen, wenn die Zeile noch denselben Kanal zeigt. */
    private class LogoCallback(view: ImageView, private val channelId: Long) :
        ImageLoader.ImageLoaderCallback<ImageView>(view) {
        override fun onBitmapLoaded(referent: ImageView, bitmap: Bitmap?) {
            if (referent.tag == channelId) referent.setImageBitmap(bitmap)
        }
    }

    companion object {
        private const val SHOW_DURATION_MILLIS = 15_000L
    }
}
