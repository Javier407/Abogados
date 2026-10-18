# 3. Roadmap por fases

Cada fase termina con algo que se puede usar y probar. No se empieza una fase sin cerrar la anterior.

| Fase | Objetivo | Entregables | Criterio de terminado |
|---|---|---|---|
| **F0. Planteamiento** | Ordenar el dominio y la arquitectura antes de programar | `docs/01-dominio.md`, `docs/02-arquitectura.md`, ADRs, base de datos de pruebas en `db/` | La base de pruebas carga sin errores y las consultas de ejemplo devuelven datos coherentes |
| **F1. Base** | Esqueleto de microservicios funcionando | Monorepo `servicios/`; `infra/docker-compose.yml` con PostgreSQL, RabbitMQ y gateway; `gabinete` migrado a Spring Boot 3 + Java 17 (`javax` → `jakarta`); `procesos` con CRUD de procesos y validación del radicado; Angular conectado al gateway | Crear un cliente, un abogado y un proceso desde Angular y verlos en la base |
| **F2. Términos y avisos** | Que no se venza ningún término | Cálculo de días hábiles con festivos y vacancia; calendario; tarea diaria que publica `termino.por_vencer`; `notificaciones` con correo e in-app | Un término que vence en 3 días genera un correo y una notificación en la app |
| **F3. Vigilancia** | Enterarse de los autos sin entrar al portal | `vigilancia` con la interfaz `FuenteRamaJudicial`; consulta programada; evento `actuacion.detectada`; notificaciones por Telegram | Un auto nuevo en un radicado vigilado llega por Telegram y queda registrado en el proceso |
| **F4. Asistente y administración** | Apoyo a la redacción y gestión de la firma | `asistente-ia` con Hermes (borradores y resúmenes); módulo de peritos; tarifas, honorarios pactados y pagos | Generar un borrador de contestación a partir de un proceso y registrar un pago de honorarios |
| **F5. Seguridad y calidad** | Listo para uso real | `identidad` con JWT y roles; contraseñas con BCrypt; CI con GitHub Actions (build y pruebas por servicio); nueva evaluación ISO/IEC 25010 | Ningún servicio responde sin token; el pipeline pasa en verde; Seguridad ≥ 7 en la evaluación |
| **F8. Administración y finanzas** | Que la firma se administre desde la app y los socios tengan información | Cuentas y roles desde la app (clave temporal que se cambia al entrar); anulación de pagos con motivo; liquidación de la cuota litis; gastos procesales; cartera; reportes de carga, términos y resultados con CSV | Crear una cuenta y que deba cambiar la clave al entrar; anular un pago y que quede en el historial sin contar; liquidar una cuota litis; registrar un gasto reembolsable y verlo en la cartera; reportes visibles solo para la dirección |
| **F7. Agenda y pruebas** | Que no se olvide una audiencia ni una entrega, y que los términos de cada auto no dependan de la memoria | Audiencias con hora y lugar en el calendario y aviso el día hábil anterior (`audiencia.proxima`); pruebas con perito y término de entrega; términos sugeridos al llegar una providencia | Una audiencia programada sale en el calendario y se avisa el día hábil anterior; una prueba pericial con perito y fecha de entrega crea su término; un auto que admite la demanda en un verbal sugiere contestar en 20 días y el término se crea con un clic |
| **F6. Expediente** | Que cada proceso tenga su historia completa y los datos estén respaldados | Partes con aviso de conflicto de intereses; actuaciones con línea de tiempo; providencia y sentencia manuales; recursos contra una providencia; otras instancias del radicado; copias de seguridad diarias | Desde la app: agregar la contraparte (con aviso si es cliente), registrar una actuación y una providencia y verlas en la línea de tiempo; existe un respaldo diario que se restaura sin errores |

## Estado

