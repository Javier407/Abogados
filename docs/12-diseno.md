# Diseño de la interfaz: Lexora (rama `feature/diseno`)

## Por qué

El diseño anterior tenía varios problemas:
- **Menú:** 17 opciones seguidas en una barra de 96 px, con etiquetas de 9,5 px y sin grupos.
- **Letra:** tres familias distintas.
- **Pantallas:** algunas con secciones muy separadas, y un orden que no seguía el trabajo del día.

El rediseño sigue los criterios que pidió Javier:

| Fuente | Qué se tomó |
|---|---|
| Tailwind CSS | Utilidades y tokens |
| Lucide | Iconos |
| shadcn/ui | Componentes accesibles; en Angular, **spartan-ng** |
| Motion | Animaciones moderadas |
| Uiverse | Inspiración para el código animado |
| Referencias | GeneX, Dashboard V14, School y Glassy Sidebar; regla 60-30-10 |

## Marca

- **Nombre:** **Lexora**, de *lex* (ley) y un sufijo de producto. "Rodríguez & Asociados" queda como la firma que lo usa.
- **Logo:** un cuadro índigo con una **L** que sostiene una **X**, el cruce de la ley con el plazo, y un punto que marca el vencimiento. Está en `frontend/src/app/core/logo.component.ts`, en dos variantes:
  - `completo`, con el nombre;
  - `icono`, para el menú contraído.
- **Favicon:** `frontend/public/favicon.svg`.

## Sistema visual (`frontend/src/styles.css`)

**Regla 60-30-10:**

| Uso | Claro | Oscuro | Contraste |
|---|---|---|---|
| 60 %: fondo y tarjetas | `#F5F6FA` / `#FFFFFF` | `#0A0F1E` / `#121A2E` | — |
| 30 %: marino, textos y títulos | `#0F1E45` | `#E8ECF7` | 16:1 / 14:1 |
| Texto secundario | `#5A6478` | `#9AA4BC` | 5,9:1 / 7:1 |
| 10 %: índigo, acción principal y lo activo | `#4338FF`, texto blanco | `#5B50FF`, texto blanco | 6,5:1 / 5,2:1 |
| Enlaces sobre fondo | `#3B30E6` | `#ABA5FF` | 7,5:1 o más |
| Borde de campos | `#8C95A8` | `#56638A` | 3:1 (WCAG 1.4.11) |

- **Letra:** Plus Jakarta Sans, servida por la app porque la CSP no permite Google Fonts.
  - **Escala:** 14 px el cuerpo, 18 px los subtítulos, 26 a 30 px los títulos.
  - **Números tabulares:** las cifras de las tablas quedan alineadas.
- **Formas:** radios de 10, 12, 16 y 24 px, bordes de 1 px y sombras suaves.
- **Modo oscuro:**
  - **Cómo cambiarlo:** con el botón de luna de la barra o desde el menú del usuario.
  - **Valor inicial:** el del sistema operativo; la elección se recuerda en el navegador (`TemaService`).
- **Animaciones:**
  - **Qué hay:** cifras que suben, listas que entran en cascada, el código de verificación y los anillos del login.
  - **Movimiento reducido:** todas respetan la opción "reducir movimiento" del sistema.
- **Compatibilidad:** las clases globales (`.btn`, `.card`, `.tabla`, `.badge`, `.form-*`) se reescribieron con los tokens nuevos, y los nombres de variables viejos quedan como alias. Por eso las 32 pantallas cambiaron sin reescribirlas una por una.

## Orden y navegación

**Menú lateral blanco, agrupado por tipo de trabajo:**

| Grupo | Opciones |
|---|---|
| Diario | Inicio · Agenda (términos y calendario en pestañas) · Procesos · Vigilancia |
| Clientes | Clientes · Consultas |
| Honorarios | Honorarios y pagos (la facturación se retiró en nov. 2026) |
| La firma (plegable) | Abogados (con ficha: sus procesos y su rango de respuesta) · Peritos · Reportes · Datos de la firma |
| Administración (plegable) | Usuarios · Servidor |

