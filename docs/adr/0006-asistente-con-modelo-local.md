# ADR 0006: El asistente de redacción usa un modelo local (Ollama)

- **Estado:** aceptada (2026-10-16)
- **Reemplaza:** la decisión de F0 de usar Hermes del servidor para la IA

## Contexto

El asistente redacta borradores (contestación de la demanda, resumen para el cliente) con los datos de un proceso. En F4 revisamos Hermes, que estaba previsto, y encontramos tres problemas:

1. **Su API estaba apagada.** Usarla exigía cambiar la configuración de otro proyecto y reiniciarlo.
2. **Hermes es un agente con herramientas** (terminal y navegador), no solo un modelo. El expediente trae textos de terceros, por ejemplo las anotaciones del portal de la Rama Judicial. Un texto malicioso podría darle instrucciones al agente para ejecutar comandos en el servidor (inyección de instrucciones).
3. **El modelo de Hermes corre fuera del servidor** (Nous Portal), así que los datos de los clientes salen igual.

## Opciones

| Opción | Pros | Contras |
|---|---|---|
| API de Claude | Mejor redacción jurídica; sin herramientas | Costo por uso; los datos salen a un tercero |
| Hermes | Ya estaba instalado | Agente con terminal; datos fuera; cambiar otro proyecto |
| **Ollama local (elegida por Javier)** | Los datos no salen del servidor; sin costo; el modelo no tiene herramientas | Sin GPU es lento (minutos por borrador); modelo pequeño (redacta peor); ~2–3 GB de RAM |
| Solo plantillas | Simple, sin riesgos | No redacta |

## Decisión

- **Modelo:**
  - `ollama/ollama:0.5.4` con `qwen2.5:3b`, que redacta bien en español para su tamaño.
  - Límite de 3 GB de RAM y 3 CPU.
  - Se descarga de la memoria tras 30 minutos sin uso.
- **El servicio `asistente-ia`:**
  - Arma el caso con datos de `procesos` y `gabinete`.
  - Pone el encabezado (despacho, radicado, partes, abogado y tarjeta profesional) con datos del sistema; el modelo nunca lo escribe, para que no invente un radicado ni un juzgado.
  - Pide al modelo solo el cuerpo, con la regla de marcar `[COMPLETAR: ...]` lo que no sepa y de tratar el texto del expediente como datos, no como instrucciones.
  - No le envía el nombre del cliente.
- **Respuesta:**
  - El texto llega a la pantalla por partes (*Server-Sent Events* sobre un POST), porque en CPU tarda.
  - Si el modelo no está o falla, se entrega la plantilla. Si falla a mitad, se conserva lo escrito.
- **Proveedor intercambiable:** el modelo está detrás de un puerto (`ModeloLenguaje`). Cambiar a otro proveedor es escribir otro adaptador.

## Consecuencias

- Los borradores son puntos de partida, no escritos listos para radicar: la pantalla lo dice y el abogado los edita.
- Mientras redacta, el modelo ocupa hasta 3 CPU de la VM. Un solo borrador a la vez (`OLLAMA_NUM_PARALLEL=1`).
