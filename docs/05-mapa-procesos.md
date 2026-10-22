# 5. Mapa de procesos: qué está hecho, qué falta y dónde va el código

Este mapa cruza los procesos de negocio de una firma de abogados (`docs/01-dominio.md`) con lo que el código hace hoy. Sirve para decidir las fases siguientes.

> Escrito al cerrar F5, **actualizado con F6 a F11**. F11 agregó el vigía del servidor con avisos por Telegram, la cobertura y la auditoría de dependencias en el CI, los abogados de apoyo, la edición de sedes, cargos y áreas y los reportes en PDF. F10 agregó el expediente digital, los borradores de demanda y recurso, la consulta inicial y la copia diaria de los respaldos al PC. F9 agregó el portal del cliente con sus avisos, sesiones renovables y revocables, verificación del token en cada servicio, outbox y HTTPS. F8 agregó administración de cuentas, anulación de pagos, liquidación, gastos, cartera y reportes. F7 trajo audiencias en la agenda con aviso, pruebas con perito y términos sugeridos.
> Actualización de F6 (rama `feature/f6-expediente`, 2026-10-17): partes y conflicto de intereses, actuaciones, providencia manual, sentencia, recursos, otras instancias y copias de seguridad.

**Estados:**
- ✅ **Hecho:** tiene lógica, API, pantalla y pruebas.
- 🟡 **Parcial:** existe una parte (por ejemplo, la tabla o la consulta), pero no el flujo completo.
- ❌ **Falta:** no hay código. Algunas tablas existen desde F0, pero nada las usa.

## Vista general

| # | Macroproceso | Estado | Resumen |
|---|---|---|---|
| 1 | Captación y vinculación del cliente | ✅ | Consulta inicial con conflicto de intereses (F10), clientes, acuerdos de honorarios y poder firmado en el expediente. |
| 2 | Gestión del proceso judicial | 🟡 | Partes, equipo (F11), actuaciones, providencias, términos sugeridos, audiencias, pruebas, sentencia, recursos y documentos (F10). Las reglas de términos sugeridos (también las de recursos) esperan la validación de un abogado. |
| 3 | Vigilancia y alertas | ✅ | Rama Judicial (real o simulada), correo, avisos en la app y Telegram; avisos al cliente y entrega garantizada (F9). |
| 4 | Redacción asistida (IA) | ✅ | Contestación, demanda, recurso y resumen para el cliente (F10). El resumen de autos en el aviso se decidió no hacerlo. |
| 5 | Finanzas | 🟡 | Tarifas, acuerdos, pagos con anulación, liquidación de la parte variable, gastos y cartera (F8). Falta la facturación electrónica. |
| 6 | Administración de la firma | ✅ | Abogados, peritos, cuentas de usuario (F8), seguridad y, desde F11, sedes, áreas y cargos editables. |
| 7 | Portal del cliente | ✅ | Desde F9: sus procesos, novedades, resumen publicado por el abogado, pagos y avisos. |
| 8 | Reportes e indicadores | ✅ | Panel general, carga por abogado, cumplimiento de términos, resultados por rama y cartera (F8), con CSV por tabla y el informe en PDF (F11). |
| 9 | Plataforma (no funcional) | ✅ | CI, JWT, auditoría, copias diarias, refresh token, JWT en cada servicio y outbox (F9). HTTPS, copias en el PC (F10), vigía con avisos y calidad en el CI (F11). |

## 1. Captación y vinculación del cliente

| Proceso | Estado | Código actual | Dónde iría lo que falta |
|---|---|---|---|
| Registrar consulta inicial (quién, qué caso, resultado) | ✅ F10 | `gabinete` → `ConsultaServicio`, `/consultas`, pantalla Consultas (con conflicto de intereses y paso a cliente) | — |
| Verificar conflicto de intereses (¿la contraparte ya es cliente?) | ✅ F6 | `ExpedienteServicio.conflictos`: documento exacto o nombre normalizado contra los clientes de gabinete; aviso en la tarjeta de partes | A escala (miles de clientes), búsqueda normalizada en gabinete. |
| Registrar cliente | ✅ | `gabinete` → `ClientesRecurso`, `pages/clientes` | — |
| Pactar honorarios | ✅ | `gabinete` → `HonorariosRecurso`, `pages/honorarios` | — |
| Otorgar poder (documento firmado) | ✅ F10 | Actuación `PODER` y el archivo firmado en el expediente digital (categoría PODER) | — |

