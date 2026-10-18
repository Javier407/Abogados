# 5. Mapa de procesos: qué está hecho, qué falta y dónde va el código

Este mapa cruza los procesos de negocio de una firma de abogados (`docs/01-dominio.md`) con lo que el código hace hoy. Sirve para decidir las fases siguientes.

> Escrito al cerrar F5, **actualizado con F6 y F7**. F7 trajo audiencias en la agenda con aviso, pruebas con perito y términos sugeridos.
> Actualización de F6 (rama `feature/f6-expediente`, 2026-10-17): partes y conflicto de intereses, actuaciones, providencia manual, sentencia, recursos, otras instancias y copias de seguridad.

**Estados:**
- ✅ **Hecho:** tiene lógica, API, pantalla y pruebas.
- 🟡 **Parcial:** existe una parte (por ejemplo, la tabla o la consulta), pero no el flujo completo.
- ❌ **Falta:** no hay código. Algunas tablas existen desde F0, pero nada las usa.

## Vista general

| # | Macroproceso | Estado | Resumen |
|---|---|---|---|
| 1 | Captación y vinculación del cliente | 🟡 | Clientes, acuerdos de honorarios, conflicto de intereses y poder (F6). Falta la consulta inicial. |
| 2 | Gestión del proceso judicial | 🟡 | Con F6 el expediente tiene partes, actuaciones, providencias, sentencia, recursos y línea de tiempo. Faltan audiencias en la agenda, pruebas y el expediente digital. |
| 3 | Vigilancia y alertas | ✅ | Rama Judicial (real o simulada), correo, avisos en la app y Telegram. |
| 4 | Redacción asistida (IA) | 🟡 | Contestación y resumen para el cliente. Faltan la demanda, los recursos y el resumen de autos. |
| 5 | Finanzas | 🟡 | Tarifas, acuerdos y pagos. Faltan anulación de pagos, gastos procesales, facturación y cartera. |
| 6 | Administración de la firma | 🟡 | Abogados, peritos y seguridad. Sedes, áreas y cargos son solo de lectura. No hay administración de usuarios. |
| 7 | Portal del cliente | ❌ | El rol CLIENTE solo ve "Mi cuenta". |
| 8 | Reportes e indicadores | 🟡 | Panel general. Faltan reportes por abogado, área y resultados. |
| 9 | Plataforma (no funcional) | 🟡 | CI, JWT, auditoría, healthchecks y copias de seguridad diarias (F6). Faltan HTTPS, refresh token, outbox y copias fuera del servidor. |

## 1. Captación y vinculación del cliente

| Proceso | Estado | Código actual | Dónde iría lo que falta |
|---|---|---|---|
| Registrar consulta inicial (quién, qué caso, resultado) | ❌ | — | `gabinete`: tabla `gabinete.consultas` (nueva) y pantalla de secretaría. |
| Verificar conflicto de intereses (¿la contraparte ya es cliente?) | ✅ F6 | `ExpedienteServicio.conflictos`: documento exacto o nombre normalizado contra los clientes de gabinete; aviso en la tarjeta de partes | A escala (miles de clientes), búsqueda normalizada en gabinete. |
| Registrar cliente | ✅ | `gabinete` → `ClientesRecurso`, `pages/clientes` | — |
| Pactar honorarios | ✅ | `gabinete` → `HonorariosRecurso`, `pages/honorarios` | — |
| Otorgar poder (documento firmado) | 🟡 F6 | Actuación tipo `PODER` con enlace al documento. | Guardar el archivo: ver "Expediente digital". |

## 2. Gestión del proceso judicial

Sigue el ciclo poder → demanda → admisión → notificación → contestación → pruebas → audiencias → sentencia → recursos.

