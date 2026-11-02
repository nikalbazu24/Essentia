# Essentia

Essentia es una app libre para Android que ejecuta fan games hechos con **RPG Maker XP, VX y VX Ace**, como los creados con *Pokémon Essentials*. La meta es que se abran **directo desde frontends** como Díptico, ES-DE o Daijishō, sin tener que entrar a otra biblioteca de juegos.

> Essentia no incluye juegos ni recursos de terceros. Cada persona usa los juegos que tiene.

## Estado

En desarrollo temprano. Plan por etapas:

1. **Que arranque:** compilar automáticamente y abrir un fan game moderno (Essentials v20 o más nuevo). ← *estamos aquí*
2. **Que se abra desde afuera:** recibir la ruta del juego desde Díptico, ES-DE o Daijishō. ← *base lista, falta probar con cada frontend*
3. **Que se juegue bien:** controles, pantalla completa, guardado, sonido y fluidez.
4. **Que corran los antiguos:** compatibilidad con juegos de Essentials anteriores a la v20.
5. **Para la comunidad:** lista de juegos probados e integración con frontends.

## Descargar

El APK más reciente está en **[Releases → dev](../../releases/tag/dev)**. Por ahora es solo para equipos Android de 64 bits (por ejemplo, AYN Thor).

## Cómo se usa

- **Desde su ícono:** Essentia abre el selector de carpetas de Android. Elige la carpeta del juego (la que tiene `Game.exe` o `Game.ini`), esté donde esté: memoria interna o tarjeta SD. La próxima vez el selector parte en esa misma carpeta.
- **Desde un frontend:** lanza la actividad `io.github.nikalbazu24.essentia/com.hatkid.mkxpz.LauncherActivity` con la ruta del juego, ya sea como dato (`file://` o `content://` del almacenamiento) o como extra de texto `path`. Puede ser la carpeta del juego o cualquier archivo dentro de ella, como `Game.ini`.

La primera vez, Essentia pide el permiso **acceso a todos los archivos**, necesario para leer los juegos desde cualquier carpeta.

## Créditos y licencia

Essentia se basa en:

- [mkxp-z](https://github.com/mkxp-z/mkxp-z), la reimplementación libre de RPG Maker XP/VX/VX Ace.
- [mkxp-z-android](https://github.com/thehatkid/mkxp-z-android), el port para Android de thehatkid (Sasha Ermohin).
- Los scripts de compatibilidad de mkxp-z (`win32_wrap.rb`, `mkxp_wrap.rb`, `ruby_classic_wrap.rb`), de Ancurio, Splendide Imaginarius y WaywardHeart, en dominio público (CC0). Essentia los carga automáticamente para que funcionen juegos más antiguos, salvo que el juego traiga los suyos en su `mkxp.json`.

Se distribuye bajo la licencia **GNU GPL v2**, igual que mkxp-z (ver [LICENSE](LICENSE)).

---

**English:** Essentia is a free Android app to run RPG Maker XP/VX/VX Ace fan games (e.g. Pokémon Essentials), designed to be launched directly from frontends such as ES-DE or Daijishō. Based on mkxp-z and mkxp-z-android. GPLv2.