## 2. Gestión del proceso judicial

Sigue el ciclo poder → demanda → admisión → notificación → contestación → pruebas → audiencias → sentencia → recursos.

| Proceso | Estado | Código actual | Dónde iría lo que falta |
|---|---|---|---|
| Registrar el proceso con su radicado | ✅ | `procesos` → `ProcesoServicio.crear`, validación del radicado y despacho, `pages/procesos/proceso-form` | — |
| Partes: cliente, contraparte y terceros | ✅ F6 | `ExpedienteRecurso` `/procesos/{id}/partes`, tarjeta "Partes" del expediente | — |
| Equipo: responsable y apoyo | ✅ F11 | `EquipoServicio`, `/procesos/{id}/asignaciones`, tarjeta "Equipo" del expediente | — |
| Actuaciones (demanda, contestación, memoriales, alegatos) | ✅ F6 | `/procesos/{id}/actuaciones`, línea de tiempo del expediente; cumplen términos y mueven la etapa | — |
| Providencias detectadas por la vigilancia | ✅ | `ConsumidorActuaciones` → `ProvidenciaServicio.registrarDetectada`, `providencias-proceso.component` | — |
| Registrar una providencia a mano | ✅ F6 | `POST /procesos/{id}/providencias` | — |
| Términos (crear, cumplir, revisión diaria, calendario) | ✅ | `TerminoServicio`, `CalendarioServicio`, `RevisionDiariaTerminos`, `pages/terminos`, `pages/calendario` | — |
| Términos sugeridos al llegar un auto (por ejemplo, admite demanda → 20 días en un verbal) | ✅ F7 (reglas a validar) | Los términos de referencia están en `01-dominio.md` §1.6, pero no en el código. | Tabla `procesos.reglas_termino` (tipo de proceso × clase de auto → días). `ProvidenciaServicio` propone el término y el abogado lo confirma. Las reglas debe validarlas un abogado. |
| Audiencias y agenda | ✅ F7 | Hora y lugar, calendario y aviso `audiencia.proxima` el día hábil anterior. | Actuación tipo `AUDIENCIA` con fecha futura, mostrada en el calendario, con un aviso el día antes (evento `audiencia.proxima`). |
| Pruebas (documental, pericial, testimonial…) | ✅ F7 | `/procesos/{id}/pruebas` con perito y término de entrega. | CRUD `/procesos/{id}/pruebas`, asignación de perito (lee `gabinete` vía `DirectorioGabinete`) y fecha de entrega del dictamen como término. |
| Sentencia y resultado | ✅ F6 | Sentencia manual con `sentido`; el proceso pasa a la etapa SENTENCIA | Usar el sentido en la cuota litis (sección 5) y en los reportes (sección 8). |
| Recursos (apelación, reposición, impugnación de tutela) | 🟡 F6 | Actuación `RECURSO` contra una providencia; otras instancias del mismo radicado enlazadas en el expediente | El término de 3 días se crea a mano: sugerirlo automáticamente (F7). |
| Expediente digital (documentos) | ✅ F10 | `procesos` → `DocumentoServicio`, volumen `documentos`, `/procesos/{id}/documentos`; el cliente ve los visibles en su portal | Antivirus si se aceptan más tipos de archivo. |

## 3. Vigilancia y alertas ✅

| Proceso | Estado | Código actual |
|---|---|---|
| Vigilar radicados (simulada; portal real solo en los marcados) | ✅ | `vigilancia` → `FuenteRamaJudicial`, consulta programada, `pages/vigilancia` |
| Avisar términos por vencer o vencidos | ✅ | `procesos` publica `termino.*` y `notificaciones` envía correo, aviso en la app y Telegram. |
| Avisar actuaciones nuevas | ✅ | `actuacion.detectada` → `procesos` + `notificaciones` |
| Avisar también al cliente | ✅ F9 | Resumen publicado y audiencia del día hábil siguiente, en palabras simples (no se le reenvían los autos sin revisión del abogado). |
| Entrega garantizada de eventos | ✅ F9 | `procesos.outbox` y `vigilancia.outbox` con confirmación del broker. |