| Proceso | Estado | Código actual | Dónde iría lo que falta |
|---|---|---|---|
| Registrar el proceso con su radicado | ✅ | `procesos` → `ProcesoServicio.crear`, validación del radicado y despacho, `pages/procesos/proceso-form` | — |
| Partes: cliente, contraparte y terceros | ✅ F6 | `ExpedienteRecurso` `/procesos/{id}/partes`, tarjeta "Partes" del expediente | — |
| Equipo: responsable y apoyo | 🟡 | Hay responsable. El anterior pasa a `APOYO` automáticamente, pero no hay forma de agregar apoyo. | `/procesos/{id}/asignaciones` y una lista en el detalle del proceso. |
| Actuaciones (demanda, contestación, memoriales, alegatos) | ✅ F6 | `/procesos/{id}/actuaciones`, línea de tiempo del expediente; cumplen términos y mueven la etapa | — |
| Providencias detectadas por la vigilancia | ✅ | `ConsumidorActuaciones` → `ProvidenciaServicio.registrarDetectada`, `providencias-proceso.component` | — |
| Registrar una providencia a mano | ✅ F6 | `POST /procesos/{id}/providencias` | — |
| Términos (crear, cumplir, revisión diaria, calendario) | ✅ | `TerminoServicio`, `CalendarioServicio`, `RevisionDiariaTerminos`, `pages/terminos`, `pages/calendario` | — |
| Términos sugeridos al llegar un auto (por ejemplo, admite demanda → 20 días en un verbal) | ✅ F7 (reglas a validar) | Los términos de referencia están en `01-dominio.md` §1.6, pero no en el código. | Tabla `procesos.reglas_termino` (tipo de proceso × clase de auto → días). `ProvidenciaServicio` propone el término y el abogado lo confirma. Las reglas debe validarlas un abogado. |
| Audiencias y agenda | ✅ F7 | Hora y lugar, calendario y aviso `audiencia.proxima` el día hábil anterior. | Actuación tipo `AUDIENCIA` con fecha futura, mostrada en el calendario, con un aviso el día antes (evento `audiencia.proxima`). |
| Pruebas (documental, pericial, testimonial…) | ✅ F7 | `/procesos/{id}/pruebas` con perito y término de entrega. | CRUD `/procesos/{id}/pruebas`, asignación de perito (lee `gabinete` vía `DirectorioGabinete`) y fecha de entrega del dictamen como término. |
| Sentencia y resultado | ✅ F6 | Sentencia manual con `sentido`; el proceso pasa a la etapa SENTENCIA | Usar el sentido en la cuota litis (sección 5) y en los reportes (sección 8). |
| Recursos (apelación, reposición, impugnación de tutela) | 🟡 F6 | Actuación `RECURSO` contra una providencia; otras instancias del mismo radicado enlazadas en el expediente | El término de 3 días se crea a mano: sugerirlo automáticamente (F7). |
| Expediente digital (documentos) | 🟡 F6 | Las actuaciones guardan un enlace (`documento_url`). | Servicio `documentos` o almacenamiento tipo S3/MinIO (ya hay uno en el servidor, de otro proyecto: crear uno propio). Debe tener control de acceso por proceso. |

## 3. Vigilancia y alertas ✅

| Proceso | Estado | Código actual |
|---|---|---|
| Vigilar radicados (simulada; portal real solo en los marcados) | ✅ | `vigilancia` → `FuenteRamaJudicial`, consulta programada, `pages/vigilancia` |
| Avisar términos por vencer o vencidos | ✅ | `procesos` publica `termino.*` y `notificaciones` envía correo, aviso en la app y Telegram. |
| Avisar actuaciones nuevas | ✅ | `actuacion.detectada` → `procesos` + `notificaciones` |
| Avisar también al cliente | ❌ | Pregunta abierta 2 de `01-dominio.md`. Depende del portal del cliente (sección 7). |
| Entrega garantizada de eventos | ❌ | Sin *outbox*: si RabbitMQ está caído, el evento se pierde (plan de mejora ISO, punto 6). |

## 4. Redacción asistida (IA local)

| Proceso | Estado | Código actual | Dónde iría lo que falta |
|---|---|---|---|
| Borrador de contestación | ✅ | `asistente-ia` → `TipoBorrador.CONTESTACION`, `asistente-proceso.component` | — |
| Resumen del proceso para el cliente | ✅ | `TipoBorrador.RESUMEN_CLIENTE` | — |
| Borrador de demanda | ❌ | — | Nuevo `TipoBorrador.DEMANDA`: solo cuando el cliente es demandante, con hechos y pretensiones que escribe el abogado. |
| Borrador de recurso | ❌ | — | `TipoBorrador.RECURSO`, a partir de la providencia que se recurre. |
| Resumen de un auto nuevo, adjunto al aviso | ❌ | — | Al recibir `actuacion.detectada`, `asistente-ia` resume la providencia y `notificaciones` lo incluye en el aviso. |

## 5. Finanzas

| Proceso | Estado | Código actual | Dónde iría lo que falta |
|---|---|---|---|
| Tarifas de referencia (Conalbos, SMMLV) | ✅ | `gabinete` → `/gabinete/tarifas` | — |
| Acuerdo de honorarios por proceso | ✅ | `HonorariosRecurso` | — |
| Registrar pagos | ✅ | `POST /honorarios/{id}/pagos` | — |
| Anular un pago con motivo | ❌ | Hoy se corrige en la base de datos. | `POST /honorarios/{id}/pagos/{pago}/anulacion`, que deja un rastro y nunca borra el pago. |
| Liquidar la cuota litis al terminar el proceso | ❌ | — | Al registrar una sentencia favorable con valor obtenido, calcular el porcentaje pactado. |
| Gastos procesales (copias, notificaciones, peritos) | ❌ | — | Tabla `gabinete.gastos` por proceso, reembolsable o no. |
| Facturación electrónica (DIAN) | ❌ | Pregunta abierta 5. | Integración con un proveedor tecnológico. Fuera del alcance académico; se registra como decisión. |
| Cartera por cliente y vencimientos | 🟡 | Saldo total en la pantalla de honorarios. | Reporte por cliente y antigüedad (sección 8). |

