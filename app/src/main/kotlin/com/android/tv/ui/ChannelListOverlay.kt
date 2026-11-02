package com.android.tv.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import com.android.tv.ChannelTuner
import com.android.tv.MainActivity
import com.android.tv.R
import com.android.tv.data.Grupo
import com.android.tv.data.Grupos
import com.android.tv.data.ProgramDataManager
import com.android.tv.data.api.Channel
import com.android.tv.ui.hideable.AutoHideScheduler

/**
 * Kanalliste links im Stil von TCL Live TV (activity_channellist/item_channel), mit OK geöffnet:
 * Leiste mit "Todos" und den Gruppen des Nutzers (M3, am Handy angelegt; darunter das Zahnrad) und die
 * sichtbaren Kanäle mit Logo, Nummer, Name und laufender Sendung. OK schaltet um; OK auf dem laufenden
 * Kanal oder Zurück schließt die Liste; CH+/CH- blättern seitenweise.
 */
class ChannelListOverlay(
    private val activity: MainActivity,
    private val channelTuner: ChannelTuner,
    private val programDataManager: ProgramDataManager,
    private val onShown: Runnable,
    private val onHidden: Runnable,
    private val onSettings: Runnable,
) {
    private val container: View = activity.findViewById(R.id.channel_list_overlay)
    private val typeRail: LinearLayout = container.findViewById(R.id.channel_list_types)
    private val grid: VerticalGridView = container.findViewById(R.id.channel_list_grid)
    private val adapter = ChannelAdapter()
    private val autoHideScheduler = AutoHideScheduler(activity) { hide() }
    private val res = activity.resources
    private val showDurationMillis = res.getInteger(R.integer.tcl_motion_hide_list).toLong()
    private val unselectedAlpha = res.getFloat(R.dimen.tcl_alpha_unselected)
    private var allChannels: List<Channel> = emptyList()
    private var grupos: List<Grupo> = emptyList()
    /** null = "Todos". */
    private var category: Grupo? = null
    private var selectedRailItemId = View.NO_ID

    var isActive = false
        private set

    init {
        grid.adapter = adapter
        // Ohne Änderungsanimation bleibt der Fokus beim Umschalten auf der Zeile.
        grid.itemAnimator = null
        // Fokuszeile wie bei TCL bei y=404..540 von 1080 (Mitte 472 statt Bildschirmmitte).
        grid.windowAlignmentOffsetPercent = 472f / 1080f * 100f
    }

    fun show() {
        if (isActive) return
        allChannels = channelTuner.getBrowsableChannelList()
        grupos = Grupos.ler(activity)
        category = null
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

    fun scheduleHide() = autoHideScheduler.schedule(showDurationMillis)

    /** Eine Seite (sichtbare Zeilen) nach oben bzw. unten. */
    fun page(down: Boolean) {
        scheduleHide()
        if (adapter.itemCount == 0) return
        val step = (grid.childCount - 1).coerceAtLeast(1)
        val target = grid.selectedPosition + if (down) step else -step
        grid.selectedPosition = target.coerceIn(0, adapter.itemCount - 1)
        grid.requestFocus()
    }

    /** "Todos" fixo, dann die Gruppen des Nutzers mit Namen statt Icon (auch leere, damit man sie findet). */
    private fun buildRail() {
        typeRail.removeAllViews()
        val inflater = LayoutInflater.from(activity)
        for (c in listOf<Grupo?>(null) + grupos) {
            val item = inflater.inflate(R.layout.tcl_rail_text_item, typeRail, false) as TextView
            item.id = View.generateViewId()
            item.tag = c ?: ALL
            item.text = c?.nome ?: activity.getString(R.string.tcl_group_all)
            item.nextFocusRightId = R.id.channel_list_grid
            item.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) selectCategory(c) }
            item.setOnClickListener { grid.requestFocus() }
            typeRail.addView(item)
        }
        // Zahnrad ganz unten im Trilho (Abstandhalter dazwischen): öffnet das Menü auf der Zeile mit den Optionen.
        typeRail.addView(Space(activity), LinearLayout.LayoutParams(0, 0, 1f))
        val settings = inflater.inflate(R.layout.tcl_rail_item, typeRail, false)
        settings.contentDescription = activity.getString(R.string.tcl_channel_list_settings)
        settings.findViewById<ImageView>(R.id.channel_type_icon).setImageResource(R.drawable.tcl_ic_settings)
        settings.nextFocusRightId = R.id.channel_list_grid
        settings.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) scheduleHide() }
        settings.setOnClickListener {
            hide()
            onSettings.run()
        }
        settings.id = View.generateViewId()
        typeRail.addView(settings)
        // Sonst springt DOWN vom letzten Icon über den Abstandhalter in die Liste: Kette fest im Trilho.
        val items = (0 until typeRail.childCount).map { typeRail.getChildAt(it) }.filter { it !is Space }
        items.forEachIndexed { i, v ->
            v.nextFocusUpId = items[maxOf(i - 1, 0)].id
            v.nextFocusDownId = items[minOf(i + 1, items.lastIndex)].id
        }
        updateRail()
    }

    private fun updateRail() {
        for (i in 0 until typeRail.childCount) {
            val item = typeRail.getChildAt(i)
            val selected = item.tag == (category ?: ALL)
            if (selected) selectedRailItemId = item.id
            // TCL: gewählte Kategorie voll deckend, die anderen mit 60 % ("withalpha"); Namen ohne Icon ganz.
            (item.findViewById<View>(R.id.channel_type_icon) ?: item).alpha = if (selected) 1f else unselectedAlpha
        }
    }

    private fun selectCategory(c: Grupo?) {
        scheduleHide()
        if (c == category) return
        category = c
        updateRail()
        adapter.channels = if (c == null) allChannels else Grupos.filtrar(c, allChannels) { Grupos.chave(it.inputId, it.displayName) }
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

    private class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val row = TclChannelRow(view)
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
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.tcl_channel_row, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val channel = channels[position]
            holder.row.bind(channel, programDataManager.getCurrentProgram(channel.id),
                channel.id == channelTuner.currentChannelId)
            holder.itemView.nextFocusLeftId = selectedRailItemId
            holder.itemView.setOnClickListener { onChannelClicked(channel) }
        }
    }

    companion object {
        private val ALL = Any()
    }
}
