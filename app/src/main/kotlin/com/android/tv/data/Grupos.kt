package com.android.tv.data

import android.content.Context

/**
 * Grupos do usuário (plano M3): nome + canais, montados pelo celular. O canal entra pela chave
 * estável (input + nome), que sobrevive a uma nova busca de canais, e não pelo id da linha do TIF.
 * "Todos" é fixo e não fica guardado aqui.
 */
data class Grupo(val nome: String, val membros: List<String>)

object Grupos {
    private const val PREFS = "grupos_usuario"
    private const val KEY = "grupos"

    fun chave(inputId: String, nome: String?): String = limpa("$inputId|${nome.orEmpty().trim().lowercase()}")

    fun ler(context: Context): List<Grupo> =
        decode(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null).orEmpty())

    fun gravar(context: Context, grupos: List<Grupo>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, encode(grupos)).apply()
    }

    /** Uma linha por grupo: nome, depois as chaves, separados por tab. */
    fun encode(grupos: List<Grupo>): String = grupos.joinToString("\n") { g -> (listOf(g.nome) + g.membros).joinToString("\t") }

    fun decode(raw: String): List<Grupo> = raw.lines().filter { it.isNotBlank() }.map { line ->
        val p = line.split('\t')
        Grupo(p[0], p.drop(1).filter { it.isNotEmpty() })
    }

    /** Tab e quebra de linha são os separadores do formato. */
    fun limpa(s: String): String = s.replace(Regex("[\\t\\r\\n]"), " ").trim()

    fun criar(grupos: List<Grupo>, nome: String): List<Grupo>? {
        val n = limpa(nome)
        if (n.isEmpty() || grupos.any { it.nome.equals(n, true) }) return null
        return grupos + Grupo(n, emptyList())
    }

    fun renomear(grupos: List<Grupo>, de: String, para: String): List<Grupo>? {
        val n = limpa(para)
        if (n.isEmpty() || grupos.none { it.nome == de } || grupos.any { it.nome != de && it.nome.equals(n, true) }) return null
        return grupos.map { if (it.nome == de) it.copy(nome = n) else it }
    }

    fun remover(grupos: List<Grupo>, nome: String): List<Grupo> = grupos.filterNot { it.nome == nome }

    fun marcar(grupos: List<Grupo>, nome: String, chave: String, dentro: Boolean): List<Grupo>? {
        if (grupos.none { it.nome == nome } || chave.isEmpty()) return null
        return grupos.map { g ->
            if (g.nome != nome) g
            else if (dentro) g.copy(membros = (g.membros - chave) + chave)
            else g.copy(membros = g.membros - chave)
        }
    }

    /** Os canais do grupo, na ordem da lista de canais. */
    fun <T> filtrar(grupo: Grupo, canais: List<T>, chaveDe: (T) -> String): List<T> {
        val set = grupo.membros.toHashSet()
        return canais.filter { chaveDe(it) in set }
    }
}