| Fase | Estado | Fecha |
|---|---|---|
| F0 | Terminada | 2026-10-15 |
| F1 | Terminada: criterio comprobado (cliente, abogado y proceso creados desde Angular y verificados en PostgreSQL; evento `proceso.creado` publicado) | 2026-10-15 |
| F2 | Terminada: un término que vence en 3 días hábiles generó el correo (Mailpit) y el aviso en la app, en vivo | 2026-10-15 |
| F3 | Terminada: un auto nuevo (fuente simulada) en el proceso 34 quedó registrado como providencia y llegó por Telegram (@javier_asistente_ia_bot), correo y app | 2026-10-16 |
| F4 | Terminada: borrador de contestación del proceso 13 generado desde la app con el modelo local (229 s) y pago de honorarios registrado desde la app | 2026-10-16 |
| F5 | Terminada: en el servidor las 13 rutas de la API responden 401 sin token (también con cabeceras falsas o token `alg none`); 403 por rol; bloqueo al 5.º fallo; pipeline en verde; Seguridad 8/10 en la [evaluación](04-evaluacion-iso25010.md) | 2026-10-16 |
| F6 | Terminada: en el servidor, contraparte con el NIT de un cliente → aviso de conflicto; "Constructora Altamira S.A.S." detectada contra el cliente "Constructora Altamira SAS"; memorial del dependiente y auto manual en la línea de tiempo; respaldo diario restaurado en una base temporal (31 procesos, 30 clientes, 19 términos, 16 usuarios) | 2026-10-17 |
| F7 | Terminada: en el proceso 2 (verbal, cliente demandado) un auto "admite la demanda" sugirió contestar en 20 días y el término se creó con un clic (vence 17/11/2026); una audiencia del lunes se avisó el domingo por la app, el correo y Telegram; una prueba pericial con entrega el 2/11 (festivo) creó su término al 30/10 y quedó cumplido al practicarla | 2026-10-18 |
| F8 | Terminada: en el servidor (23 comprobaciones), cuenta nueva con clave temporal que exige el cambio al entrar; el administrador no puede quitarse su rol; pago anulado que queda en el historial y deja de contar; cuota litis del 25 % sobre 40.000.000 liquidada en 10.000.000; gasto reembolsable registrado por un abogado y visible en la cartera; reportes 403 para abogados | 2026-10-18 |

## F1: qué quedó y qué falta

Hecho, en el orden sugerido:

1. `infra/docker-compose.yml` con PostgreSQL, RabbitMQ, los dos servicios, el gateway y el frontend.
2. Contratos en `docs/contratos/` (OpenAPI de `procesos` y `gabinete`; eventos).
3. `procesos`: CRUD, radicado validado (23 dígitos, despacho registrado, no repetido), análisis del radicado mientras se escribe, catálogos y evento `proceso.creado`.
4. `gabinete`: clientes y abogados en Spring Boot 3 + `jakarta` sobre el esquema `gabinete`.
5. `gateway` con las rutas `/api/*`.
6. Angular en `frontend/`: panel, procesos (lista con filtros y formulario), clientes y abogados. Pagos queda para F4.

Pendiente o decidido para después:

- El código del laboratorio (`src/`, MySQL) sigue en la raíz porque es la entrega del laboratorio #1. `servicios/gabinete` ya cubre su función. Moverlo o borrarlo lo decide Javier.
- Sin paginación en los listados: con ~30 procesos no hace falta. Se agrega cuando la firma pase de unos cientos.
- Si RabbitMQ está caído al crear un proceso, el evento se pierde (queda en el log). Para garantizar la entrega se puede usar una tabla *outbox* en F3, cuando `vigilancia` lo consuma.
- ~~Sin autenticación~~: resuelto en F5 (JWT y roles en el gateway).

## F2: qué quedó y qué falta

Hecho:

1. **Calendario judicial** en `procesos`:
   - Cuenta en días hábiles, sin fines de semana, festivos, Semana Santa ni vacancia del 20 de diciembre al 10 de enero.
   - Hace las mismas cuentas que las funciones SQL y se niega a calcular más allá de los festivos cargados (hoy, hasta enero de 2028).
   - Festivos 2027 en `db/migraciones/001-f2-terminos-y-avisos.sql`.
