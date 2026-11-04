# Borrado local de juegos

Disponible desde **Biblioteca → Opciones → Borrar juego y archivos** y desde
la ficha de la tienda cuando el juego está instalado. **Quitar de la biblioteca**
continúa siendo una acción distinta que no borra la carpeta del juego.

## Confirmaciones y alcance

1. La primera confirmación muestra el juego, su carpeta y un aviso de que también
   se borrarán las partidas guardadas dentro de esa carpeta.
2. La segunda vuelve a mostrar las carpetas elegidas, avisa de que la acción no
   se puede deshacer y exige pulsar **Borrar definitivamente**.

Ambos diálogos enfocan **Cancelar**. El botón de borrado final permanece
deshabilitado durante 1,5 segundos para evitar aceptar las dos confirmaciones
con pulsaciones seguidas del mando o un doble toque.

Las partidas externas en `Essentia/Partidas/<nombre>` se conservan por defecto.
Se ofrece una casilla para borrarlas si esa carpeta existe y no coincide con
el nombre o la carpeta de otro juego registrado. El nombre de una entrada
renombrada no permite atribuirle otras carpetas antiguas: no se borran carpetas
por aproximación. Tampoco se borran los respaldos en la nube, ZIP originales
ni archivos fuera de las carpetas confirmadas.

## Límites del borrado

- Solo se permite una carpeta de juego registrada dentro del almacenamiento
  principal o de una tarjeta reconocida por Android. Se comprueban archivos de
  identificación de RPG Maker antes de borrar.
- Se rechazan raíces de almacenamiento, carpetas generales como Descargas,
  carpetas de datos de Android, contenedores de Essentia y carpetas que se solapen
  con otro juego registrado. La revisión también detecta juegos anidados que
  todavía no estén registrados.
- Se revisan todos los árboles seleccionados antes del primer borrado. Los
  enlaces simbólicos encontrados dentro del juego se eliminan como enlaces;
  nunca se recorre ni se borra el contenido al que apuntan. No se admite un
  enlace simbólico como carpeta raíz del juego.
- Se rechaza el borrado mientras esté abierto un proceso de juego o haya una
  instalación/actualización en curso. Durante el borrado tampoco se pueden
  iniciar juegos ni nuevos trabajos del actualizador.
- El borrado se ejecuta fuera del hilo de interfaz. Si falla, se informa del
  posible borrado parcial y se conserva la entrada de la biblioteca. Solo se
  confirma éxito tras borrar las carpetas seleccionadas y quitar la entrada.
  La detección de nuevos juegos valida de nuevo la carpeta para evitar reañadir
  un resultado antiguo de una exploración iniciada antes del borrado.

## Verificación

`GameFilesTest` trabaja con carpetas temporales: borrado completo, archivos
ocultos, partidas internas, juegos vecinos, raíces y carpetas protegidas,
solapamientos, juegos anidados, rutas externas, enlaces simbólicos, cambios
entre revisión y borrado y fallos de permisos. No toca juegos del usuario.

La revisión manual en Android debe comprobar Cancelar en cada diálogo, la
segunda confirmación con mando y pantalla táctil, la casilla de partidas
externas y el refresco de biblioteca/tienda. Usar un juego de prueba con copia
de seguridad: el borrado es definitivo.