- **Escritorio:** el menú se contrae a iconos y lo recuerda.
- **Celular:** se abre como panel lateral.
- **Permisos:** cada rol sigue viendo solo lo suyo.
- **Barra superior:**
  - migas de pan;
  - búsqueda global;
  - tema claro u oscuro;
  - campana de avisos con contador;
  - menú del usuario, con Mi cuenta, Tema y Cerrar sesión.
- **Portal del cliente:**
  - **Sin menú lateral:** las tres opciones van en la barra superior.
  - **Lectura:** la letra es más grande y el contenido más angosto.
  - **Celular:** está pensado primero para esa pantalla.
- **Encabezado de página:** el título y la descripción van a la izquierda y las acciones a la derecha. Entre bloques hay un espacio uniforme de 20 px.

## Flujos encadenados

| Flujo | Cómo se recorre |
|---|---|
| **Inicio "Mi día"** | Saludo y lo urgente de quien entra: sus términos (los vencidos primero), sus audiencias de los próximos 14 días (o las de la firma), los avisos sin leer y los radicados vigilados con fallas. Cada tarjeta lleva al detalle. Socios y administradores ven además "La firma" |
| **Búsqueda Ctrl+K** | Busca procesos por radicado o asunto, clientes por nombre o documento, y ofrece atajos según el rol. Con Enter se abre lo elegido |
| **Proceso de punta a punta** | La cabecera muestra la etapa como barra de pasos y las acciones: registrar gasto y redactar con IA. En cada providencia: términos sugeridos → borrador del recurso |
| **Vigilancia → término** | El panel del radicado muestra sus providencias recientes, con las detectadas marcadas, y crea el término sugerido ahí mismo |
| **Cliente → proceso** | Ficha lateral con el contacto y los procesos. Desde ahí: "Nuevo proceso", con el cliente ya puesto |

**Enlaces profundos:**

| Enlace | Qué abre |
|---|---|
| `/procesos/:id?pestana=asistente` | El expediente en esa pestaña |
| `/clientes?id=N` | La ficha del cliente |
| `/clientes?nuevo=1` | El formulario de cliente nuevo |
| `/procesos/nuevo?idCliente=N` | El proceso nuevo con ese cliente |

## Verificación (2026-11-06)

- **Pruebas:** 25 en verde, entre ellas 9 de accesibilidad (se sumó el Inicio) y 4 del código de verificación. `ng build` termina sin avisos.
- **Peso de la carga inicial:** bajó de **703 kB a 608 kB**. El marco y todas las páginas internas se descargan aparte, así el login carga más liviano.
- **Auditoría axe-core 4.10 en el navegador, en claro y oscuro:** 0 violaciones graves en todas las pantallas:

| Rol | Pantallas |
|---|---|
| Dependiente | Inicio, Agenda (términos y calendario), Procesos, Expediente (todas sus pestañas), Vigilancia, Clientes y ficha, Consultas, Abogados, Peritos, Reglas, Avisos, Cuenta, búsqueda Ctrl+K |
| Cliente | Portal: mis procesos, detalle, pagos, avisos y cuenta |
| Administrador | Inicio con "La firma", Honorarios, Facturación (lista y nueva), Reportes, Datos de la firma, Usuarios, Servidor, Nuevo proceso, pestañas de gastos y asistente |

- **Correcciones encontradas en la auditoría:**
  - La paleta de spartan dejaba `aria-activedescendant` apuntando a una opción borrada, y un `listbox` vacío.
  - En Reportes, una barra con `aria-label` no tenía rol.
  - En Servidor, una lista con desplazamiento no se podía enfocar con el teclado.
- **Flujos probados a mano:**
  - Ctrl+K → proceso;
  - providencia → borrador del recurso;
  - expediente → factura ya llena;
  - cliente → nuevo proceso con el cliente puesto;
  - menú del celular y del usuario.

## Dependencias y vulnerabilidades (`npm audit`, 2026-11-06)

