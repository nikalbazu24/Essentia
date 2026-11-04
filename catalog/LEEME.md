# Catálogo de la tienda de Essentia

`catalog.json` es la lista de fan games que muestra la **Tienda** de la app. La app la descarga desde este repositorio (como mucho cada 6 horas), así que para agregar o corregir un juego basta con editar este archivo en GitHub: no hace falta sacar una versión nueva de Essentia.

Essentia **no aloja juegos**: cada juego apunta a la página oficial de su creador.

## Comunidades y multimedia

La tienda permite explorar **Whack a Hack!** (catálogo filtrado por RPG Maker XP)
y **Eevee Expo** (juegos completados). Los botones abren sus páginas en el navegador.
Ambas comunidades también publican proyectos de otros motores: aparecer allí no
confirma que un juego funcione en Essentia. El estado de compatibilidad se conserva
por separado y los juegos nuevos se marcan `sin_probar`.

Las fichas seleccionadas enlazan portadas, arte de título, capturas y vídeos publicados
por sus creadores o en la ficha de la comunidad. Se muestran los créditos y un enlace
a esa ficha. Essentia no copia juegos ni aloja las imágenes; las descargas continúan
desde las páginas de sus creadores. Las imágenes se guardan temporalmente en el
dispositivo después de cargarlas. Una imagen que deja de estar disponible no impide
abrir la ficha: se conserva el título sobre el fondo de color.

Esta integración es una selección mantenida en `catalog.json`, no una importación
automática de todos los juegos de esas webs. Sus catálogos completos siguen accesibles
desde los botones «Explorar». Para añadir una ficha, comprueba los enlaces reales y
los créditos de sus imágenes; no deduzcas versiones nuevas ni compatibilidad a partir
del estado de publicación de la comunidad.

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
| `logo` | Logotipo o arte de título. Se muestra sin recortar en la ficha; también sirve de portada si no hay `cover`. |
| `screens` | Lista de capturas (enlaces a imágenes). |
| `source_page` | Ficha concreta en `https://whackahack.com/` o `https://eeveeexpo.com/` (también se acepta `www`). Determina el filtro de comunidad. |
| `media_credit` | Autor/equipo de las imágenes y comunidad donde se publicaron. Obligatorio si hay imágenes o vídeos. |
| `media_page` | Página original de la galería cuando la publica directamente el creador fuera de las dos comunidades. HTTPS; no convierte el sitio en una comunidad del filtro. |
| `community_rank` | Posición de 1 a 5 en la selección por puntuación de reacciones del primer mensaje de Eevee Expo; independiente de compatibilidad. |
| `popularity_page` | URL del listado ordenado que permite comprobar la posición. |
| `popularity_checked` | Fecha ISO de la revisión de popularidad. Es una selección fechada, no un ranking sincronizado en directo. |
| `trailer` | Vídeo o tráiler HTTPS publicado en la ficha. Se abre en el navegador o aplicación de vídeo. |
| `size` | Tamaño aproximado (`"1,2 GB"`). |
| `color` | Color de la portada dibujada (`"#2F6FD6"`). |

Después de editar, copia el mismo contenido a `app/src/main/assets/essentia_catalog.json` cuando salga una versión nueva de la app (es la copia para el primer inicio sin internet).

El catálogo con multimedia y popularidad declara `"version": 3`: una copia antigua en caché o en
internet no debe sustituir la incluida en la nueva app. Antes de publicar, ejecuta
`python3 tools/check_catalog.py`. Comprueba las dos copias, enlaces HTTPS, identificadores,
créditos y estados de compatibilidad. La compilación de GitHub ejecuta esta validación
y las pruebas de lectura del catálogo.

Fuentes comprobadas el 4 de noviembre de 2026:

- [Whack a Hack! — RPG Maker XP](https://whackahack.com/juegos/?category=4)
- Fichas de [Añil](https://whackahack.com/juegos/pokemon-anil/),
  [Iberia](https://whackahack.com/juegos/pokemon-iberia/),
  [Ópalo](https://whackahack.com/juegos/pokemon-opalo-2/),
  [Z](https://whackahack.com/juegos/pokemon-z/),
  [Reborn](https://whackahack.com/juegos/pokemon-reborn/),
  [Consonancia](https://whackahack.com/juegos/pokemon-consonancia/),
  [Tectonic](https://whackahack.com/juegos/pokemon-tectonic/) y
  [Edición Sombría](https://whackahack.com/juegos/pokemon-edicion-sombria/).
- [Eevee Expo — juegos completados](https://eeveeexpo.com/completed-games/)
  y [publicación de Hydro Bliss por 5PastPoliwag](https://eeveeexpo.com/threads/9100/).