## 6. Administración de la firma

| Proceso | Estado | Código actual | Dónde iría lo que falta |
|---|---|---|---|
| Abogados y personal | ✅ | `AbogadosRecurso`, `pages/abogados` | — |
| Peritos | ✅ | `PeritosRecurso`, `pages/peritos` | No tiene `DELETE`. Si se agrega, debe impedir borrar peritos con pruebas asignadas. |
| Sedes, áreas de práctica y cargos | 🟡 | Solo `GET` en `/gabinete/*`. | CRUD solo para la dirección (ADMIN o SOCIO). |
| Usuarios, roles y restablecer contraseña | ❌ | Se hace por SQL. | `identidad`: `/usuarios` (solo ADMIN) y pantalla de administración. Enlazar abogado → usuario (1 : 0..1). |
| Seguridad: login, roles, bloqueo y auditoría | ✅ | `identidad`, `gateway` (`PoliticaAcceso`, `Auditoria`) | — |

## 7. Portal del cliente ❌

| Proceso | Dónde iría |
|---|---|
| El cliente ve sus procesos, su estado y sus próximas fechas | Rutas `/api/portal/**` en el gateway, solo para el rol CLIENTE, filtradas por `idCliente` del token. Hay que enlazar el cliente con su usuario. |
| El cliente ve sus pagos y su saldo | Lo mismo, consultando `gabinete`. |
| Resumen en lenguaje sencillo | Reutiliza `RESUMEN_CLIENTE`, siempre revisado por el abogado antes de publicarse. |
| Avisos al cliente | `notificaciones` con preferencias por cliente. |

## 8. Reportes e indicadores

| Proceso | Estado | Dónde iría |
|---|---|---|
| Panel general (términos, procesos por rama) | ✅ | `dashboard` |
| Carga por abogado y por área | ❌ | Consultas en `procesos` (ya existen ejemplos en `db/consultas/ejemplos.sql`) y una pantalla de reportes para SOCIO y ADMIN. |
| Resultados (sentencias favorables por rama) | ❌ | Depende de "Sentencia y resultado" (sección 2). |
| Términos cumplidos a tiempo y vencidos | ❌ | Fecha de cumplimiento frente al vencimiento en `procesos.terminos`. |
| Exportar a Excel o PDF | ❌ | En el frontend (por ejemplo, CSV) o un endpoint de exportación. |

## 9. Plataforma (no funcional)

| Necesidad | Estado | Nota |
|---|---|---|
| CI en cada push | ✅ | `.github/workflows/ci.yml` |
| JWT, roles, bloqueo y auditoría | ✅ | F5 |
| HTTPS / `tailscale serve` | ❌ | Prioridad 1 del plan de mejora antes de salir de la VPN. |
| Refresh token y revocación | ❌ | Prioridad 2. |
| Validar el JWT en cada servicio | ❌ | Prioridad 3. |
| Outbox de eventos | ❌ | Prioridad 6. |
| Copias de seguridad de PostgreSQL | 🟡 F6 | `pg_dump` diario con 14 días de historia y prueba de restauración (`infra/respaldo/respaldar.sh`). Falta copiarlas **fuera de la VM**. |
| Monitoreo y logs centralizados | ❌ | Solo `docker compose logs`. |
| IP real del cliente (Tailscale SNAT) | ❌ | Brecha 7 de la evaluación ISO. |

## Propuesta de fases siguientes

Ordenadas por valor para la firma y por dependencias:

| Fase | Contenido | Por qué en este orden |
|---|---|---|
| **F6. Expediente** ✅ | Partes (con conflicto de intereses), actuaciones con línea de tiempo, providencia manual, sentencia con sentido, recursos. Copias de seguridad diarias. | Completa el corazón del dominio: hoy un proceso no tiene historia propia, solo términos y autos detectados. Antes de meter datos reales hacen falta copias de seguridad. |
| **F7. Agenda y pruebas** ✅ | Audiencias en el calendario con aviso, pruebas con perito, términos sugeridos al llegar un auto (reglas validadas por un abogado). | Usa las actuaciones de F6. Es lo que más evita olvidos después de los términos. |
| **F8. Administración y finanzas** | Usuarios y roles desde la app, anulación de pagos, gastos, cuota litis, reportes. | Quita el trabajo por SQL y da información a los socios. |
| **F9. Portal del cliente y endurecimiento** | Portal, avisos al cliente, HTTPS, refresh token, outbox. | El portal expone datos fuera del equipo: requiere primero las prioridades 1 a 3 de seguridad. |
