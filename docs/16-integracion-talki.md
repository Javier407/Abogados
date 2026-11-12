# 16. Integración con Talki (whatsapp-ai-saas): Toga en todos los canales

**Objetivo:** que Toga, la asistente de Lexora, responda por WhatsApp, Instagram y cualquier canal que maneje Talki, sin que Lexora se exponga a internet.

**Reparto de papeles:**

- **Talki** (proyecto `whatsapp-ai-saas`, en el mismo servidor) recibe los mensajes de cada canal, decide con sus flujos qué hacer y responde. Su base de conocimiento (RAG) aprende lo general de la firma: servicios, horarios, sedes, preguntas frecuentes.
- **Lexora** es la fuente de verdad de los procesos. Toga solo responde a personas vinculadas, con sus propios procesos y en solo lectura.
- **Los datos de los procesos nunca entran al RAG de Talki ni a un modelo de lenguaje.** Se consultan en vivo cada vez.

```text
WhatsApp / Instagram / (otros)                          Telegram
        │ webhook de Meta (ngrok)                            │ el bot de Lexora sigue igual
        ▼                                                    ▼
   Talki gateway ─▶ flow-engine ── flujo «Toga» ──▶ api_call ──▶ Lexora gateway ──▶ notificaciones
                        │                         (red interna del servidor,      (ConsultasBot: mismas
                        └─ RAG / LLM para lo        clave compartida)               reglas que Telegram
                           general de la firma                                       y el portal)
```

## 1. Contrato de Lexora para canales externos

Base: `http://abogados-gateway:8080/api/canales` (red interna de Docker; ver §3).

Todas las llamadas llevan la cabecera `X-Canal-Clave: <CANAL_CLAVE>`, una clave compartida que Javier escribe en el `.env` de cada proyecto. Sin ella, o con una clave equivocada, la respuesta es 401. Si Lexora no tiene `CANAL_CLAVE`, la respuesta es 503 (canales apagados).

Otros errores: 400 si falta un campo, el canal no es `WHATSAPP` ni `INSTAGRAM`, el `idExterno` trae caracteres raros (se permiten letras, números y `_ . : + -`, de 3 a 64) o el texto pasa de 300 caracteres. Lo demás siempre es 200 con un mensaje listo para enviar.

### Vincular a una persona

Primero la persona entra al portal de Lexora → **Avisos** → **Vincular WhatsApp** (o Instagram). Lexora le muestra el mensaje que debe escribir, con un código de 8 caracteres de un solo uso, válido 24 horas y solo para ese canal: `vincular K7P2M9QX`. No importan las mayúsculas ni los guiones o espacios dentro del código. Cada chat tiene 5 intentos por hora; después, Lexora dice que espere aunque el código sea bueno.

```http
POST /api/canales/vinculo
X-Canal-Clave: …
{ "canal": "WHATSAPP", "idExterno": "573001234567", "codigo": "K7P2M9QX" }

200 { "vinculado": true,  "mensaje": "Listo: este chat quedó vinculado a Lexora. Pregúnteme por sus procesos…" }
200 { "vinculado": false, "mensaje": "Ese código no sirve o ya venció. Genere otro en el portal → Avisos." }
```

### Preguntarle a Toga

```http
POST /api/canales/toga
X-Canal-Clave: …
{ "canal": "WHATSAPP", "idExterno": "573001234567", "texto": "¿qué términos me vencen?" }

200 { "vinculado": true,  "respuesta": "Tiene 2 término(s) pendiente(s)…" }
200 { "vinculado": false, "respuesta": "Este chat no está vinculado a Lexora. Si trabaja en la firma…" }
```

- `canal`: `WHATSAPP`, `INSTAGRAM` (y los que se agreguen).
- `idExterno`: el identificador de quien escribe en ese canal (`wa_id` en WhatsApp, IGSID en Instagram).
- `texto`: hasta 300 caracteres.
- La respuesta es texto plano listo para enviar (saltos de línea con `\n`, sin formato propio de ningún canal).
- Toga entiende: sus procesos, sus términos, sus audiencias, un radicado de 23 dígitos y frases simples. A todo lo demás contesta con la ayuda.
- Lexora registra cada consulta en `notificaciones.consultas_bot` (chat `whatsapp:<idExterno>`, sin el texto) y en su bitácora de auditoría (pasa por el gateway).
- Si la persona vuelve a vincular desde otro número, el chat anterior deja de estar vinculado: una persona tiene un solo chat por canal.
- Por estos canales **no salen avisos**: Toga solo responde cuando la persona pregunta.

## 2. Lo que hay que hacer en Talki (para el asistente de ese proyecto)

