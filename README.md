# Essentia

Essentia es una app libre para Android que ejecuta fan games hechos con **RPG Maker XP, VX y VX Ace**, como los creados con *Pokémon Essentials*. La meta es que se abran **directo desde frontends** como Díptico, ES-DE o Daijishō, sin tener que entrar a otra biblioteca de juegos.

> Essentia no incluye juegos ni recursos de terceros. Cada persona usa los juegos que tiene.

## Estado

**2.0 beta para Android**, centrada en teléfono y AYN Thor. La versión de PC queda pospuesta.

Incluye controles por juego, guía inicial, panel inferior con equipo/notas/progreso compatible,
instalación ZIP/RAR/7z con preparación y recuperación, restauración local con doble confirmación,
avisos diarios optativos, revisión de traducciones y diagnóstico local. La IA con clave propia
es opcional y está desactivada; no se suministran claves ni créditos.

El [seguimiento de las 23 propuestas y sus límites](docs/2.0-beta.md) distingue lo implementado
de las pruebas físicas pendientes. El objetivo de mejorar 15–20 % el rendimiento necesita
mediciones reales; no se declara logrado por compilar o pasar pruebas automáticas.

## Descargar

El APK más reciente está en **[Releases → dev](../../releases/tag/dev)**. Por ahora es solo para equipos Android de 64 bits (por ejemplo, AYN Thor).

## Cómo se usa

- **Biblioteca:** al abrir Essentia aparece tu biblioteca. Con **+ Agregar juegos** eliges la carpeta de un juego (la que tiene `Game.exe` o `Game.ini`), o una carpeta con varios juegos, y Essentia los encuentra todos, con su nombre y su portada.
- **Controles:** A confirma (Enter), B cancela o abre el menú (Esc), Start abre el menú, X es Shift y L/R pasan de página. En el juego, **Atrás** o **Select + Start** abren el menú para salir.
- **Controles en pantalla:** para celulares y tablets. En *Ajustes* se eligen el modo (automático, siempre visibles o desactivados), el tamaño y la opacidad.
- **Dos pantallas:** en equipos como la AYN Thor, la segunda pantalla muestra la portada del juego elegido y, mientras juegas, un mapa en vivo con acciones (equipo, mochila, Pokédex, guardar, turbo, captura y trucos). Se puede desactivar en *Ajustes*.
- **Dos motores de Ruby:** *Moderno* (Ruby 3.1, el predeterminado) y *Clásico* (Ruby 1.8.7, el mismo de RPG Maker XP) para juegos muy antiguos. Se elige por juego: *Opciones → Motor de Ruby*.
- **Formato de pantalla:** bordes ambientales, original, estirado o panorámico inteligente (mapa ancho; menús y batallas en su tamaño original, centrados).
- **Turbo y capturas:** turbo x2–x4 (Select + R1) y capturas en *Imágenes/Essentia* (Select + L1).
- **RTP:** si un juego antiguo pide el RTP de RPG Maker, cópialo en `Essentia/RTP/XP` (o `VX`, `VXAce`).
- **Actualizaciones:** Essentia avisa sola cuando hay una versión nueva y se actualiza desde la propia app.
- **Borrar juegos:** desde Opciones puedes eliminar la carpeta de un juego con dos confirmaciones. Las partidas externas tienen una casilla adicional y se conservan por defecto.
- **Tienda y comunidades:** descubre fan games desde Whack a Hack! y Eevee Expo, filtra las fichas por comunidad y consulta sus portadas, capturas y vídeos. Las descargas se abren en la página de sus creadores; los juegos que aún no se probaron en Essentia se indican como «Sin probar».
- **Desde un frontend:** lanza la actividad `io.github.nikalbazu24.essentia/com.hatkid.mkxpz.LauncherActivity` con la ruta del juego, ya sea como dato (`file://` o `content://` del almacenamiento) o como extra de texto `path`. Puede ser la carpeta del juego o cualquier archivo dentro de ella, como `Game.ini`.

La primera vez, Essentia pide el permiso **acceso a todos los archivos**, necesario para leer los juegos desde cualquier carpeta.

## Créditos y licencia

Essentia se basa en:

- [mkxp-z](https://github.com/mkxp-z/mkxp-z), la reimplementación libre de RPG Maker XP/VX/VX Ace.
- [mkxp-z-android](https://github.com/thehatkid/mkxp-z-android), el port para Android de thehatkid (Sasha Ermohin).
- [Ruby](https://www.ruby-lang.org) 3.1 y 1.8.7 (licencia Ruby / GPLv2).
- Los scripts de compatibilidad de mkxp-z (`win32_wrap.rb`, `mkxp_wrap.rb`, `ruby_classic_wrap.rb`), de Ancurio, Splendide Imaginarius y WaywardHeart, en dominio público (CC0). Essentia los carga automáticamente para que funcionen juegos más antiguos, salvo que el juego traiga los suyos en su `mkxp.json`.

Se distribuye bajo la licencia **GNU GPL v2**, igual que mkxp-z (ver [LICENSE](LICENSE)).

---

**English:** Essentia is a free Android app to run RPG Maker XP/VX/VX Ace fan games (e.g. Pokémon Essentials), designed to be launched directly from frontends such as ES-DE or Daijishō. Based on mkxp-z and mkxp-z-android. GPLv2.
