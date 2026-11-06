# ADR 0002: Mensajería con RabbitMQ

- **Estado:** aceptada
- **Fecha:** 2026-10-15

## Contexto

Varias acciones se disparan en un servicio y se atienden en otro. Por ejemplo, una actuación detectada por `vigilancia` debe registrarse en `procesos` y avisarse en `notificaciones`. Si se encadenan llamadas REST, un servicio caído hace perder el aviso.

## Decisión

**RabbitMQ 3.13** con un exchange `gabinete.eventos` tipo *topic*. Cada consumidor tiene su propia cola durable. Los eventos se listan en `docs/02-arquitectura.md` §2.4.

## Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Apache Kafka | Pensado para volúmenes y retención muy altos; consume mucha más RAM; excesivo aquí. |
| REST síncrono entre servicios | Acopla los servicios y pierde eventos si el destino está caído. |
| Redis Streams | Viable y liviano, pero menos herramientas de administración y de reintentos para Spring y Python. |

## Consecuencias

- **Positivas:**
  - Los servicios se desacoplan.
  - Los mensajes se reintentan.
  - El panel de administración ayuda a depurar.
- **Negativas:**
  - Un componente más (≈ 256 MB).
  - Los consumidores deben ser idempotentes: el mismo evento puede llegar dos veces.
