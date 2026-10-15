# ADR 0003: PostgreSQL con un esquema por servicio

- **Estado:** aceptada
- **Fecha:** 2026-10-15

## Contexto

En microservicios cada servicio debe ser dueño de sus datos. El servidor tiene RAM limitada (≈ 9,6 GB compartidos con otros proyectos). El dominio usa mucho cálculo de fechas (términos en días hábiles) y datos semiestructurados (actuaciones traídas del portal).

## Decisión

Una instancia de **PostgreSQL 16** con un **esquema por servicio**: `identidad`, `gabinete`, `procesos`, `vigilancia` y `notificaciones`. Cada servicio se conecta con un usuario que solo tiene permisos sobre su esquema. **No hay llaves foráneas entre esquemas**: las referencias a otros servicios se guardan como identificadores.

## Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Un contenedor de base de datos por servicio | Más aislamiento, pero multiplica la RAM. Se puede adoptar después sin cambiar el código, porque cada servicio ya usa solo su esquema. |
| MySQL (el del laboratorio) | Menor soporte de JSONB y de funciones de fechas; PostgreSQL es más común en microservicios. |

## Consecuencias

- **Positivas:**
  - Poca RAM.
  - Respaldos en un solo lugar.
  - Integridad referencial completa dentro de cada servicio.
- **Negativas:**
  - Sin integridad referencial entre servicios: un `id_cliente` en `procesos` podría apuntar a un cliente borrado. Se mitiga borrando de forma lógica (estado `INACTIVO`) y con eventos.
