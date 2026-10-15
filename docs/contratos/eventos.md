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

### `termino.por_vencer` y `termino.vencido` (F2)

**Lo publica:** `procesos`, con una tarea diaria.
**Lo consume:** `notificaciones`.

```json
{ "id_termino": 12, "id_proceso": 2, "vence": "2026-10-21", "dias_habiles_restantes": 4,
  "id_abogado": 52345678, "descripcion": "Sustentar el recurso de apelación", "ocurrido": "..." }
```

### `notificacion.enviada` (F2)

**Lo publica:** `notificaciones`.
**Uso:** auditoría.

```json
{ "id_notificacion": 40, "canal": "EMAIL", "destinatario": 52345678, "evento": "termino.por_vencer", "ocurrido": "..." }
```