1. **Pasar a los flujos quién escribe y por qué canal.** Hoy el `wa_id` está en la sesión pero no en `session.slots`, así que `api_call` no lo puede usar. Agregar al iniciar cada turno los slots `sender_id` (el `wa_id` o el IGSID) y `canal` (`WHATSAPP` o `INSTAGRAM`), con prueba unitaria.
2. **Permitir a `api_call` llamar a Lexora por la red interna.** `_ssrf_guard` bloquea las IP privadas, y eso está bien. Agregar una lista blanca configurable: `API_CALL_HOSTS_PERMITIDOS=abogados-gateway`. Solo esos nombres pasan, y nunca `localhost` ni la IP de metadatos. Agregar pruebas de que todo lo demás sigue bloqueado.
3. **Secretos fuera del JSON del flujo.** Permitir en `headers` valores como `"${env:LEXORA_CANAL_CLAVE}"`, que se resuelven desde el entorno al ejecutar. La clave no debe quedar escrita en la base de datos de flujos ni en los registros.
4. **Leer un campo del JSON de respuesta.** Agregar `response_path` a `api_call` (por ejemplo `"respuesta"`) para guardar solo ese campo en el slot, y que un nodo `message` lo envíe con `{toga_respuesta}`.
5. **Flujo «Toga»** (con el creador de flujos o por la API de `tenant-api`):
   - **disparador:** el texto empieza con `vincular`, o menciona proceso, procesos, término, vence, audiencia, radicado o Toga, o trae 23 dígitos;
   - **si empieza con `vincular`:** `api_call` a `/api/canales/vinculo` con `{canal, idExterno: sender_id, codigo}` y luego un `message` con `{toga_mensaje}`;
   - **en otro caso:** `api_call` a `/api/canales/toga` con `{canal, idExterno: sender_id, texto}` y luego un `message` con `{toga_respuesta}`;
   - **si `api_call` falla:** «Ahora no puedo consultar Lexora. Intente en unos minutos».
   - Lo que no dispare este flujo sigue por el RAG y el LLM de siempre (preguntas generales de la firma).
6. **Privacidad en el registro de conversaciones.** `postgres_conv_log` guarda los turnos. Para el flujo «Toga», no guardar el texto de la respuesta de Lexora (guardar solo «respuesta de Lexora»), porque lleva datos de procesos de la firma.
7. **Instagram.** Usar la API de mensajería de Instagram de Meta: cuenta profesional ligada a una página de Facebook, permiso `instagram_manage_messages` y webhook con `object: "instagram"`. El gateway debe aceptar ese tipo de evento, verificar la firma y encolarlo igual que WhatsApp. El envío se hace por Graph API `/{ig-user-id}/messages`. El mismo flujo «Toga» sirve con `canal = INSTAGRAM`.
8. **Telegram.** Lexora ya tiene su bot (@javier_asistente_ia_bot) con Toga. Telegram solo deja que un proceso lea los mensajes de cada bot. Si Talki quiere Telegram, debe usar **otro bot con otro token**, o se apaga el de Lexora.
9. **Prueba de punta a punta:** webhook firmado simulado («vincular» con un código real generado en Lexora, y luego «mis procesos»), y después con un mensaje real desde un número autorizado en el modo de prueba de Meta.

## 3. Cómo se alcanzan los dos proyectos sin exponer Lexora

- Los dos corren en la misma VM. Se unen con una red de Docker compartida: en `whatsapp-ai-saas/infra/docker-compose.yml`, el servicio `flow-engine` se agrega a la red externa `abogados_default`. Así llega a `http://abogados-gateway:8080` sin salir del servidor.
- Lexora no publica nada nuevo; solo el webhook de Talki sigue público (ngrok), como ya lo está.

## 4. Lo que ya está hecho en Lexora

- `servicios/notificaciones/app/canales.py`: vínculo con código, límite de intentos y la pregunta a Toga (`ConsultasBot.responder_externo`, mismas reglas que Telegram y el portal).
- `app/main.py`: `POST /canales/vinculo` y `POST /canales/toga` con `X-Canal-Clave` (comparación en tiempo constante), y para el portal `GET /notificaciones/canales`, `POST /notificaciones/canales/{canal}/vinculo` y `DELETE /notificaciones/canales/{canal}`.
- Gateway: `/api/canales/**` va a `notificaciones`; solo `POST /api/canales/vinculo` y `POST /api/canales/toga` pasan sin token de usuario.
- Migración `db/migraciones/021-canales-externos.sql`: `notificaciones.vinculos_canal` (códigos) y `notificaciones.canales_externos` (a quién pertenece cada chat), aparte de las preferencias de avisos.
- Portal → **Avisos**: tarjeta «Toga por WhatsApp e Instagram», solo para el equipo y solo si el servidor tiene `CANAL_CLAVE`.
- Política de privacidad 1.3: WhatsApp e Instagram (Meta) y Talki como canales opcionales del equipo.
- Pruebas: `tests/test_canales.py` (notificaciones), `SeguridadTest` (gateway) y `avisos-canales.spec.ts` (portal).

**Para encenderlo:** aplicar la migración 021, desplegar `gateway`, `notificaciones` y `frontend`, y que Javier escriba `CANAL_CLAVE` en `infra/.env` y reinicie `notificaciones`.

**Ojo con la red compartida:** al unir `flow-engine` a `abogados_default`, también ve directo a `notificaciones:8000` y a los demás servicios. No es un hueco (todo lo demás exige token, y `/canales/*` exige la clave), pero Talki debe llamar siempre a `abogados-gateway:8080` para que quede en la bitácora. La lista blanca de `api_call` debe tener solo `abogados-gateway`.

## 5. Lo que hace Javier

- **Clave compartida:** generarla (`openssl rand -hex 32`) y escribirla como `CANAL_CLAVE` en `~/proyectos/abogados/infra/.env` y como `LEXORA_CANAL_CLAVE` en el `.env` de Talki.
- **WhatsApp en Meta:** que el webhook apunte a la URL de ngrok de Talki, el número de prueba activo y su propio número registrado como destinatario de prueba.
- **Instagram:** cuenta profesional ligada a una página de Facebook y permisos de mensajería en la app de Meta.
- **Revisión legal:** los datos de los procesos pasan por los servidores de Meta, igual que con Telegram. Debe revisarlo la firma (Ley 1581).
