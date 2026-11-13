# 15. Toga: avisos y consultas por Telegram y en el portal

**En el portal:** el equipo de la firma ve abajo a la derecha el botón con la cara de Toga. Abre una conversación con las mismas preguntas que el bot (sus procesos, sus términos, sus audiencias o un radicado suyo) y las mismas reglas: solo personal activo, solo lo suyo y solo lectura. La pregunta va por el gateway (`POST /api/notificaciones/toga`), así que queda en la bitácora de auditoría, y también en `consultas_bot` con el chat `portal`. Siempre está encendida; el interruptor `TELEGRAM_CONSULTAS` solo afecta a Telegram.

El bot de la firma (el mismo de los avisos desde F3) también puede **responder consultas** del equipo sobre sus procesos. Habla como **Toga**, la asistente del portal. Las consultas vienen **apagadas** y se encienden con `TELEGRAM_CONSULTAS=true` en `infra/.env`.

## 1. Qué responde

| Comando | Qué dice |
|---|---|
| `/mis_procesos` | Sus procesos abiertos (a cargo y de apoyo), con tipo, etapa y el próximo vencimiento |
| `/terminos` | Sus términos pendientes, del más urgente al más lejano (los vencidos primero) |
| `/audiencias` | Sus audiencias de las próximas dos semanas, con hora y lugar |
| `/proceso <radicado>` | Cómo va **un proceso suyo**: tipo, etapa y términos pendientes |
| `/ayuda` (o cualquier otra cosa) | Qué puede preguntar |

También entiende frases simples («¿qué términos me vencen?», «mis audiencias») y un radicado suelto de 23 dígitos. No hay un modelo de lenguaje en medio: el bot reconoce la intención por palabras clave y los datos exactos los pone el sistema (el mismo principio de `docs/08-ia-riesgos.md`).

Solo **consulta**: no crea, cambia ni borra nada.

## 2. A quién le responde

1. **Solo en chat privado.** En un grupo no contesta nada, ni siquiera un error.
2. **Solo a un chat vinculado** (Portal → Avisos → Vincular Telegram, con el código de un uso). Si un chat aparece a nombre de dos personas, no es de nadie.
3. **Solo a personas de la firma que sigan activas.** Se comprueba en cada consulta contra `gabinete` (`GET /abogados/{id}`). Un cliente que vinculó Telegram para sus avisos no recibe datos por aquí: se le dice que use el portal.
4. **Solo lo suyo.** Los procesos en los que está (a cargo, apoyo o dependiente), los términos a su nombre y sus audiencias. Un radicado que no es suyo responde igual que uno que no existe.
5. **Límite:** 10 consultas por persona y minuto.
6. Si `gabinete` no responde, no arriesga: dice que intente más tarde.

## 3. Qué datos salen por Telegram

Lista blanca: radicado, asunto, tipo de proceso, rama, etapa, estado, términos (qué hay que hacer, cuándo vence, cuánto falta) y audiencias (fecha, hora, lugar, descripción). **No** salen cuantías, partes, documentos, datos del cliente ni errores internos.

Telegram guarda los mensajes en sus servidores (no son chats cifrados de extremo a extremo). La política de privacidad (versión 1.2) lo dice; antes de encender las consultas conviene que la firma lo revise a la luz de la Ley 1581 de 2012.

## 4. Cómo se registra

Las consultas del bot **no pasan por el gateway**: el servicio `notificaciones` pide los datos directo a `procesos` y `gabinete` con su token de servicio (que solo permite leer). Por eso no quedan en la bitácora encadenada (`docs/14-auditoria.md`). Su registro es `notificaciones.consultas_bot` (migración `020`): momento, persona, chat, comando y resultado (`RESPONDIDA`, `SIN_VINCULO`, `NO_AUTORIZADO`, `LIMITE`, `NO_ENCONTRADO`, `ERROR`). **No guarda el texto** que escribió la persona ni la respuesta. El servicio solo agrega y lee.

## 5. Cómo encenderlo

1. Aplicar la migración `020` (crea el registro y el índice que impide un chat a nombre de dos personas; si ya hay chats repetidos, avisa y no crea el índice).
2. Desplegar `notificaciones` con este cambio.
3. En `infra/.env`: `TELEGRAM_CONSULTAS=true` (lo escribe Javier). Reiniciar `notificaciones`.
4. Opcional, en @BotFather: nombre «Lawy» (`/setname`), foto `docs/marca/lawy-512.png` (`/setuserpic`) y la lista de comandos:

```text
mis_procesos - Sus procesos abiertos
terminos - Sus términos pendientes
audiencias - Sus audiencias de dos semanas
proceso - Cómo va un proceso suyo (con el radicado)
ayuda - Qué puede preguntar
```

Es un solo bot y un solo token: Telegram solo deja que un proceso lea los mensajes de cada bot. Por defecto ese proceso es `notificaciones`; con `TELEGRAM_ESCUCHA=false` los lee Talki y le pregunta a Lexora por `/api/canales/*` (docs/16-integracion-talki.md, §2.8), con el mismo vínculo y las mismas respuestas. El vigía (`infra/monitoreo/vigia.sh`) usa el mismo token solo para enviar.

## 6. Pruebas

`servicios/notificaciones/tests/test_consultas_bot.py`: comandos y frases, procesos y términos propios, radicado ajeno, chat sin vínculo, cliente e inactivo rechazados, `gabinete` caído, límite por minuto, grupo ignorado, token de servicio en cada lectura, el token del bot fuera del registro y un chat a nombre de dos personas.
