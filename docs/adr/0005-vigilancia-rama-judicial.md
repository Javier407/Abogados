# ADR 0005: Vigilancia de la Rama Judicial detrás de una interfaz

- **Estado:** aceptada en F3 (2026-10-15), con la consulta real limitada a los radicados que la firma marque
- **Fecha:** 2026-10-15

## Contexto

El problema central del producto es que los avances solo se ven en el portal **Consulta de Procesos** de la Rama Judicial, ingresando los 23 dígitos del radicado. **No encontramos una API oficial documentada.** El portal hace consultas internas que devuelven datos estructurados (despacho, sujetos procesales, actuaciones), y existen servicios de terceros que los extraen.

## Decisión

El servicio `vigilancia` (Python) define una interfaz **`FuenteRamaJudicial`** con una operación `consultar(radicado) → actuaciones`. Se pueden enchufar varias implementaciones sin cambiar el resto del sistema. Cada radicado elige su fuente:

| Fuente | Cómo funciona | Estado |
|---|---|---|
| `MANUAL` | No se consulta: el dependiente registra las actuaciones a mano | Por defecto para todo proceso nuevo |
| `SIMULADA` | Actuaciones escritas en la pantalla de vigilancia | Implementada; para demostraciones y pruebas |
| `PORTAL_HTTP` | Llama a los endpoints internos del portal con httpx | Implementada; solo si alguien la elige para un radicado real |
| `PORTAL_NAVEGADOR` | Abrir el portal con Playwright como una persona | Respaldo futuro si `PORTAL_HTTP` deja de funcionar |
| `TERCERO` | Servicio externo de scraping | Descartada por ahora (costo y datos de clientes fuera del sistema) |

**Cómo funciona `PORTAL_HTTP`**, comprobado el 2026-10-15 con una sola consulta manual:

1. `GET https://consultaprocesos.ramajudicial.gov.co:448/api/v2/Procesos/Consulta/NumeroRadicacion?numero=<23 dígitos>&SoloActivos=false&pagina=1` devuelve los procesos con ese radicado (`idProceso`, `despacho`, `esPrivado`, `fechaUltimaActuacion`).
2. `GET .../api/v2/Proceso/Actuaciones/<idProceso>?pagina=1` devuelve las 40 actuaciones más recientes (`idRegActuacion`, `fechaActuacion`, `actuacion`, `anotacion`, `fechaRegistro`).

`idRegActuacion` identifica cada actuación; con él se detectan las nuevas sin duplicar. Cada consulta tarda unos 15 segundos.

**Reglas de uso moderado:**

- Máximo una consulta cada 12 horas por radicado (por defecto, cada 24).
- Una consulta a la vez, con 10 segundos de pausa entre radicados.
- Se identifica con un User-Agent propio.

**Línea base:** la primera consulta exitosa con una fuente guarda el historial sin avisar. Solo se avisan las actuaciones que aparecen después.

## Hallazgo importante: radicados de prueba que existen de verdad

Los códigos de despacho de la base de pruebas son reales. Por eso un radicado "ficticio" puede coincidir con un proceso verdadero de otras personas. El de prueba `11001-31-03-032-2024-00458-00` existe en el portal, con partes reales.

En consecuencia:

- Ningún radicado se consulta en el portal por defecto.
- La pantalla pide confirmación antes de activar `PORTAL_HTTP`.
- Los radicados de la base de pruebas deben usar `MANUAL` o `SIMULADA`.

## Consecuencias

- **Positivas:**
  - El producto no depende de una sola forma de obtener los datos.
  - Se puede demostrar el flujo completo sin tocar el portal.
- **Negativas:**
  - El portal puede cambiar sus endpoints sin aviso. Si pasa, la consulta falla, el radicado acumula fallos y la pantalla lo muestra en alerta desde 3 fallos seguidos.
- **Pendiente:**
  - Revisar formalmente los términos de uso del portal.
  - Preguntar si la Rama Judicial ofrece un servicio de consulta para entidades antes de vigilar muchos radicados reales.
