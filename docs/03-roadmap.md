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

## Orden sugerido dentro de F1

1. `infra/docker-compose.yml` con PostgreSQL y RabbitMQ (la BD de pruebas de F0 ya sirve aquí).
2. Contratos OpenAPI de `gabinete` y `procesos` en `docs/contratos/`.
3. `procesos`, porque es el corazón del producto: entidades, validación del radicado y CRUD.
4. `gabinete`: migrar el código actual a Spring Boot 3 y al esquema `gabinete`.
5. `gateway` con las rutas a los dos servicios.
6. Copiar `gestion-juridica-frontend` a `frontend/`, cambiar `localhost:8860` por la URL del gateway y crear las vistas de procesos.
