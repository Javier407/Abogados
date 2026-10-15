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

## Estado

| Fase | Estado | Fecha |
|---|---|---|
| F0 | Terminada | 2026-10-15 |
| F1 | Terminada: criterio comprobado (cliente, abogado y proceso creados desde Angular y verificados en PostgreSQL; evento `proceso.creado` publicado) | 2026-10-15 |
| F2 – F5 | Pendientes | |

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
- Sin autenticación: el gateway está abierto dentro de la VPN. Se agrega en F5.
