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
| **F14. Facturación lista para la DIAN** | Facturar honorarios y reembolsos con el flujo de la factura electrónica, sin depender todavía de un proveedor | Facturas con numeración de la resolución, IVA y retención configurables (validados por el contador), reembolsos sin IVA, CUFE, nota crédito para anular, representación gráfica con QR, proveedor SIMULADO detrás de una interfaz | Una factura toma el siguiente número sin saltos y su CUFE coincide con la fórmula del Anexo Técnico; un gasto no se cobra dos veces; anularla emite una nota crédito y libera el gasto |
| **F13. Verificación en dos pasos** | Que una contraseña robada no baste para entrar | Código TOTP de una app autenticadora (activarlo, entrar, códigos de recuperación, reinicio por el administrador), roles que la exigen, secretos cifrados; identidad revisa los tokens revocados; el bloqueo por intentos no cierra sesiones abiertas | Con la verificación activa, la contraseña sola no da token; un código no sirve dos veces; 5 códigos malos bloquean la cuenta; un rol que la exige debe activarla para entrar |
| **F12. Reglas validadas y calidad** | Que las sugerencias de términos tengan respaldo de un abogado y que el código esté probado | Reglas de términos editables, validadas por un socio, con historial; días de contestación editables; historial del vigía con pantalla "Servidor"; cobertura de pruebas ≥ 70 % en todos los servicios | Un socio valida una regla y queda a su nombre; cambiarla la deja sin validar; un contenedor detenido queda como incidente con su duración; el CI exige la cobertura alcanzada |
| **F11. Operación** | Operar con tranquilidad: enterarse de los problemas antes que los usuarios y que la calidad no retroceda | Vigía del servidor con avisos por Telegram; Dependabot, auditoría de dependencias y cobertura mínima en el CI; abogados de apoyo; sedes, cargos y áreas editables; reportes en PDF | Un contenedor detenido llega como aviso por Telegram y su recuperación también; el CI falla si baja la cobertura o aparece una vulnerabilidad conocida; un abogado de apoyo ve el proceso en sus procesos; la dirección crea una sede sin romper la regla de una sola principal |
| **F10. Documentos, redacción y consultas** | Que el expediente tenga sus archivos, que el asistente redacte demandas y recursos, que la firma registre a quien consulta y que los respaldos no dependan del servidor | Expediente digital con permisos (el cliente ve lo que se marque visible); borradores de demanda y recurso; consulta inicial con conflicto de intereses y paso a cliente; copia diaria de base y documentos al PC | Un PDF subido se descarga idéntico y uno falso se rechaza; el cliente solo descarga lo visible de sus procesos; una consulta contra un cliente avisa el conflicto y otra se convierte en cliente; los respaldos llegan al PC con su SHA-256 verificado |
| **F9. Portal del cliente y endurecimiento** | Que el cliente vea lo suyo sin llamar a la firma, y que la app aguante datos reales | Portal (mis procesos, novedades, resumen publicado, pagos) y avisos al cliente; HTTPS con `tailscale serve`; token de 15 min con refresh token rotatorio y revocable; contraseña temporal exigida por la API; cada servicio verifica el token; outbox en `procesos` y `vigilancia` | Un cliente ve solo sus procesos y pagos; al publicar el abogado un resumen con RabbitMQ caído, el aviso le llega cuando RabbitMQ vuelve; un token revocado deja de servir; ningún servicio responde sin token aunque se le llame desde la red interna |
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
| F9 | Terminada: en el servidor (39 comprobaciones, repetidas por HTTPS el 2026-10-22 en https://docker-server.tail76e9a3.ts.net), Carolina Méndez ve solo su proceso y sus pagos (otro proceso responde 404); con RabbitMQ detenido el abogado publicó un resumen, el evento esperó en el outbox y el aviso le llegó al volver RabbitMQ; un refresh token reutilizado cerró la sesión y su token de acceso dejó de servir en 20 s; los 6 servicios responden 401 sin token desde la red interna aunque se envíen cabeceras falsas; con clave temporal la API niega todo menos cambiarla | 2026-10-18 |
| F10 | Terminada: en el servidor (27 comprobaciones por HTTPS), un PDF subido se descargó con el mismo SHA-256 y uno falso, un HTML y uno de 20,5 MB se rechazaron; Carolina Méndez solo descargó el documento marcado visible y otro cliente recibió 404; una consulta contra "CONSTRUCTORA ALTAMIRA S.A.S." avisó el conflicto con el cliente 2 y otra se convirtió en cliente; el recurso citó la providencia elegida; la tarea del PC copió 8 respaldos verificando su SHA-256 | 2026-10-22 |
| F11 | Terminada: en el servidor (24 comprobaciones por HTTPS), al detener Mailpit llegó el aviso por Telegram y al encenderlo el de "resuelto"; Laura Pineda agregada como apoyo vio el proceso en sus procesos; marcar una sede nueva como principal dejó una sola principal; la auditoría encontró y se corrigieron vulnerabilidades en Angular, Starlette y PyJWT | 2026-10-22 |
| F12 | Terminada: en el servidor (20 comprobaciones por HTTPS), un socio creó, validó, editó y desactivó una regla y el historial lo contó todo; cambiar los días de contestación del verbal dejó sin validar la regla de contestar; al detener Mailpit quedó un incidente abierto y al encenderlo se cerró con su duración; cobertura de líneas: procesos 85 %, gabinete 85 %, identidad 78 %, vigilancia 99 %, notificaciones 74 % | 2026-10-22 |
| F13 | Terminada: en el servidor (30 comprobaciones por HTTPS), Laura activó la verificación con un código calculado como lo hace la app; después la contraseña sola ya no dio token; el mismo código y el mismo código de recuperación no sirvieron dos veces; 5 códigos malos bloquearon la cuenta sin cerrar su sesión abierta; el reinicio del administrador sí la cerró; con el rol DEPENDIENTE exigido, Pedro tuvo que activarla para entrar y no pudo desactivarla | 2026-10-22 |
| F14 | Terminada: en el servidor (24 comprobaciones por HTTPS), se facturaron a Constructora Altamira honorarios con IVA y un gasto reembolsable sin IVA: total 1.310.000, retención 11 %, a recibir 1.200.000; número SETP990000000 y CUFE igual al calculado aparte con la fórmula de la DIAN; el mismo gasto no se pudo cobrar dos veces; la anulación emitió la nota NC1 y el gasto volvió a quedar pendiente | 2026-10-22 |

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

## F9: qué quedó y qué falta

Hecho:

1. **Portal del cliente** (rol CLIENTE; cuenta enlazada con su ficha en **Usuarios**):
   - **Mis procesos:** etapa, estado, abogado responsable, próxima audiencia y fecha de la última novedad.
   - **Detalle:** el resumen en lenguaje sencillo que publicó su abogado, las próximas audiencias y las novedades (actuaciones de la firma y decisiones del juzgado). No ve los términos (agenda interna) ni procesos de otros clientes (responde "no existe").
   - **Mis pagos:** acuerdos de honorarios con lo pagado y el saldo, pagos (sin los anulados) y gastos por reembolsar.
   - El cliente del token sale del token (`idCliente`), nunca de la petición.
2. **Resumen para el cliente:** el abogado lo genera con el asistente (o lo escribe), lo corrige y lo publica desde el expediente. Queda guardado (`procesos.resumenes_cliente`) y se avisa al cliente.
3. **Avisos al cliente:** cuando se publica un resumen (`portal.resumen_publicado`) y el día hábil antes de una audiencia, en la app y al correo de su cuenta, en palabras simples y con enlace al portal.
4. **Sesiones (prioridad 2 de la evaluación):**
   - Token de acceso de 15 minutos con `jti`; refresh token de 12 h en cookie HttpOnly, SameSite=Strict, solo para `/api/auth` (Secure con HTTPS). La sesión dura máximo 7 días.
   - Cada renovación gasta el refresh token y entrega otro. Si uno gastado vuelve a llegar (copiado), se cierra la sesión completa.
   - Cerrar sesión, cambiar la contraseña, desactivar la cuenta, cambiarle los roles o restablecer su clave cortan las sesiones abiertas: el gateway rechaza esos tokens en máximo 15 s (lista de revocados).
   - Del refresh token solo se guarda el SHA-256. `renovar` y `salir` exigen `X-Requested-With` (CSRF).
   - La interfaz renueva sola un minuto antes de vencer; al recargar o abrir otra pestaña recupera la sesión con la cookie.
5. **Contraseña temporal exigida por la API** (pendiente de F8): el token lleva `clave_temporal` y el gateway y los servicios solo permiten ver el perfil y cambiarla.
6. **Cada servicio verifica el token (prioridad 3):** los 6 servicios validan firma, emisor y vencimiento con la llave pública de identidad, y toman la identidad del token (las cabeceras `X-Usuario-*` que lleguen se descartan). Para llamarse entre ellos usan un token de servicio (rol SERVICIO, solo lectura) que identidad entrega a cambio de una clave por servicio (`infra/.env`). El gateway rechaza los tokens de servicio que lleguen de fuera.
7. **Outbox (prioridad 6):** `procesos` escribe sus eventos en `procesos.outbox` en la misma transacción; `vigilancia`, las actuaciones detectadas en `vigilancia.outbox`. Un envío con confirmación del broker los publica cada 2 s. Ver `contratos/eventos.md`.
8. **HTTPS (prioridad 1), activo desde el 2026-10-22:** `tailscale serve` publica https://docker-server.tail76e9a3.ts.net (solo dentro de la tailnet) con certificado de Tailscale hacia la entrada local del frontend (127.0.0.1:8093). http://IP:8090 redirige a HTTPS, se envía HSTS y la cookie de sesión es `Secure`. El gateway ya no se publica en la VPN (solo 127.0.0.1:8091).
   - **IP real del cliente:** por la entrada de `tailscale serve`, nginx toma la última IP de `X-Forwarded-For` (la que agrega Tailscale; la que escriba el cliente se ignora). La bitácora de ingresos y el límite por IP ven al equipo real.
9. Migración `007-f9-portal-y-sesiones.sql`. Pruebas nuevas: sesiones (9), cuentas de cliente (2), filtro del token (5 por servicio Java y 4 por servicio Python), portal y outbox en procesos (6), avisos al cliente (5), gateway (3) y frontend (5).

Pendiente o decidido para después:

- El límite de intentos de ingreso por IP sigue en 100 (se subió cuando todas las conexiones llegaban con la misma IP); con la IP real se puede bajar.
- El cliente no puede cambiar por dónde recibe los avisos (siempre app y correo de su cuenta).
- Las copias de seguridad siguen en la misma VM.
- Facturación electrónica, expediente digital (archivos) y los borradores de demanda y recurso.

## F10: qué quedó y qué falta

Hecho:

1. **Expediente digital** (`procesos`, tarjeta "Documentos" del expediente):
   - PDF, Word, Excel, imágenes y texto de hasta 20 MB. El tipo se comprueba por el contenido (un .pdf que no empieza por `%PDF` se rechaza); no se aceptan HTML, SVG ni ejecutables.
   - Se guarda en el volumen `documentos` con un nombre aleatorio y su SHA-256; los datos en `procesos.documentos`. Si falla guardar los datos, el archivo se borra.
   - Abogados y dependientes suben; abogados cambian y borran. Las descargas van como adjunto, con `nosniff` y sin caché. Cada subida, descarga y borrado queda en el log con quién lo hizo.
   - **Portal:** el cliente ve y descarga solo los documentos marcados "visible para el cliente", y solo de sus procesos.
   - nginx acepta archivos grandes solo en la ruta de subida (el resto de la API sigue en 1 MB).
2. **Borradores de demanda y recurso** (asistente):
   - Demanda: solo si el cliente es demandante, accionante o víctima, y con los hechos y pretensiones que escribe el abogado (van al modelo como datos; no inventa hechos). El encabezado pone demandante y demandado desde las partes del expediente.
   - Recurso: contra una providencia del proceso que se elige; el encabezado la cita y la plantilla trae sus secciones.
   - Ningún escrito (salvo el resumen) sobre un proceso cerrado.
3. **Consulta inicial** (`gabinete`, pantalla "Consultas"): quién consulta, contraparte, rama, asunto y quién atiende. Avisa el conflicto de intereses si la contraparte ya es cliente (por documento o por nombre, sin importar tildes ni puntuación). "Contrató" crea la ficha de cliente (o enlaza la existente); "No contrató" y "Remitida" piden motivo.
4. **Copias fuera de la VM:**
   - El contenedor `respaldo` empaqueta también los documentos cada día (`documentos-*.tar.gz`) y deja los archivos a nombre del usuario de la VM.
   - En el PC, la tarea programada **"Abogados - copia de respaldos"** (todos los días a las 9:00 y al iniciar sesión; si el PC estaba apagado, al encenderlo) ejecuta `infra/respaldo/copiar-al-pc.ps1`. El script baja lo nuevo a `D:\respaldos\abogados`, verifica el SHA-256 contra el servidor, guarda 30 días y deja `copias.log`.
5. Migración `008-f10-documentos-y-consultas.sql`. Pruebas nuevas: documentos (6 + 1), consultas (5), asistente (5) y permisos del gateway (10).

Pendiente o decidido para después:

- **Resumen del auto en el aviso:** se decidió no hacerlo por ahora. Las anotaciones del portal de la Rama Judicial son cortas, y el modelo local tarda minutos por texto en CPU. El aviso ya trae la anotación completa.
- Los documentos no se revisan con antivirus: solo se aceptan tipos de archivo pasivos, comprobados por su contenido.
- Las copias en el PC no van cifradas (el disco es del PC de Javier). Si el PC se comparte, conviene cifrarlas.
- Si el PC no se enciende en varios días, la copia en el PC se atrasa (en la VM se guardan 14 días).

## F11: qué quedó y qué falta

Hecho:

1. **Vigía del servidor** (`infra/monitoreo/vigia.sh`, timer de systemd `abogados-vigia` cada 5 minutos, instalado con `infra/monitoreo/instalar.sh`):
   - Revisa que cada contenedor esté corriendo y sano, el disco (85 %), la memoria disponible (500 MB), la edad del último respaldo (26 h), eventos atascados en los outbox (10 min), las colas de RabbitMQ (sin consumidor o con más de 100 mensajes) y la app por HTTPS.
   - Avisa por Telegram a las cuentas ADMIN que vincularon Telegram, solo cuando algo cambia: el problema nuevo y, después, "resuelto". Compara por una clave estable, así que no repite el aviso porque cambie un número (por ejemplo, el % de disco).
   - Cada día, después de las 7:00, manda un resumen aunque todo esté bien, para saber que el vigía sigue vivo.
   - El token del bot se lee de `infra/.env` y no aparece en la línea de comandos. `vigia.sh estado` muestra la revisión sin avisar y `vigia.sh probar` envía un mensaje de prueba.
2. **Calidad en el CI:**
   - **Cobertura:** JaCoCo en los servicios Java y coverage.py en los Python, con resumen en cada ejecución. Hoy: identidad 59 %, gabinete 64 %, procesos 49 %, gateway 95 %, notificaciones 73 %, vigilancia 65 % y asistente-ia 84 %. Cada servicio tiene un mínimo un poco por debajo de lo actual: la cobertura ya no puede bajar. La meta es 70 %.
   - **Vulnerabilidades:** `pip-audit` en los servicios Python y `npm audit` (altas y críticas, dependencias de producción) en el frontend; el CI falla si aparece una.
   - **Dependabot** (`.github/dependabot.yml`) propone cada semana las actualizaciones de Maven, pip y npm (agrupadas) y cada mes las de Docker y GitHub Actions.
   - Al activarlo se encontraron y corrigieron: Angular 21.2.12 → 21.2.25 (XSS), Starlette 0.41.3 → 1.7.0 (con FastAPI 0.142.2), PyJWT 2.9.0 → 2.15.1 y aiosmtplib 3.0.2 → 5.1.3. Se eligieron versiones publicadas hace al menos dos semanas.
3. **Equipo del proceso:** tarjeta "Equipo" en el expediente para agregar abogados de apoyo y dependientes (personal activo) y quitarlos. El responsable se cambia editando el proceso. Quien está en el equipo ve el proceso en "solo mis procesos".
4. **La firma** (pantalla nueva, solo ADMIN y SOCIO): crear, cambiar y borrar sedes, cargos y áreas de práctica. Siempre hay una sola sede principal; los códigos no se repiten; el director de un área debe ser personal activo; no se borra lo que tiene clientes o personal.
5. **Reportes en PDF:** botón "Imprimir o guardar PDF" con una hoja de impresión (A4 horizontal, sin menú ni botones, con el periodo y la fecha de generación).
6. Pruebas nuevas: equipo (3), catálogos (4) y permisos del gateway (5).

Pendiente o decidido para después:

- Subir la cobertura de procesos, identidad, gabinete y vigilancia hasta el 70 %.
- El vigía no avisa por correo (el correo de la firma aún va a Mailpit) y no guarda historia de métricas: para eso haría falta una herramienta como Prometheus o Uptime Kuma.
- El PDF de los reportes lo genera el navegador; no hay PDF generado en el servidor (por ejemplo, para enviarlo por correo).

## F12: qué quedó y qué falta

Hecho:

1. **Reglas de términos validadas** (pantalla "Reglas", `/reglas-termino` en procesos, migración `009`):
   - El personal ve las reglas; solo un **SOCIO** las crea, edita, activa o desactiva y **valida** (es un juicio jurídico; el ADMIN no basta). Lo exigen el gateway y el propio servicio.
   - Validar deja la regla a nombre del socio y con la fecha. Cambiar lo que dice (patrón, días, norma, rama…) la deja otra vez **sin validar**; activarla o desactivarla, no. Una regla desactivada deja de sugerir términos.
   - Los **días de contestación** de cada tipo de proceso se editan en la misma pantalla; cambiarlos deja sin validar las reglas que los usan.
   - **Historial** de cada regla: quién la creó, cambió (con el antes y el después), validó o desactivó, y cuándo.
   - "Probar" un patrón con la clase de un auto, con el mismo texto normalizado que usa el sugeridor.
2. **Historial del vigía** (pantalla "Servidor", solo ADMIN, `GET /notificaciones/servidor`):
   - `vigia.sh` guarda cada revisión en `operacion.revisiones` (90 días) y cada problema en `operacion.incidentes`, desde que aparece hasta que se resuelve. Los incidentes se concilian con la lista actual, así que se corrigen solos si una vuelta no pudo escribir.
   - La pantalla muestra el estado actual, la disponibilidad de la app en el periodo (1, 7, 30 o 90 días), gráficas de disco y memoria (por hora si son más de 2 días, con el peor valor de cada hora) e incidentes con su duración. Si el vigía lleva 15 minutos sin revisar, lo advierte.
3. **Cobertura ≥ 70 % en todos los servicios** (pruebas nuevas, sin cambiar el código de producción salvo los arreglos de abajo):

   | Servicio | F11 | F12 | Mínimo en el CI |
   |---|---|---|---|
   | procesos | 49 % | 85 % | 80 % |
   | gabinete | 64 % | 85 % | 80 % |
   | identidad | 59 % | 78 % | 74 % |
   | gateway | 95 % | 95 % | 90 % |
   | notificaciones | 73 % | 74 % | 72 % |
   | vigilancia | 65 % | 99 % | 95 % |
   | asistente-ia | 84 % | 84 % | 82 % |

   Los repositorios JDBC de procesos se prueban con un doble de `JdbcClient` que pasa las filas por el `RowMapper` real; los de gabinete, con H2.
4. Arreglos encontrados al escribir las pruebas:
   - procesos: un parámetro obligatorio que faltaba en cualquier ruta respondía "Adjunte el archivo y elija la categoría"; ahora dice cuál falta. Una cabecera de identidad que falta responde con el formato de error de siempre.
   - vigilancia: el relay del outbox repetía el aviso de "RabbitMQ no confirmó" en cada vuelta si una tanda salía a medias.

Pendiente o decidido para después:

- **Que un abogado de la firma revise y valide las reglas.** El software ya lo permite y lo registra; la revisión jurídica no la puede hacer el código.
- Observaciones de las pruebas que no se cambiaron (riesgo bajo, se registran):
  - identidad no consulta la lista de tokens revocados (sí lo hace el gateway, que es la única entrada desde fuera de la VM).
  - Fallar 5 veces la contraseña de alguien bloquea su cuenta y cierra sus sesiones al renovar: es lo buscado contra la fuerza bruta, pero permite molestar a un usuario conocido. Lo mitiga el límite de intentos por IP.
- PDF generado en el servidor y facturación electrónica (DIAN), como en F11.

## F13: qué quedó y qué falta

Hecho:

1. **Verificación en dos pasos** (TOTP, RFC 6238: Google Authenticator, Microsoft Authenticator, Authy…), migración `010`:
   - Cada persona la activa en **Mi cuenta**: escanea el código QR (o escribe la clave) y confirma un código. Recibe **10 códigos de recuperación** de un solo uso, que se muestran una vez (copiar o descargar).
   - Con la verificación activa, `/auth/login` con la contraseña correcta **no entrega token ni cookie**: devuelve un desafío firmado (HMAC, 5 minutos) y la sesión se abre en `/auth/login/mfa` con el código de la app o uno de recuperación.
   - Un código no sirve dos veces (se guarda el último intervalo usado, de forma atómica). Los códigos incorrectos cuentan como intentos fallidos: 5 bloquean la cuenta 15 minutos, igual que la contraseña. También aplica el límite de intentos por IP.
   - **Roles que la exigen**: `MFA_OBLIGATORIO_ROLES` en `infra/.env` (p. ej. `ADMIN,SOCIO`). Quien tiene uno de esos roles y no la ha activado la configura en el mismo inicio de sesión y no puede desactivarla. Hoy está vacío (opcional para todos): activarla para ADMIN y SOCIO es decisión de la firma.
   - El **administrador** ve en Usuarios quién la tiene y puede **reiniciarla** (quien perdió el teléfono y los códigos): se cortan las sesiones de esa persona y la configura de nuevo al entrar.
   - El secreto se guarda **cifrado con AES-256-GCM**; la llave (`MFA_CLAVE_CIFRADO`) se generó en el servidor sin mostrarla y no está en la base ni en sus respaldos. Los códigos de recuperación se guardan como SHA-256.
2. Arreglos de seguridad que dejaron anotados las pruebas de F12:
   - **identidad revisa la lista de tokens revocados** (antes solo el gateway): un token cortado deja de servir también si alguien llega por la red interna.
   - **El bloqueo por intentos fallidos ya no cierra las sesiones abiertas** al renovarlas: así nadie puede cerrarle la sesión a otra persona fallando a propósito su contraseña. Desactivar la cuenta sí las cierra.
   - La IP del cliente: se confirmó que nginx reemplaza `X-Forwarded-For` y el gateway solo escucha en 127.0.0.1, así que no se puede falsificar (no hizo falta cambiar nada).
3. Pruebas: TOTP con los vectores del RFC 6238, activación, segundo paso, recuperación, bloqueo, desafíos alterados o vencidos, rol obligatorio, reinicio, cifrado (11 + 6 de la API + revocación). Cobertura de identidad 79,8 %.

Pendiente o decidido para después:

- **Respaldar la llave `MFA_CLAVE_CIFRADO`** junto con las demás de `infra/.env` y `infra/secretos/` (no van en el respaldo diario de la base). Si se pierde, el administrador debe reiniciar la verificación de cada cuenta.
- Llaves físicas (WebAuthn / passkeys) y aviso por correo al activar o reiniciar la verificación.

## F14: qué quedó y qué falta

Hecho (pantalla **Facturación**, `/facturas` y `/facturacion` en gabinete, migración `011`):

1. **Facturas electrónicas de venta** a un cliente:
   - El sistema sugiere lo **pendiente por facturar**: el saldo de cada acuerdo de honorarios (lo ya facturado se descuenta), la cuota de los acuerdos mensuales y los gastos reembolsables que la firma adelantó y aún no cobró.
   - **SERVICIO** (honorarios u otros) lleva IVA si la firma es responsable; **REEMBOLSO** (gastos del cliente) va sin IVA ni retención, con el concepto y el valor del gasto registrado. Un gasto solo se cobra en una factura.
   - **Retención en la fuente** estimada (11 % sugerido si el cliente es persona jurídica, editable): no cambia el total facturado, cambia lo que se espera recibir.
   - **Numeración sin saltos** de la resolución vigente (la configuración se bloquea al tomar el número); avisa cuando quedan menos de 100 números o la resolución vence.
   - Los datos del cliente se copian al emitir; el cliente debe tener **correo y dirección** (la DIAN exige el correo del adquirente).
2. **CUFE** (SHA-384 de los campos del Anexo Técnico) y **representación gráfica** imprimible o en PDF con el código QR de consulta en la DIAN, la resolución, el adquirente y los totales. En ambiente de pruebas dice "Documento de prueba · sin validez fiscal".
3. **Anulación con nota crédito** por el total (prefijo NC, con su CUDE): la factura queda ANULADA y sus gastos vuelven a quedar pendientes.
4. **Configuración** (solo la dirección la cambia): responsabilidad de IVA, tarifa, retenciones sugeridas, días de crédito, resolución (prefijo, rango, vigencia), ambiente. **El contador valida** el IVA y las retenciones (como las reglas de términos en F12); cambiarlos los deja sin validar. No se puede pasar a PRODUCCION con el proveedor simulado.
5. **Proveedor tecnológico detrás de una interfaz** (`ProveedorFacturacion`). Hoy solo existe el **SIMULADO**: revisa los datos obligatorios, calcula el CUFE con la clave técnica pública del set de pruebas y responde "aceptada". Arranca con el rango del set de pruebas de la DIAN (SETP 990000000 a 995000000).
6. Pruebas: servicio (14), SQL sobre H2 (4), API (4) y permisos del gateway. Cobertura de gabinete 87,8 %.

Pendiente (lo que no es código o requiere decisiones de la firma):

- **Contratar un proveedor tecnológico** autorizado (Alegra, Siigo, The Factory HKA…) y escribir su adaptador de `ProveedorFacturacion` con las credenciales en `infra/.env`. Con él, hacer la habilitación ante la DIAN (set de pruebas) y pedir la resolución de numeración real.
- **Que el contador valide** la responsabilidad de IVA, la tarifa y las retenciones. Otras retenciones (ICA, reteIVA) y la factura en moneda extranjera no están.
- El cliente aún no ve sus facturas en el portal, y la factura no se envía por correo (en producción la entrega el proveedor).