2. **Términos:**
   - Se registran por proceso con el vencimiento calculado: por defecto el responsable es el del proceso, la notificación no puede ser futura y no corren en procesos cerrados.
   - Se pueden marcar cumplidos.
   - Hay una agenda con su situación (vencido, por vencer, al día, cumplido).
3. **Revisión diaria** (7:00, lunes a viernes) que publica `termino.por_vencer` (5 días hábiles o menos) y `termino.vencido` (vencidos hace 3 días hábiles o menos). También se puede lanzar a mano.
4. **Servicio `notificaciones`** (Python):
   - Aviso en la app y correo, con el correo de la ficha del abogado si no tiene preferencia.
   - No repite el aviso en el mismo día y canal, y deja constancia de los correos fallidos.
   - Avisos en vivo por SSE y evento `notificacion.enviada`.
5. **Frontend:** agenda de términos, calendario mensual, avisos con contador en vivo, términos dentro de cada proceso y alerta en el panel.

Pendiente o decidido para después:

- **Correo:** en pruebas va a Mailpit (puerto 8092). El correo real se configura con `SMTP_*` en `.env` y no debe usarse con la base de pruebas.
- **Persona que usa la app:** se elige en "Ver como" (provisional). El aviso de un término es para su responsable; los de apoyo no reciben aviso aún.
- **Cálculo de términos:** los términos de referencia por tipo de proceso (`termino_contestacion_dias`) todavía no se proponen solos al crear el término; se escriben los días. Sigue pendiente validar los términos con un abogado.
- **Festivos de 2028:** antes de diciembre de 2027 hay que cargarlos con una nueva migración. El sistema avisa con un error si faltan.

## F3: qué quedó y qué falta

Hecho:

1. **Servicio `vigilancia`** (Python):
   - Interfaz `FuenteRamaJudicial` con fuente manual, simulada y portal real (ADR 0005).
   - Consulta programada cada 10 minutos para los radicados a los que les toca (por defecto, cada 24 horas).
   - Línea base sin avisos, detección sin repetir y alerta desde 3 fallos seguidos.
   - Primero publica y después marca como conocida, para no perder actuaciones si RabbitMQ falla.
2. **`procesos`:** registra `actuacion.detectada` como providencia (fuente `VIGILANCIA`, sin duplicar) y expone `GET /procesos/{id}/providencias`.
3. **`notificaciones`:**
   - Avisa las actuaciones al responsable del proceso.
   - Canal de Telegram con vinculación por código de un solo uso (24 h).
   - El bot usa *long polling*, sin exponer puertos.
   - El token nunca sale en los logs.
4. **Frontend:** pantalla de vigilancia (fuente por radicado, consultar ahora, historial y simulador), vinculación de Telegram en Avisos y actuaciones del juzgado en cada proceso.

Pendiente o decidido para después:

- **Bot de Telegram:** el token está en `infra/.env` del servidor (lo escribió Javier). Cada abogado se vincula desde **Avisos**.
- **Corregido en la prueba final:**
  - Los servicios Python ahora comprueban cada conexión del pool, porque tras reiniciar PostgreSQL usaban conexiones cerradas.
  - Los servicios Java recuerdan la IP de otro contenedor solo 5 segundos (antes 30), porque tras recrear contenedores el gateway llegó a enviar peticiones al servicio equivocado.
- **Términos de uso del portal:** revisarlos antes de vigilar radicados reales. Los radicados de prueba no deben usar la fuente del portal (ADR 0005).
- **Procesos creados antes de vigilancia:** se agregan solos al arrancar el servicio, como `MANUAL`.
- **Responsable para los avisos:** se toma de `procesos` en el momento del aviso. Si `procesos` no responde, el evento se reintenta.

## F4: qué quedó y qué falta

Hecho:

1. **`asistente-ia`** con Ollama local (`qwen2.5:3b`, ADR 0006):
   - Contestación de la demanda (solo si el cliente es demandado o accionado) y resumen del proceso para el cliente.
   - El encabezado y la firma los pone el sistema; el texto llega a la pantalla por partes.
   - Si no hay modelo, se entrega la plantilla.