## 4. Redacción asistida (IA local)

| Proceso | Estado | Código actual | Dónde iría lo que falta |
|---|---|---|---|
| Borrador de contestación | ✅ | `asistente-ia` → `TipoBorrador.CONTESTACION`, `asistente-proceso.component` | — |
| Resumen del proceso para el cliente | ✅ | `TipoBorrador.RESUMEN_CLIENTE` | — |
| Borrador de demanda | ✅ F10 | `TipoBorrador.DEMANDA`: solo si el cliente es demandante; hechos y pretensiones del abogado como datos | — |
| Borrador de recurso | ✅ F10 | `TipoBorrador.RECURSO` contra la providencia elegida | — |
| Resumen de un auto nuevo, adjunto al aviso | ⏸ Decidido no hacerlo (F10) | — | Las anotaciones del portal son cortas y el modelo local tarda minutos en CPU; el aviso ya trae la anotación. Revisar si se usa un modelo más rápido. |

## 5. Finanzas

| Proceso | Estado | Código actual | Dónde iría lo que falta |
|---|---|---|---|
| Tarifas de referencia (Conalbos, SMMLV) | ✅ | `gabinete` → `/gabinete/tarifas` | — |
| Acuerdo de honorarios por proceso | ✅ | `HonorariosRecurso` | — |
| Registrar pagos | ✅ | `POST /honorarios/{id}/pagos` | — |
| Anular un pago con motivo | ✅ F8 | `POST /honorarios/{id}/pagos/{pago}/anulacion`: queda en el historial con motivo, fecha y autor | — |
| Liquidar la cuota litis al terminar el proceso | ✅ F8 | `POST /honorarios/{id}/liquidacion`: porcentaje × valor obtenido (+ fijo en MIXTO); desde ahí el saldo tiene tope | Proponerla al registrar una sentencia favorable. |
| Gastos procesales (copias, notificaciones, peritos) | ✅ F8 | `gabinete.gastos` y `/gastos`; tarjeta en el expediente; reembolsables en la cartera | — |
| Facturación electrónica (DIAN) | ❌ | Pregunta abierta 5. | Integración con un proveedor tecnológico. Fuera del alcance académico; se registra como decisión. |
| Cartera por cliente y vencimientos | ✅ F8 | `GET /honorarios/cartera`: saldo, gastos por reembolsar y días desde el último pago | — |

## 6. Administración de la firma

| Proceso | Estado | Código actual | Dónde iría lo que falta |
|---|---|---|---|
| Abogados y personal | ✅ | `AbogadosRecurso`, `pages/abogados` | — |
| Peritos | ✅ | `PeritosRecurso`, `pages/peritos` | No tiene `DELETE`. Si se agrega, debe impedir borrar peritos con pruebas asignadas. |
| Sedes, áreas de práctica y cargos | ✅ F11 | `CatalogoServicio` (POST, PUT y DELETE en `/gabinete/sedes`, `/cargos`, `/areas`), pantalla "La firma" | — |
| Usuarios, roles y restablecer contraseña | ✅ F8 | `identidad` `/usuarios` (solo ADMIN) y pantalla Usuarios; clave temporal que se debe cambiar al entrar | Enlazar la cuenta con la ficha del abogado al crearla. |
| Seguridad: login, roles, bloqueo y auditoría | ✅ | `identidad`, `gateway` (`PoliticaAcceso`, `Auditoria`) | — |

## 7. Portal del cliente ✅ (F9)

| Proceso | Estado | Código actual |
|---|---|---|
| El cliente ve sus procesos, su estado y sus próximas fechas | ✅ | `procesos` → `PortalServicio`, `/portal/procesos` (filtra por `idCliente` del token, enlazado en `identidad.usuarios.id_cliente`); pantallas `pages/portal` |
| El cliente ve sus pagos y su saldo | ✅ | `gabinete` → `PortalFinanzasServicio`, `/portal/finanzas` |
| Resumen en lenguaje sencillo | ✅ | El abogado lo genera con `RESUMEN_CLIENTE` o lo escribe, lo revisa y lo publica (`/procesos/{id}/resumen-cliente`) |
| Avisos al cliente | ✅ | `notificaciones`: `portal.resumen_publicado` y `audiencia.proxima`, a las cuentas del cliente (`identidad /interno/clientes/{id}/cuentas`). Falta que el cliente elija sus canales. |

