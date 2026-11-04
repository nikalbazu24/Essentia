# Catálogo de la tienda de Essentia

`catalog.json` es la lista de fan games que muestra la **Tienda** de la app. La app la descarga desde este repositorio (como mucho cada 6 horas), así que para agregar o corregir un juego basta con editar este archivo en GitHub: no hace falta sacar una versión nueva de Essentia.

Essentia **no aloja juegos**: cada juego apunta a la página oficial de su creador.

## Campos de cada juego

| Campo | Para qué sirve |
|---|---|
| `id` | Nombre corto único, sin espacios (`pokemon-anil`). |
| `title` | Nombre del juego. |
| `aliases` | Otros nombres con que puede aparecer (para reconocerlo en la biblioteca). |
| `author` | Creador o equipo. |
| `lang` | Idiomas: `"es"`, `"en"`… |
| `essentials` | Versión de Pokémon Essentials (`"17.1"`, `"21.1"`). |
| `maker` | `RPG Maker XP`, `VX` o `VX Ace`. |
| `version` | Última versión publicada. Si está, Essentia avisa a quien tenga una más vieja. |
| `status` | Compatibilidad con Essentia: `perfecto`, `jugable`, `con_fallas`, `no_abre` o `sin_probar`. |
| `note` | Nota de compatibilidad (qué falla, qué se arregló). |
| `description` | Texto corto del juego. |
| `page` | Página oficial (web, foro, itch.io, GitHub, red social del creador). |
| `download` | Enlace directo a un `.zip` (GitHub, Dropbox o MEGA), **solo si el creador lo publica así**. Con él, Essentia instala y actualiza sola. |
| `pokeupdater` | `true` si el juego trae PokéUpdater (Essentia lo actualiza sola). |
| `cover` | Imagen de portada (enlace a una imagen publicada por el creador). Sin ella se dibuja una portada de color. |
| `screens` | Lista de capturas (enlaces a imágenes). |
| `size` | Tamaño aproximado (`"1,2 GB"`). |
| `color` | Color de la portada dibujada (`"#2F6FD6"`). |

Después de editar, copia el mismo contenido a `app/src/main/assets/essentia_catalog.json` cuando salga una versión nueva de la app (es la copia para el primer inicio sin internet).
