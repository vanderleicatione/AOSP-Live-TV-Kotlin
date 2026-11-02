package com.android.tv.ui.sidepanel

import android.graphics.Bitmap
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.android.tv.R
import com.android.tv.data.Grupos
import com.android.tv.ui.GruposServer
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/** Configurações → Grupos de canais (M3): QR da página do celular; o servidor fica no ar enquanto o painel está aberto. */
class GruposFragment : SideFragment<Item>() {
    private var server: GruposServer? = null

    override fun getTitle(): String = getString(R.string.settings_groups)

    override fun getItemList(): List<Item> {
        server?.stop()
        val canais = channelDataManager.getBrowsableChannelList().map {
            GruposServer.Canal(Grupos.chave(it.inputId, it.displayName), it.displayName.orEmpty(), fonte(it.inputId))
        }
        val s = GruposServer(requireContext().applicationContext, canais)
        server = s
        return listOf(QrItem(s.start()))
    }

    override fun onDestroyView() {
        server?.stop()
        server = null
        super.onDestroyView()
    }

    private inner class QrItem(private val url: String?) : Item() {
        override fun getResourceId() = R.layout.tcl_qr_item

        override fun onBind(view: View) {
            super.onBind(view)
            val px = resources.getDimensionPixelSize(R.dimen.tcl_qr_size)
            val image = view.findViewById<ImageView>(R.id.qr_image)
            image.visibility = if (url == null) View.GONE else View.VISIBLE
            if (url != null) image.setImageBitmap(qrBitmap(url, px))
            view.findViewById<TextView>(R.id.qr_hint).setText(if (url == null) R.string.groups_no_network else R.string.groups_qr_hint)
            view.findViewById<TextView>(R.id.qr_url).text = url?.substringBefore("?").orEmpty()
        }

        override fun onSelected() {}
    }

    companion object {
        /** O mesmo corte de fontes do trilho antigo: antena da TV, TCL Channel+, o resto (Canais+ …). */
        fun fonte(inputId: String): String = when {
            inputId.startsWith("com.tcl.tvinput/") -> "Antena"
            inputId.startsWith("com.tcl.channelplus/") -> "Channel+"
            inputId.startsWith("br.canais.provedor") -> "Canais+"
            else -> inputId.substringBefore('/')
        }

        private fun qrBitmap(text: String, px: Int): Bitmap {
            val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, px, px)
            val pixels = IntArray(px * px) { if (m[it % px, it / px]) Color.BLACK else Color.WHITE }
            return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
        }
    }
}