## 8. Reportes e indicadores

| Proceso | Estado | Dónde iría |
|---|---|---|
| Panel general (términos, procesos por rama) | ✅ | `dashboard` |
| Carga por abogado y por área | ✅ F8 (por abogado) | `GET /reportes/carga` | Agrupar también por área de práctica. |
| Resultados (sentencias favorables por rama) | ✅ F8 | `GET /reportes/resultados` | — |
| Términos cumplidos a tiempo y vencidos | ✅ F8 | `GET /reportes/terminos`; `procesos.terminos.cumplido_en` desde F8 | — |
| Exportar a Excel o PDF | ✅ F11 | CSV por tabla (F8) e informe completo en PDF con la impresión del navegador | PDF generado en el servidor, si se quiere enviar por correo. |

## 9. Plataforma (no funcional)

| Necesidad | Estado | Nota |
|---|---|---|
| CI en cada push | ✅ | `.github/workflows/ci.yml` |
| JWT, roles, bloqueo y auditoría | ✅ | F5 |
| HTTPS / `tailscale serve` | ✅ F9 | https://docker-server.tail76e9a3.ts.net (solo tailnet), redirección, HSTS y cookie Secure. |
| Refresh token y revocación | ✅ F9 | Token de 15 min, refresh token rotatorio en cookie HttpOnly, lista de revocados en el gateway. |
| Validar el JWT en cada servicio | ✅ F9 | `FiltroToken` (Java) y `MiddlewareToken` (Python); tokens de servicio entre ellos. |
| Outbox de eventos | ✅ F9 | `procesos` y `vigilancia`. |
| Copias de seguridad de PostgreSQL y documentos | ✅ F10 | `pg_dump` y paquete de documentos diarios en la VM (14 días) y copia al PC con SHA-256 verificado (tarea programada, 30 días). |
| Monitoreo y alertas | ✅ F11 | Vigía cada 5 minutos con avisos por Telegram (`infra/monitoreo`). Sin historia de métricas ni logs centralizados. |
| Análisis de dependencias y cobertura | ✅ F11 | Dependabot, `pip-audit` y `npm audit` en el CI; JaCoCo y coverage.py con mínimo por servicio. |
| IP real del cliente (Tailscale SNAT) | ✅ F9 | Por `tailscale serve`: última IP de `X-Forwarded-For`. |

## Propuesta de fases siguientes

Ordenadas por valor para la firma y por dependencias:

| Fase | Contenido | Por qué en este orden |
|---|---|---|
| **F6. Expediente** ✅ | Partes (con conflicto de intereses), actuaciones con línea de tiempo, providencia manual, sentencia con sentido, recursos. Copias de seguridad diarias. | Completa el corazón del dominio: hoy un proceso no tiene historia propia, solo términos y autos detectados. Antes de meter datos reales hacen falta copias de seguridad. |
| **F7. Agenda y pruebas** ✅ | Audiencias en el calendario con aviso, pruebas con perito, términos sugeridos al llegar un auto (reglas validadas por un abogado). | Usa las actuaciones de F6. Es lo que más evita olvidos después de los términos. |
| **F8. Administración y finanzas** ✅ | Usuarios y roles desde la app, anulación de pagos, gastos, cuota litis, reportes. | Quita el trabajo por SQL y da información a los socios. |
| **F9. Portal del cliente y endurecimiento** ✅ | Portal, avisos al cliente, HTTPS, refresh token, JWT en cada servicio, outbox. | El portal expone datos fuera del equipo: requiere primero las prioridades 1 a 3 de seguridad. |
| **F10. Documentos y redacción** ✅ | Expediente digital con archivos y control de acceso, borradores de demanda y recurso, consulta inicial, copias fuera de la VM. (El resumen del auto en el aviso se decidió no hacerlo.) | Lo que más trabajo ahorra ahora que el núcleo está completo. |
| **F11. Operación** ✅ | Vigía con avisos por Telegram, análisis de dependencias y cobertura en el CI, sedes/áreas/cargos editables, abogados de apoyo, PDF en reportes. | Lo que faltaba para operar con tranquilidad. |