| Momento | Total | Críticas | Altas | Moderadas | Bajas | En lo que se ejecuta |
|---|---|---|---|---|---|---|
| Antes del rediseño (F17) | 43 | 3 | 18 | 20 | 2 | — |
| Al instalar spartan | 51 | 3 | 14 | 32 | 2 | 1 (`fast-uri`) |
| **Después de la revisión** | **16** | **0** | **0** | **16** | **0** | **0** |

**Qué se hizo:**
- **Parches de Angular:** el framework pasó a 21.2.26 y las herramientas (`@angular/build`, `@angular/cli`) a 21.2.29; `vitest` a 4.1.12. Con eso se corrigen `piscina`, `vite`, `undici`, `esbuild`, `immutable` y otras.
- **Se quitó `@spartan-ng/cli`:** solo servía para generar los componentes, que ya están en `src/app/ui/helm`, y traía `nx`, `zod` y `ts-morph` vulnerables. Para generar otro se usa una vez y sin instalar: `npx @spartan-ng/cli@1.9.0 ...` (o `ng g` tras instalarlo temporalmente).
- **`package-lock.json` regenerado:** npm no podía resolver las versiones encima del árbol anterior.

**Lo que queda.** Son 16 moderadas, todas dentro de `@angular/cli`, en la cadena que descarga paquetes (`pacote`, `sigstore`, `glob`, `minimatch`):
- **Cuándo se usan:** solo al instalar paquetes con `ng add` o `ng update` en el PC del desarrollador. No llegan al navegador ni a la imagen que se publica, que es nginx con los archivos ya compilados.
- **Cómo se quitan:** con Angular 22, una versión mayor. Conviene hacerlo en una rama aparte, con sus propias pruebas.

**Comprobación:**
- `npm audit --omit=dev` encuentra 0 vulnerabilidades.
- `ng build` y las 25 pruebas pasan.
- La imagen Docker se construye en la VM y está desplegada.

## Migración a Angular 22 (rama `chore/angular-22`, 2026-11-06)

| | Antes | Después |
|---|---|---|
| Angular | 21.2.26 | 22.2.5 (CLI y build 22.2.6) |
| TypeScript | 5.9 | 6.0 |
| `@angular/cdk` / `@ng-icons` | 21.2 / 33 | 22.2 / 36.1 |
| Node (Dockerfile y CI) | 22.12.0 | 22.22.3 (Angular 22 pide ^22.22.3) |
| `npm audit` | 16 moderadas | **0** |

**Qué cambió en el código:** lo hizo `ng update`, con sus migraciones automáticas:
- **Detección de cambios:** cada componente lleva `changeDetection: ChangeDetectionStrategy.Eager`. En Angular 22 el valor por defecto pasa a OnPush, y la migración conserva el comportamiento de antes. Pasar componentes a OnPush queda como mejora aparte, de a uno y con pruebas.
- **`HttpClient`:** `provideHttpClient(withXhr(), …)` conserva el backend XHR. En v22 el predeterminado pasa a ser `fetch`.
- **Otros:** ajustes de `tsconfig` y formato con Prettier de los archivos migrados. Por eso el diff es grande aunque los cambios de fondo son pocos.

**Encontrado al probar la migración** (no venía de Angular 22, pero se corrigió en esta rama):
- **Cifras del Inicio:** podían quedar en 0 si el navegador no ejecuta cuadros de animación, por ejemplo en una pestaña en segundo plano. Ahora el valor final queda fijado igual.
- **Tablas anchas:**
  - **Problema:** en pantallas angostas, una tabla que desborda y no tiene botones no se podía desplazar con el teclado (axe `scrollable-region-focusable`).
  - **Corrección:** `core/tablas-desplazables.ts` vuelve enfocable el contenedor solo mientras desborde, con `role="region"` y el título cercano como nombre.

**Verificación:**
- `ng build` sin avisos (632 kB) y 27 pruebas en verde.
- Imagen Docker construida en la VM.
- Recorrido en el navegador con Angular 22.2.5: login, menús, Ctrl+K, expediente y pestañas.
- axe sin violaciones graves en 15 pantallas a 1366 px y en 11 a 375 px.
