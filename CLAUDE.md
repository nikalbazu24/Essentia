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


Continuación del 4 de noviembre: PR #5 renueva navegación y pantallas Android con `ui/AppShell`,
`ui/CompanionView` compartido entre teléfono/Thor y herramientas por juego agrupadas. Lee la
sección «Renovación Android» de `docs/2.0-beta.md`. GS Vitro sigue sin causa confirmada: nuevo
reporte manual de beta.61 sin traza; adjunto privado nuevo no transferido ni comparado. Se añade
registro local optativo de la decisión real de motor, fases y salidas de Android, sin cambiar la
política automática. `ExperienceTest` y `ui-review.yml` producen capturas Android revisables.

El dueño envió durante esta continuación la referencia visual de code.xr (Dashboard V15,
Instagram reel DezD0vhzvvL). La dirección aprobada pasa a lavanda claro, tarjetas blancas y
pastel y acciones oscuras; mantener los temas oscuros como opción. Repetir capturas y controles
de contraste después de este ajuste. El PR #5 ya está integrado; compilación y firma original verificadas. La validación física continúa pendiente.

Prioridad explícita del dueño: GS Vitro. La revisión del diagnóstico detectó que iniciar el
motor clásico borraba el error que había disparado el fallback. Se conserva ahora como
«Error del intento anterior», separado de la sesión actual, acotado y con la redacción de
credenciales existente. No equivale a identificar ni corregir el cierre del juego.


Continuación del 5 de noviembre — PR #8: superficies glass ligeras, resumen con notas
por juego y accesos anunciados por las APIs disponibles. En combate/transición se
desactivan los menús y se conserva su posición; juegos sin integración conservan
captura, turbo, notas y teclas. Misma vista en teléfono/Thor. Validación UI: 17 pruebas,
44 capturas Android; exploración, combate, cambio de juego y tema oscuro revisados.
Fixture Ruby original comprueba capacidades, bloqueos, combate clásico y protocolo.
Consulta la compilación y verificación del APK en el PR antes de dar una versión por publicada.

GS Vitro: la captura posterior al reporte manual SÍ aporta una traza: SystemStackError
(stack level too deep), Quest Markers → inicializadores del juego → widescreen.rb:409.
El dueño confirmó que Pantalla Original evita ese cierre inicial. No equivale a resolver
el juego: quedan música y efectos anteriores al cambiar escena, empeoramiento con el
tiempo, tirones y fondos de batalla negros/incompletos/solo cielo. Altavoces de Thor;
usó turbo apagado y ×3, sin comparación controlada desde un arranque nuevo a 1×.
La cadena de alias del modo panorámico es una hipótesis por reproducir. Audio y
composición no tienen causa demostrada; no se aplican parches de motor especulativos.


Revisión posterior de identidad, PR #9, autorizada por el dueño: beta.78 le resultó plana y
monótona, sin logo visible ni tour reconocible. Se recupera el frasco original con
recurso propio sin márgenes adaptativos para la interfaz; los iconos del launcher se
conservan. `LibraryTour` recorre controles reales, puede repetirse desde Biblioteca,
Guía y Ajustes, y registra visto solo al terminar/omitir explícitamente. Sustituye el
arranque que marcaba `whats_new_tour=5` antes de abrir una guía textual. La guía y las
novedades siguen disponibles. Identidad más intensa, contraste, iconos y carátulas de
reserva distintas por juego, sin inventar arte oficial. Revisar capturas antes de
integrar; mantener intactos juegos, partidas, motores y pendientes de GS Vitro.
