# Usar Essentia con frontends

Essentia puede abrir tus juegos desde **Díptico**, **ES-DE**, **Daijishō** o cualquier launcher que sepa abrir un archivo con una app.

## 1. Crear los accesos (en Essentia)

1. Agrega tus juegos a la biblioteca de Essentia.
2. Ve a **Ajustes › Usar con frontends › Crear accesos de mis juegos**.
3. Elige una carpeta dentro de tus ROMs, por ejemplo `ROMs/rpgmaker`.

Essentia escribe un archivo pequeño por juego, por ejemplo `Pokémon Añil.essentia`. Adentro solo tiene la ruta de la carpeta del juego. Cuando agregas juegos nuevos a la biblioteca, sus accesos se crean solos en esa misma carpeta.

> Si prefieres hacerlos a mano: un archivo de texto con extensión `.essentia` cuya primera línea sea la carpeta del juego (por ejemplo `/storage/emulated/0/Juegos/Pokemon Anil`).

## 2. Configurar el frontend

### Díptico

Díptico reconoce la carpeta `rpgmaker` y abre los archivos `.essentia` con Essentia. No hay que configurar nada más.

### ES-DE

1. Copia los dos archivos de [`frontends/es-de/custom_systems`](../frontends/es-de/custom_systems) a la carpeta `ES-DE/custom_systems/` de tu equipo. Si ya tienes esos archivos, agrega solo el bloque `<system>` o `<emulator>` de cada uno.
2. Reinicia ES-DE. Aparecerá el sistema **RPG Maker XP / VX / VX Ace** con los juegos de `ROMs/rpgmaker`.

Essentia recibe el archivo con `%DATA%=%ROMSAF%`.

### Daijishō

1. Copia [`RPG Maker (Essentia).json`](../frontends/daijisho) a tu equipo.
2. En Daijishō: **Ajustes › Biblioteca › Importar plataforma** y elige ese archivo.
3. Asocia la carpeta `ROMs/rpgmaker` a la plataforma nueva y sincroniza.

### Otros launchers

Abre la actividad `io.github.nikalbazu24.essentia/com.hatkid.mkxpz.LauncherActivity` con la acción `android.intent.action.VIEW` y, como dato, el archivo `.essentia` (`file://` o `content://`). También acepta la carpeta del juego, cualquier archivo dentro de ella (como `Game.ini`), o un extra de texto `path` con la ruta.

Ejemplo con `am`:

```
am start -n io.github.nikalbazu24.essentia/com.hatkid.mkxpz.LauncherActivity \
  -a android.intent.action.VIEW -d "file:///storage/emulated/0/ROMs/rpgmaker/Pokemon Anil.essentia"
```

## Partidas

Por defecto, Essentia guarda las partidas **en la carpeta de cada juego**, igual que JoiPlay, así que puedes jugar la misma partida en las dos apps. Se puede cambiar en **Ajustes › Partidas**. Para traer partidas de Kirin o de un respaldo: en la biblioteca, mantén presionado el juego (o botón Y) › **Importar partidas**.
