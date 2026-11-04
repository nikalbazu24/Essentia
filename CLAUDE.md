# Contexto compartido de Essentia

GitHub es la fuente compartida del código. Lee `docs/2.0-beta.md` antes de proponer cambios.
La entrega actual es **2.0 beta para Android (teléfono y AYN Thor)**. El dueño pospuso PC.

- Conserva las partidas y los ajustes existentes. Borrar juegos y sus archivos requiere doble confirmación.
- Las instalaciones se preparan antes de reemplazar archivos; conserva el diario de recuperación si falla una reversión.
- Ambos motores, moderno y clásico, son necesarios. `tools/verify_apk.py` impide publicar un APK incompleto.
- La versión del actualizador sale de los metadatos del APK, no de otra fórmula copiada en el flujo.
- La traducción IA empieza desactivada y necesita elección explícita y clave del usuario. No crees APIs, claves ni gasto por tu cuenta.
- Los reportes y adjuntos de Google son privados. El lector comunitario solo muestra aportes aprobados y campos de juego.
- Los scripts de juegos aportados para diagnóstico son material privado de trabajo: no los publiques ni los incluyas en pruebas del repositorio.
- Las correcciones sintéticas no sustituyen pruebas en la Thor. No asegures porcentajes de rendimiento ni compatibilidad total sin medir.

La compilación en `.github/workflows/build.yml` comprueba catálogo, servicio, Ruby, pruebas JVM/UI,
ambos motores, contenido del APK y firma antes de publicar en `dev`. `main` es la rama de distribución.
La bitácora privada de Claude no se sincroniza automáticamente; actualiza este registro compartido al cambiar el proyecto.