2. **`gabinete`:**
   - Peritos.
   - Tarifas de referencia en pesos con el salario mínimo del año.
   - Acuerdos de honorarios: reglas por forma de cobro, máximo 50 %, uno por proceso y el proceso debe ser del mismo cliente.
   - Pagos: en FIJO no se puede pagar más que el saldo, y al completarlo el acuerdo pasa a CUMPLIDO.
3. **Frontend:** asistente en cada proceso, página de honorarios y pagos con tarifas, y página de peritos.

Pendiente o decidido para después:

- **Calidad del modelo:** con un modelo de 3B en CPU el borrador es un **esqueleto para editar**. Repite frases entre secciones, a veces supone hechos y cambia palabras del asunto ("contrato real" por "contrato realidad").
  - Tarda unos 4 minutos.
  - Para mejorar: un modelo más grande (más lento y más RAM), una GPU, o un proveedor externo con datos anonimizados (decisión de Javier, ADR 0006).
- **Pruebas periciales:** se registran en `procesos.pruebas` con `id_perito`, pero todavía no hay pantalla para asignar un perito a una prueba.
- **Pagos:** no se anulan desde la app. Un pago mal registrado se corrige en la base (pendiente: anulación con motivo).

## F5: qué quedó y qué falta

Hecho:

1. **`identidad`** (Spring Boot, ADR 0004):
   - Inicio de sesión con usuario o correo; contraseñas con BCrypt.
   - JWT RS256 de 8 h firmado con una llave que vive solo en el servidor (`infra/secretos/`, fuera de git); JWKS pública para el gateway.
   - 5 fallos seguidos bloquean la cuenta 15 min (423, desde el 5.º fallo); más de 100 intentos por IP en 10 min dan 429 (freno global: ver la brecha 7 de la evaluación). Mensaje de error genérico y tiempo de respuesta igual para usuarios que no existen.
   - Bitácora de ingresos (`identidad.ingresos`, migración 003) y cambio de contraseña con política (10+ caracteres, letras y números, sin el usuario ni el documento).
2. **`gateway`:**
   - Toda `/api/**` exige token salvo el login; tabla de permisos por rol (`PoliticaAcceso`); lo que no está en la tabla se niega.
   - Borra las cabeceras `X-Usuario-*` que mande el cliente y las pone desde el token.
   - Línea `AUDITORIA` por petición; Actuator solo expone `health`.
3. **`notificaciones`:** cada persona ve solo sus avisos (ADMIN, todos).
4. **Frontend:** pantalla de inicio de sesión, guardas por rol, menú filtrado, **Mi cuenta** (cambio de contraseña) y cierre de sesión. El selector "Ver como" desapareció.
5. **nginx:** CSP y cabeceras de seguridad, sin versión, IP real del cliente; la ruta de avisos en vivo (token en la URL) no se registra.
6. **CI** en GitHub Actions: cada servicio Java y Python, el frontend y la validación de `docker-compose.yml`. Los errores se publican como anotaciones.
7. **Evaluación ISO/IEC 25010** en [`04-evaluacion-iso25010.md`](04-evaluacion-iso25010.md): Seguridad 4 → 8; total 7,65 → 8,15.

**Corregido al desplegar:** el gateway respondía 500 en toda la API porque intentaba borrar cabeceras en la petición real de Netty, que son de solo lectura (las pruebas usaban peticiones simuladas, que sí se pueden modificar). Ahora arma una copia, y una prueba reproduce las cabeceras de solo lectura.

Pendiente o decidido para después (detalle en el plan de mejora de la evaluación):

- HTTPS, *refresh token* con revocación y validación del JWT en cada servicio: obligatorios antes de publicar fuera de la VPN.
- Administración de usuarios desde la app (hoy por SQL) y portal del cliente (el rol CLIENTE solo ve **Mi cuenta**).
- Análisis de dependencias y cobertura en CI.

## F6: qué quedó y qué falta

