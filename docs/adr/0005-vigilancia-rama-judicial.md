# ADR 0005: Vigilancia de la Rama Judicial detrás de una interfaz

- **Estado:** propuesta (requiere validación legal y técnica en F3)
- **Fecha:** 2026-10-15

## Contexto

El problema central del producto es que los avances solo se ven en el portal **Consulta de Procesos** de la Rama Judicial, ingresando los 23 dígitos del radicado. **No encontramos una API oficial documentada.** El portal hace consultas internas que devuelven datos estructurados (despacho, sujetos procesales, actuaciones), y existen servicios de terceros que los extraen.

## Decisión

El servicio `vigilancia` define una interfaz **`FuenteRamaJudicial`** con una operación `consultar(radicado) → lista de actuaciones`. Se pueden enchufar varias implementaciones sin cambiar el resto del sistema:

| Implementación | Cómo funciona | Pros | Contras |
|---|---|---|---|
| `ManualFuente` | El dependiente judicial registra la actuación a mano | Funciona desde el día 1; sin riesgo legal | No automatiza la vigilancia |
| `PortalHttpFuente` | Llama a los mismos endpoints internos que usa el portal (httpx) | Rápida y liviana | No documentada: puede cambiar sin aviso; revisar los términos de uso |
| `PortalNavegadorFuente` | Abre el portal con Playwright como lo haría una persona | Resiste cambios internos del API | Más lenta y pesada (≈ 300 MB); frágil ante cambios de la página |
| `TerceroFuente` | Servicio externo de scraping (p. ej. Apify) | Mantenimiento a cargo del proveedor | Costo; dependencia externa; datos de clientes salen del sistema |

**Orden de adopción:**
1. `ManualFuente` (F2).
2. `PortalHttpFuente` con consultas espaciadas, por ejemplo una vez al día por radicado y con pausas entre consultas (F3).
3. `PortalNavegadorFuente` como respaldo si la anterior deja de funcionar.

## Consecuencias

- **Positivas:**
  - El producto no depende de una sola forma de obtener los datos.
  - Se puede empezar sin automatización.
- **Negativas:**
  - Hay que detectar cuándo una fuente deja de funcionar (alerta si una consulta falla N veces seguidas).
- **Pendiente antes de F3:** revisar los términos de uso del portal y consultar si la Rama Judicial ofrece un servicio de consulta para entidades. Cualquier consulta automática debe ser moderada para no sobrecargar el portal.
