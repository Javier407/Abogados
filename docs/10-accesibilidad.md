# Accesibilidad con Axe (fase F16)

## Qué es Axe

**Axe DevTools** es la extensión de navegador de **Deque Systems** para auditar la accesibilidad de una página contra las pautas **WCAG 2.1** (niveles A y AA). Su motor, **axe-core**, es de código abierto y es el mismo que usan Lighthouse, la extensión y las librerías de pruebas. Por eso lo que la extensión encuentra a mano, una prueba lo puede vigilar en el CI.

Para cada página, Axe revisa reglas como:
- contraste del texto;
- etiquetas en los campos;
- nombre accesible en los botones;
- texto alternativo en las imágenes;
- uso correcto de los roles ARIA.

Clasifica cada violación por impacto: *minor*, *moderate*, *serious* o *critical*.

Una herramienta automática encuentra cerca de la mitad de los problemas de accesibilidad. El resto (orden lógico del teclado, textos que se entiendan, experiencia real con lector de pantalla) necesita revisión humana. Por eso esta fase se complementa con las pruebas con usuarios (`docs/11`).

## Cómo lo aplicamos

1. **Auditoría de la app desplegada.**
   - Se cargó axe-core 4.13.0 en Edge sin ventana sobre la app real, igual que lo hace la extensión, pantalla por pantalla y con sesión iniciada como abogado.
   - Etiquetas evaluadas: `wcag2a`, `wcag2aa`, `wcag21a` y `wcag21aa`.
2. **Corrección** de lo encontrado.
3. **Prueba automática** (`frontend/src/app/a11y.spec.ts`), que corre con `ng test` en cada *push* del CI:
   - monta el inicio de sesión, el asistente de borradores y el calendario;
   - falla si axe-core encuentra violaciones *serious* o *critical*;
   - el contraste no se mide aquí, porque jsdom no pinta la página; ese queda para la auditoría en el navegador;
   - incluye un control: comprueba que axe sí detecta un campo sin etiqueta y una imagen sin `alt`, para que la prueba no pase en verde por error;
   - se verificó que falla con el calendario anterior a la corrección.

## Hallazgos y correcciones

**Antes** (7 pantallas auditadas):

| Pantalla | Violaciones |
|---|---|
| Inicio de sesión | ninguna |
| Inicio | contraste (2) |
| Procesos | contraste (14) |
| Términos | contraste (5) |
| Clientes | contraste (30) |
| Facturación | ninguna |
| Calendario | estructura ARIA (`aria-required-children` 1, `aria-required-parent` 49), contraste (8) |

| Causa | Detalle | Corrección |
|---|---|---|
| Gris secundario `--muted` `#6b7584` | 4,0:1 sobre los chips y filas grises, 4,1:1 sobre el fondo de alerta. Debe ser 4,5:1 o más | `#5b6472`: 5,2:1 sobre gris y 6,0:1 sobre blanco |
| Ámbar `--warn` `#9a6408` | 4,4:1 sobre su fondo suave (estado "por vencer") | `#855505`: 5,6:1 |
| Verde `--ok` `#2f7a4f` | 4,5:1 justo en el límite sobre gris | `#2a6e47`: 5,3:1 |
| Días fuera del mes en el calendario, `#b3bcc8` | 1,9:1 | Gris secundario: 6,0:1 |
| Calendario con `role="grid"` | Las celdas no estaban dentro de filas: un lector de pantalla anunciaba una tabla rota | `role="group"` con nombre del mes. Cada día ya es un botón con su fecha y lo que tiene. Los encabezados visuales quedan ocultos al lector |

Los cambios de color son de tono, no de diseño: la paleta se ve igual, solo más legible.

**Después:** **0 violaciones** en las 11 pantallas auditadas:
- inicio de sesión;
- inicio;
- procesos;
- términos;
- clientes;
- facturación;
- calendario;
- consultas;
- honorarios;
- vigilancia;
- reportes.

Algunas violaciones venían de elementos que el propio Edge inyecta en la página, con clases `ait-*`. No son de la app y se excluyeron de la auditoría.

## Cómo repetirla a mano

Con la extensión **axe DevTools** en Chrome o Edge:
1. Abrir cada pantalla.
2. Ir a DevTools → pestaña *axe DevTools* → *Scan all of my page*.
3. Filtrar por *Critical* y *Serious*.

## Pendiente

- Recorrer el flujo completo solo con teclado y con un lector de pantalla (NVDA): `docs/11`.
- Revisar el modo oscuro, si se agrega.
