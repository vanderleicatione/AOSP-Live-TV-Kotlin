package com.android.tv.ui

/** A página dos grupos no celular (M3): um HTML só, no estilo da página do Canais+. Sem `$` no JS. */
object GruposPage {
    const val HTML = """<!doctype html>
<html lang="pt-BR"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Grupos do Live TV</title>
<style>
body{margin:0;font-family:Montserrat,system-ui,sans-serif;background:#16181d;color:#f2f2f2}
header{position:sticky;top:0;background:#16181d;padding:16px;border-bottom:1px solid #2a2d33}
h1{margin:0 0 8px;font-size:22px}h1:after{content:"";display:block;width:36px;height:3px;background:#e50914;margin-top:6px}
input,select{box-sizing:border-box;padding:9px;border-radius:6px;border:1px solid #3a3d43;background:#22252b;color:#fff;font-size:16px}
.r{display:flex;gap:6px;margin-top:8px}.r input,.r select{flex:1;min-width:0}
button{padding:8px 12px;border:0;border-radius:6px;background:#f2f2f2;color:#111;font-weight:600;font-size:14px}
button.x{background:#2a2d33;color:#ff8a8a}#msg{font-size:14px;color:#9aa0a6;margin-top:8px;min-height:18px}
main{padding:0 16px 32px}label.c{display:flex;align-items:center;gap:10px;border-bottom:1px solid #2a2d33;padding:12px 0}
label.c input{width:22px;height:22px;accent-color:#e50914}.f{font-size:13px;color:#9aa0a6;margin-left:auto}
</style></head><body>
<header><h1>Grupos do Live TV</h1>
<div class="r"><input id="novo" placeholder="Novo grupo (ex.: Ação)"><button id="criar">Criar</button></div>
<div class="r"><select id="grupo"></select><button id="ren">Renomear</button><button id="rem" class="x">Apagar</button></div>
<div class="r"><input id="busca" type="search" placeholder="Buscar canal"></div>
<div id="msg"></div></header>
<main id="lista"></main>
<script>
var T=new URLSearchParams(location.search).get('t'),D={canais:[],grupos:[]},atual='';
function api(m,p,params){params=params||{};params.t=T;return fetch(p+'?'+new URLSearchParams(params),{method:m}).then(function(r){if(!r.ok)return r.text().then(function(e){throw e});return r.json()})}
function msg(t){document.getElementById('msg').textContent=t}
function load(d){D=d;var s=document.getElementById('grupo');s.innerHTML='';
 if(!D.grupos.some(function(g){return g.nome==atual}))atual=D.grupos.length?D.grupos[0].nome:'';
 D.grupos.forEach(function(g){var o=document.createElement('option');o.value=g.nome;o.textContent=g.nome+' ('+g.membros.length+')';o.selected=g.nome==atual;s.appendChild(o)});
 if(!D.grupos.length){var o=document.createElement('option');o.textContent='Crie um grupo acima';s.appendChild(o)}
 render()}
function render(){var L=document.getElementById('lista'),b=document.getElementById('busca').value.toLowerCase();L.innerHTML='';
 var g=D.grupos.filter(function(x){return x.nome==atual})[0];if(!g)return;
 D.canais.filter(function(c){return c.nome.toLowerCase().indexOf(b)>=0}).forEach(function(c){
  var l=document.createElement('label');l.className='c';var i=document.createElement('input');i.type='checkbox';i.checked=g.membros.indexOf(c.chave)>=0;
  i.onchange=function(){api('POST','/membro',{grupo:atual,chave:c.chave,on:i.checked?'1':'0'}).then(function(d){D=d;load(d);msg('Salvo. Abra a lista de canais na TV para ver.')}).catch(msg)};
  var n=document.createElement('span');n.textContent=c.nome;var f=document.createElement('span');f.className='f';f.textContent=c.fonte;
  l.appendChild(i);l.appendChild(n);l.appendChild(f);L.appendChild(l)})}
document.getElementById('grupo').onchange=function(e){atual=e.target.value;render()};
document.getElementById('busca').oninput=render;
document.getElementById('criar').onclick=function(){var n=document.getElementById('novo').value;
 api('POST','/grupo',{nome:n}).then(function(d){atual=n.trim();document.getElementById('novo').value='';load(d);msg('Grupo criado.')}).catch(msg)};
document.getElementById('ren').onclick=function(){if(!atual)return;var n=prompt('Novo nome do grupo',atual);if(!n)return;
 api('POST','/grupo/renomear',{de:atual,para:n}).then(function(d){atual=n.trim();load(d);msg('Renomeado.')}).catch(msg)};
document.getElementById('rem').onclick=function(){if(!atual||!confirm('Apagar o grupo '+atual+'?'))return;
 api('POST','/grupo/remover',{nome:atual}).then(function(d){load(d);msg('Apagado.')}).catch(msg)};
api('GET','/dados').then(load).catch(function(e){msg('Erro: '+e)});
</script></body></html>"""
}
