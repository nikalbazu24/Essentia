# Essentia

Essentia es una app libre para Android que ejecuta fan games hechos con **RPG Maker XP, VX y VX Ace**, como los creados con *Pokémon Essentials*. La meta es que se abran **directo desde frontends** como Díptico, ES-DE o Daijishō, sin tener que entrar a otra biblioteca de juegos.

> Essentia no incluye juegos ni recursos de terceros. Cada persona usa los juegos que tiene.

## Estado

En desarrollo temprano. Plan por etapas:

1. **Que arranque:** compilar automáticamente y abrir un fan game moderno (Essentials v20 o más nuevo). ← *estamos aquí*
2. **Que se abra desde afuera:** recibir la ruta del juego desde Díptico, ES-DE o Daijishō.
3. **Que se juegue bien:** controles, pantalla completa, guardado, sonido y fluidez.
4. **Que corran los antiguos:** compatibilidad con juegos de Essentials anteriores a la v20.
5. **Para la comunidad:** lista de juegos probados e integración con frontends.

## Descargar

El APK más reciente está en **[Releases → dev](../../releases/tag/dev)**. Por ahora es solo para equipos Android de 64 bits (por ejemplo, AYN Thor).

## Probar un juego (versión actual)

1. Instala el APK y ábrelo una vez; concede el permiso de **acceso a todos los archivos**.
2. Copia los archivos del juego (la carpeta donde está `Game.exe` y `Game.ini`) dentro de la carpeta `mkxp-z` en la memoria interna: `/storage/emulated/0/mkxp-z/`.
3. Abre Essentia.

## Créditos y licencia

Essentia se basa en:

- [mkxp-z](https://github.com/mkxp-z/mkxp-z), la reimplementación libre de RPG Maker XP/VX/VX Ace.
- [mkxp-z-android](https://github.com/thehatkid/mkxp-z-android), el port para Android de thehatkid (Sasha Ermohin).

Se distribuye bajo la licencia **GNU GPL v2**, igual que mkxp-z (ver [LICENSE](LICENSE)).

---

**English:** Essentia is a free Android app to run RPG Maker XP/VX/VX Ace fan games (e.g. Pokémon Essentials), designed to be launched directly from frontends such as ES-DE or Daijishō. Based on mkxp-z and mkxp-z-android. GPLv2.
