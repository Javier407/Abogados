# Contratos de eventos

- **Exchange:** `gabinete.eventos`, tipo `topic`, durable. Lo declara el primer servicio que se conecta.
- **Clave de enrutamiento:** el nombre del evento, por ejemplo `proceso.creado`.
- **Formato:** JSON (`content_type: application/json`) con claves en `snake_case`, para que los servicios Python los lean sin traducir.
- **Fechas:** ISO-8601 en UTC.
- **Entrega:** el mensaje sale **después del commit**, así nunca se anuncia algo que se deshizo. Si RabbitMQ no está disponible, el servicio registra un aviso y la operación sigue. Ese evento se pierde: en una fase posterior se puede agregar una tabla *outbox* si hace falta garantizar la entrega.
- **Consumidores:** cada uno declara su propia cola durable y la enlaza al exchange con las claves que le interesan. Por ejemplo, `vigilancia.procesos` ← `proceso.creado`.

## Eventos

### `proceso.creado` (F1, implementado)

**Lo publica:** `procesos`, al registrar un proceso.
**Lo consume:** `vigilancia`, desde la cola `vigilancia.procesos`. Agrega el radicado con fuente `MANUAL`; la consulta automática se activa en la pantalla de vigilancia.

```json
{
  "id_proceso": 34,
  "radicado": "11001400301520260077700",
  "id_abogado_responsable": 1099887766,
  "ocurrido": "2026-10-15T16:51:39.110456122Z"
}
```

### `actuacion.detectada` (F3, implementado)

**Lo publica:** `vigilancia`, por cada actuación nueva de un radicado con fuente `SIMULADA` o `PORTAL_HTTP`. La primera consulta (línea base) no publica nada.

**Lo consumen:**

- `procesos`, desde la cola `procesos.actuaciones`. La registra como providencia con fuente `VIGILANCIA` y no la duplica, porque usa `referencia`.
- `notificaciones`, desde la cola `notificaciones.avisos`. Le pide a `procesos` el responsable y le avisa en la app, por correo y por Telegram, una sola vez por persona y canal.

```json
{ "radicado": "11001400301520260077700", "id_proceso": 34, "referencia": "SIM:1", "fecha": "2026-10-15",
  "tipo": "AUTO", "actuacion": "Auto requiere al demandante", "anotacion": "Allegue el poder en 5 días",
  "fuente": "SIMULADA", "ocurrido": "2026-10-16T04:56:06Z" }
```

- `tipo`: `SENTENCIA` si el texto lo menciona; si no, `AUTO`.
- `referencia`: `PORTAL:<idRegActuacion>` o `SIM:<id>`.

### `termino.por_vencer` y `termino.vencido` (F2, implementado)

**Lo publica:** `procesos`, cada día hábil a las 7:00 o al pedir la revisión con `POST /terminos/revision`.

- `termino.por_vencer`: términos pendientes que vencen en 5 días hábiles o menos, incluido hoy (`dias_habiles_restantes` = 0).
- `termino.vencido`: términos pendientes que vencieron hace 1 a 3 días hábiles.

**Lo consume:** `notificaciones`, desde la cola `notificaciones.avisos`. Guarda un aviso en la app y envía un correo.

Para no repetir avisos, cada uno lleva la clave `evento:id_termino:persona:canal:día`. Si la revisión corre dos veces el mismo día, no se repiten.

```json
{ "id_termino": 18, "id_proceso": 34, "radicado": "11001400301520260077700",
  "descripcion": "Contestar requerimiento del juzgado", "vence": "2026-10-20", "dias_habiles_restantes": 3,
  "id_abogado": 79123456, "ocurrido": "2026-10-15T12:00:00Z" }
```

### `audiencia.proxima` (F7, implementado)

**Lo publica:** `procesos`, en la misma revisión de cada día hábil a las 7:00 o al pedirla con `POST /audiencias/avisos`. Avisa las audiencias del **próximo día hábil**: el viernes, las del sábado al lunes; antes de un festivo, también las del día siguiente al festivo.

- `id_abogado`: quien atiende la audiencia (la persona de la actuación o, si no se indicó, el responsable del proceso).
- `hora` y `lugar` pueden ser `null`.

**Lo consume:** `notificaciones` (cola `notificaciones.avisos`): aviso en la app, correo y Telegram. La clave `audiencia.proxima:id_actuacion:persona:canal:fecha` evita repetir el aviso; si la audiencia se reprograma a otra fecha, se avisa de nuevo.

```json
{ "id_actuacion": 77, "id_proceso": 34, "radicado": "11001400301520260077700",
  "descripcion": "Audiencia inicial", "fecha": "2026-10-19", "hora": "09:30:00", "lugar": "Sala virtual",
  "id_abogado": 79123456, "ocurrido": "2026-10-18T12:00:00Z" }
```

### `notificacion.enviada` (F2, implementado)

**Lo publica:** `notificaciones`.
**Uso:** auditoría.

```json
{ "id_notificacion": 40, "canal": "EMAIL", "destinatario": 52345678, "evento": "termino.por_vencer" }
```
