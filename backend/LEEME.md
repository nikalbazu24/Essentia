# Base de datos de Essentia (Google Apps Script)

La base de datos de Essentia es una pequeña aplicación de Google que vive en
tu propio Google Drive. Guarda:

- **Reportes de errores** (hoja «Essentia · Reportes», con los scripts adjuntos)
- **Traducciones** compartidas de cada juego (carpeta «Traducciones»)
- **Respaldos de partidas** (carpeta «Respaldos», protegidos con una clave)

Todo queda en la carpeta **«Essentia · Base de datos»** de tu Drive.

## Instalación (una vez, ~5 minutos, mejor desde un computador)

1. Entra a <https://script.google.com> con tu cuenta de Google y toca **Nuevo proyecto**.
2. Borra lo que trae y pega todo el contenido de `Code.gs`. Ponle de nombre «Essentia».
3. Arriba, elige la función **setup** y toca **Ejecutar**. Google te pedirá permisos
   (Drive y Hojas de cálculo): acéptalos. En el registro aparece la **clave de respaldo**: anótala.
4. Toca **Implementar → Nueva implementación**, tipo **Aplicación web**:
   - Ejecutar como: **Yo**
   - Quién tiene acceso: **Cualquier persona**
5. Toca **Implementar** y copia la **URL de la aplicación web** (termina en `/exec`).
6. En Essentia: **Ajustes → Base de datos y nube**, pega la URL y la clave, y toca **Probar conexión**.

Si cambias el código más adelante: **Implementar → Gestionar implementaciones → editar →
Versión nueva** (la URL no cambia).