Hecho:

1. **Partes** (`/procesos/{id}/partes`): contraparte, terceros y llamados en garantía, con documento y apoderado. El cliente lo crea el proceso y no se edita ni se quita desde aquí.
2. **Conflicto de intereses:** al agregar o editar una parte se compara con todos los clientes de la firma, por documento exacto o por nombre normalizado (sin tildes, puntos ni mayúsculas). Es un **aviso**, no un bloqueo. Si gabinete no responde, la parte se guarda y se avisa que no se verificó.
3. **Actuaciones** (`/procesos/{id}/actuaciones`): poder, demanda, contestación, memorial, recurso, audiencia, alegatos, notificación.
   - Una fecha futura queda como programada.
   - Pueden **cumplir un término** del proceso.
   - DEMANDA, CONTESTACION y RECURSO mueven la etapa si es posterior a la actual (nunca retrocede).
   - Un RECURSO exige la providencia que se recurre.
   - Guardan quién las registró (del token). Los dependientes también pueden registrarlas (radican memoriales).
4. **Providencias manuales** (`POST /procesos/{id}/providencias`): auto o sentencia. La sentencia exige su sentido (favorable, desfavorable, parcial) y lleva el proceso a la etapa SENTENCIA.
5. **Línea de tiempo y otras instancias** (`/procesos/{id}/expediente`): actuaciones, providencias y términos en una sola lista. Las otras instancias son los procesos con los mismos 21 primeros dígitos del radicado.
6. **Pantalla del expediente** (`/procesos/{id}`): encabezado del proceso, línea de tiempo con filtros, formularios, partes, términos y asistente. La lista de procesos y el formulario de edición llevan a ella.
7. **Copias de seguridad:** contenedor `respaldo` con `pg_dump` diario a las 02:30 (y al arrancar si la última tiene más de un día), 14 días de historia en `infra/respaldos/` (fuera de git). `respaldar.sh probar` restaura la última copia en una base temporal y cuenta filas.
8. Migración `db/migraciones/004-f6-expediente.sql`. Pruebas: 14 nuevas del expediente y 3 de providencia manual. Contrato OpenAPI de `procesos` actualizado.

Pendiente o decidido para después:

- **Las copias viven en la misma VM.** Si se daña el disco del servidor se pierden con la base. Siguiente paso: copiarlas al host Windows o al PC (por ejemplo, `scp` programado) o a un almacenamiento externo.
- **Conflicto de intereses a escala:** se trae la lista completa de clientes. Sirve para cientos o pocos miles; más allá conviene una búsqueda normalizada en gabinete (con `unaccent`).
- **Documentos:** la actuación guarda un enlace (`https://…`), no el archivo. El expediente digital con almacenamiento propio queda para después.
- **Providencias:** no se editan ni se borran desde la app (para no alterar lo que dijo el juez). Una registrada por error se corrige en la base.
- Las audiencias se registran como actuaciones programadas, pero todavía no salen en el calendario ni generan aviso. Es lo primero de F7.

## F7: qué quedó y qué falta

Hecho:

1. **Audiencias:** actuación de tipo AUDIENCIA con **hora y lugar** (sala o enlace). Salen en el **calendario** (azul) y en `GET /audiencias`.
   - Cada mañana hábil, la misma revisión de los términos publica `audiencia.proxima` para las audiencias del **próximo día hábil**. Contempla los fines de semana y los festivos: el viernes avisa las del lunes, y antes de un festivo las del día siguiente.
   - `notificaciones` avisa por la app, el correo y Telegram, una vez por audiencia y fecha.
2. **Pruebas** (`/procesos/{id}/pruebas`): documental, pericial (con perito de gabinete), testimonial, etc.; estados SOLICITADA → DECRETADA/NEGADA → PRACTICADA.
   - Si está pendiente y tiene fecha de entrega, se crea un término que vence ese día, o el último día hábil antes si ese día no es hábil.
   - Practicarla cumple el término; negarla o borrarla lo quita; cambiar la fecha lo reemplaza.
