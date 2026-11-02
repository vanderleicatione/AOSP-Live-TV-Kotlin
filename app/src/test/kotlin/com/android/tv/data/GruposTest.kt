package com.android.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GruposTest {
    private val antena = "com.tcl.tvinput/.TDTVInputService"

    @Test
    fun chaveIgnoraCaixaEspacoEIdDaLinha() {
        assertEquals(Grupos.chave(antena, " Globo HD "), Grupos.chave(antena, "globo hd"))
        assertEquals("$antena|globo hd", Grupos.chave(antena, "Globo HD"))
    }

    @Test
    fun encodeDecodeIdaEVolta() {
        val g = listOf(Grupo("Ação", listOf("a|x", "b|y")), Grupo("Infantil", emptyList()))
        assertEquals(g, Grupos.decode(Grupos.encode(g)))
        assertEquals(emptyList<Grupo>(), Grupos.decode(""))
    }

    @Test
    fun nomeComTabNaoQuebraOFormato() {
        val g = Grupos.criar(emptyList(), "A\tB\nC")!!
        assertEquals(listOf(Grupo("A B C", emptyList())), Grupos.decode(Grupos.encode(g)))
    }

    @Test
    fun criarRenomearRemover() {
        var g = Grupos.criar(emptyList(), "Ação")!!
        assertNull(Grupos.criar(g, "ação"))
        assertNull(Grupos.criar(g, "  "))
        g = Grupos.criar(g, "Infantil")!!
        assertNull(Grupos.renomear(g, "Ação", "infantil"))
        g = Grupos.renomear(g, "Ação", "Filmes")!!
        assertEquals(listOf("Filmes", "Infantil"), g.map { it.nome })
        assertEquals(listOf("Infantil"), Grupos.remover(g, "Filmes").map { it.nome })
    }

    @Test
    fun marcarSemRepetirEFiltrarNaOrdemDaLista() {
        var g = Grupos.criar(emptyList(), "Ação")!!
        g = Grupos.marcar(g, "Ação", "a|2", true)!!
        g = Grupos.marcar(g, "Ação", "a|1", true)!!
        g = Grupos.marcar(g, "Ação", "a|1", true)!!
        assertEquals(listOf("a|2", "a|1"), g[0].membros)
        assertNull(Grupos.marcar(g, "Outro", "a|1", true))
        val canais = listOf("a|1", "a|3", "a|2")
        assertEquals(listOf("a|1", "a|2"), Grupos.filtrar(g[0], canais) { it })
        g = Grupos.marcar(g, "Ação", "a|2", false)!!
        assertEquals(listOf("a|1"), g[0].membros)
    }
}
