package com.android.tv.ui

import android.content.Context
import android.util.Log
import com.android.tv.data.Grupo
import com.android.tv.data.Grupos
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

/**
 * Página dos grupos no celular (plano M3), no molde do `GestaoServer` do Canais+: no ar só enquanto o
 * QR está aberto, porta aleatória, token aleatório na URL checado em toda rota, só rede local.
 */
class GruposServer(private val context: Context, private val canais: List<Canal>) {
    /** Foto da lista de canais na hora do QR (a lista do app só pode ser lida na thread da tela). */
    data class Canal(val chave: String, val nome: String, val fonte: String)

    private var socket: ServerSocket? = null
    private var token = ""

    /** Liga o servidor; devolve a URL do QR, ou null sem rede local. */
    fun start(): String? {
        val host = lanAddress() ?: return null
        token = ByteArray(5).also(SecureRandom()::nextBytes).joinToString("") { "%02x".format(it) }
        val s = runCatching { ServerSocket(0) }.getOrElse { Log.w(TAG, "socket", it); return null }
        socket = s
        thread(name = "grupos", isDaemon = true) {
            while (!s.isClosed) {
                val client = runCatching { s.accept() }.getOrNull() ?: break
                runCatching { handle(client) }.onFailure { Log.w(TAG, "request", it) }
                runCatching { client.close() }
            }
        }
        return "http://$host:${s.localPort}/?t=$token"
    }

    fun stop() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun handle(client: Socket) {
        client.soTimeout = 15_000
        val out = client.getOutputStream()
        if (!isPrivate(client.inetAddress?.hostAddress)) return out.write(resp("403 Forbidden", "text/plain", "Só rede local."))
        val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
        val parts = (reader.readLine() ?: return).split(' ')
        if (parts.size < 2) return
        while (!reader.readLine().isNullOrEmpty()) Unit // cabeçalhos: não usados
        val method = parts[0]
        val path = parts[1].substringBefore('?')
        val q = query(parts[1].substringAfter('?', ""))
        if (token.isEmpty() || q["t"] != token) return out.write(resp("404 Not Found", "text/plain", "Not found."))

        fun muda(novo: List<Grupo>?, erro: String): ByteArray {
            if (novo == null) return resp("400 Bad Request", "text/plain", erro)
            Grupos.gravar(context, novo)
            return resp("200 OK", "application/json", dados())
        }
        val g = Grupos.ler(context)
        out.write(
            when {
                method == "GET" && path == "/" -> resp("200 OK", "text/html", GruposPage.HTML)
                method == "GET" && path == "/dados" -> resp("200 OK", "application/json", dados())
                method == "POST" && path == "/grupo" -> muda(Grupos.criar(g, q["nome"].orEmpty()), "Nome vazio ou repetido.")
                method == "POST" && path == "/grupo/renomear" ->
                    muda(Grupos.renomear(g, q["de"].orEmpty(), q["para"].orEmpty()), "Nome vazio ou repetido.")
                method == "POST" && path == "/grupo/remover" -> muda(Grupos.remover(g, q["nome"].orEmpty()), "")
                method == "POST" && path == "/membro" ->
                    muda(Grupos.marcar(g, q["grupo"].orEmpty(), q["chave"].orEmpty(), q["on"] == "1"), "Grupo não existe.")
                else -> resp("404 Not Found", "text/plain", "Not found.")
            },
        )
    }

    private fun dados(): String {
        val cs = JSONArray()
        for (c in canais) cs.put(JSONObject().put("chave", c.chave).put("nome", c.nome).put("fonte", c.fonte))
        val gs = JSONArray()
        for (g in Grupos.ler(context)) gs.put(JSONObject().put("nome", g.nome).put("membros", JSONArray(g.membros)))
        return JSONObject().put("canais", cs).put("grupos", gs).toString()
    }

    companion object {
        private const val TAG = "GruposServer"

        internal fun query(raw: String): Map<String, String> = raw.split('&').filter { '=' in it }.associate {
            fun dec(v: String) = URLDecoder.decode(v.replace("+", "%20"), "UTF-8")
            dec(it.substringBefore('=')) to dec(it.substringAfter('='))
        }

        internal fun isPrivate(host: String?): Boolean {
            val n = host?.split('.')?.map { it.toIntOrNull() ?: return false } ?: return false
            if (n.size != 4) return false
            return n[0] == 10 || n[0] == 127 || (n[0] == 192 && n[1] == 168) || (n[0] == 172 && n[1] in 16..31)
        }

        private fun resp(status: String, type: String, body: String): ByteArray {
            val bytes = body.toByteArray(Charsets.UTF_8)
            val head = "HTTP/1.1 $status\r\nContent-Type: $type; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                "Connection: close\r\nCache-Control: no-store\r\nReferrer-Policy: no-referrer\r\n\r\n"
            return head.toByteArray(Charsets.US_ASCII) + bytes
        }

        /** IPv4 privado da TV; pergunta às interfaces porque a TV pode estar no cabo. */
        private fun lanAddress(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .mapNotNull { it.hostAddress }
                .firstOrNull { !it.startsWith("127.") && isPrivate(it) }
        }.getOrNull()
    }
}
