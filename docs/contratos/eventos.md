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
**Lo consume:** `vigilancia` (F3), que empieza a consultar el radicado.

```json
{
  "id_proceso": 34,
  "radicado": "11001400301520260077700",
  "id_abogado_responsable": 1099887766,
  "ocurrido": "2026-10-15T16:51:39.110456122Z"
}
```

### `actuacion.detectada` (F3)

**Lo publica:** `vigilancia`, cuando aparece una actuación nueva en el portal.
**Lo consumen:** `procesos`, que la registra, y `notificaciones`, que avisa.

```json
{ "radicado": "11001310303220240045800", "fecha": "2026-10-14", "tipo": "AUTO",
  "descripcion": "Fija fecha para audiencia de instrucción y juzgamiento", "fuente": "PORTAL", "ocurrido": "..." }
```

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

### `notificacion.enviada` (F2, implementado)

**Lo publica:** `notificaciones`.
**Uso:** auditoría.

```json
{ "id_notificacion": 40, "canal": "EMAIL", "destinatario": 52345678, "evento": "termino.por_vencer" }
```
