# ADR 0001: Microservicios políglotas (Java + Python)

- **Estado:** aceptada
- **Fecha:** 2026-10-15

## Contexto

El proyecto empezó como un monolito Spring Boot (gabinete de abogados en tres capas). El producto necesita:

- Un núcleo transaccional: procesos, términos, honorarios.
- Vigilar un portal web sin API oficial (Rama Judicial).
- Generar textos con IA.

Además, el curso pide una arquitectura de microservicios.

## Decisión

Microservicios **políglotas**:
- **Java 17 / Spring Boot 3** para `gateway`, `identidad`, `gabinete` y `procesos`.
- **Python / FastAPI** para `vigilancia`, `notificaciones` y `asistente-ia`.

## Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Monolito modular | Más simple, pero no cumple el objetivo de microservicios y deja scraping e IA en Java. |
| Microservicios solo Java con Spring Cloud completo | Un solo lenguaje, pero más RAM por servicio y menos herramientas para scraping e IA. |

## Consecuencias

- **Positivas:**
  - Cada servicio usa el ecosistema más adecuado.
  - Los servicios Python son livianos.
  - Un fallo en la vigilancia no afecta el registro de procesos.
- **Negativas:**
  - Dos stacks que mantener.
  - Hacen falta contratos claros (OpenAPI y eventos) y pruebas de integración entre servicios.
- **Obligaciones:** contrato primero (`docs/contratos/`), un esquema de BD por servicio y nada de llaves foráneas entre esquemas.