3. **Términos sugeridos** (`/procesos/{id}/providencias/{idProv}/sugerencias`):
   - Reglas de referencia en `procesos.reglas_termino`: contestar (días del tipo de proceso), subsanar 5, excepciones 5, reposición 3, apelar sentencia 3 (CGP) o 10 (CPACA), impugnar tutela 3.
   - También se sugiere el plazo escrito en la propia providencia ("en 5 días").
   - Nada se crea solo: el abogado confirma con "Crear término". Al registrar una providencia, el expediente muestra sus sugerencias de inmediato.
4. Migración `005-f7-agenda-y-pruebas.sql`, que incluye el nuevo evento en la restricción de `notificaciones`. Pruebas: 9 de agenda y pruebas, 6 del sugeridor y 6 de avisos de audiencia.

Pendiente o decidido para después:

- **Las reglas están sin validar** (`validada = false`, y la app lo dice). Un abogado debe revisarlas antes de confiar en ellas. Laboral y penal no tienen reglas de recursos porque se interponen en audiencia.
- Las reglas se editan por SQL; no hay pantalla de administración.
- Una prueba no muestra su término en la lista de términos con un enlace de vuelta (se ve en "Términos del proceso" como "Entrega de prueba: …").
- **Red del servidor:** durante el despliegue la VM y el host Windows tenían la misma IP local (`192.168.1.41`), y la conexión se cortaba cada pocos minutos. Ver `servidor/BITACORA-REINICIOS.md`.

## F8: qué quedó y qué falta

Hecho:

1. **Cuentas desde la app** (`identidad` `/usuarios`, solo ADMIN; pantalla **Usuarios**): crear, cambiar datos y roles, activar o desactivar, restablecer la contraseña y desbloquear.
   - La contraseña temporal la escribe el administrador (cumple la política) y la persona debe cambiarla al entrar (`debe_cambiar_clave`).
   - Nadie puede quitarse a sí mismo el rol de administrador ni desactivar su cuenta, y siempre queda al menos un administrador activo.
   - El servicio comprueba el rol ADMIN aunque el gateway ya lo exija.
2. **Pagos anulables:** un pago no se borra. Se anula con motivo, fecha y autor; queda tachado en el historial y deja de contar. Si el acuerdo estaba cumplido, vuelve a vigente.
3. **Liquidación** de cuota litis, porcentaje de la cuantía o mixto: porcentaje × valor obtenido (más el fijo en MIXTO), redondeado al peso. Desde ahí el acuerdo tiene un total: los pagos no lo superan y al completarlo queda cumplido.
4. **Gastos procesales** (`/gastos`, tarjeta en el expediente):
   - Lo pagó la firma o el cliente; si la firma lo adelantó y es reembolsable, entra a la cartera hasta que se marque reembolsado.
   - Los abogados los registran; la administración marca reembolsos y borra.
5. **Cartera** (`/honorarios/cartera`): saldo de honorarios, gastos por reembolsar y días desde el último pago, por cliente.
6. **Reportes** (`/reportes/*`, solo ADMIN y SOCIO; pantalla **Reportes** con CSV para Excel): carga por abogado, cumplimiento de términos (a tiempo, tarde, vencidos) y resultados de las sentencias por rama.
   - Los términos guardan desde ahora la fecha en que se cumplen (`cumplido_en`); los cumplidos antes de F8 aparecen "sin fecha".
7. Migración `006-f8-administracion.sql`. Pruebas nuevas: 6 de cuentas, 9 de finanzas y 2 de reportes, más los permisos en el gateway.

Pendiente o decidido para después:

- **El cambio obligatorio de la clave temporal lo exige la interfaz**, no la API: con la clave temporal todavía se puede llamar a la API directamente hasta cambiarla. Para cerrarlo, `identidad` podría emitir un token limitado a `/auth/clave`.
- La cuenta no se enlaza todavía con la ficha del abogado en gabinete: se usa el mismo documento por convención.
- Facturación electrónica (DIAN) y PDF de los reportes.
