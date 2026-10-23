#!/bin/sh
# Vigía del servidor (F11). Lo ejecuta cada 5 minutos el timer de systemd abogados-vigia (ver instalar.sh).
#
#   vigia.sh           revisa y avisa por Telegram solo cuando algo cambia (problema nuevo o resuelto)
#   vigia.sh estado    solo muestra la revisión, sin avisar
#   vigia.sh probar    envía un mensaje de prueba
#
# Revisa: contenedores corriendo y sanos, disco, memoria, edad del último respaldo, eventos atascados en los
# outbox, colas de RabbitMQ sin consumidor o acumuladas y la app por HTTPS. Desde F15 también el respaldo cifrado de
# las llaves (y lo crea si cambiaron), la prueba semanal de restauración y los errores en los registros de cada
# servicio (los guarda en operacion.errores). Una vez al día (después de las 7:00)
# manda un resumen aunque todo esté bien, para saber que el vigía sigue vivo.
#
# Cada revisión queda en operacion.revisiones (90 días) y cada problema en operacion.incidentes, desde que aparece
# hasta que se resuelve (F12): lo muestra la pantalla "Servidor" del administrador.
#
# Avisa a las cuentas ADMIN activas que vincularon Telegram en la app (notificaciones.preferencias). Si la base
# no responde, usa los destinatarios de la última revisión. El token del bot sale de infra/.env y no se muestra.
set -u

INFRA="$(cd "$(dirname "$0")/.." && pwd)"
ESTADO="${XDG_STATE_HOME:-$HOME/.local/state}/abogados-vigia"
mkdir -p "$ESTADO" && chmod 700 "$ESTADO"
cd "$INFRA" || exit 1
MODO="${1:-revisar}"

UMBRAL_DISCO=85            # % de uso del disco raíz
UMBRAL_MEMORIA_MB=500      # memoria disponible mínima
UMBRAL_RESPALDO_HORAS=26   # el respaldo es diario a las 02:30
UMBRAL_COLA=100            # mensajes esperando en una cola
UMBRAL_ERRORES=20          # líneas de error de un servicio en una vuelta (F15)

problemas=""
# agregar CLAVE MENSAJE: la clave identifica el problema (el mensaje puede cambiar, p. ej. el % de disco).
TAB=$(printf '\t')
agregar() { problemas="${problemas}${1}${TAB}${2}
"; }

psql_q() {
  timeout 20 docker compose exec -T postgres sh -c 'psql -tA -U "$POSTGRES_USER" -d "$POSTGRES_DB"' 2>/dev/null
}

# psql_v -v nombre=valor ... < sql: los valores van como variables de psql (:'nombre'), nunca pegados en el SQL.
psql_v() {
  timeout 20 docker compose exec -T postgres sh -c \
    'psql -tA -q -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"' sh "$@" 2>/dev/null
}

# ---------------------------------------------------------------- revisiones

esperados=$(docker compose config --services 2>/dev/null)
estado_ps=$(docker compose ps -a --format '{{.Service}}|{{.State}}|{{.Health}}' 2>/dev/null)
for s in $esperados; do
  fila=$(printf '%s\n' "$estado_ps" | grep "^$s|" | head -1)
  estado=$(printf '%s' "$fila" | cut -d'|' -f2)
  salud=$(printf '%s' "$fila" | cut -d'|' -f3)
  if [ "$estado" != "running" ]; then
    agregar "contenedor-$s" "El contenedor $s no está corriendo (${estado:-no existe})"
  elif [ -n "$salud" ] && [ "$salud" != "healthy" ] && [ "$salud" != "starting" ]; then
    agregar "contenedor-$s" "El contenedor $s no está sano ($salud)"
  fi
done

disco=$(df -P / | awk 'NR==2 {gsub("%", "", $5); print $5}')
[ "${disco:-0}" -ge "$UMBRAL_DISCO" ] && agregar disco "Disco al ${disco} % (límite ${UMBRAL_DISCO} %)"

memoria=$(awk '/MemAvailable/ {print int($2 / 1024)}' /proc/meminfo)
[ "${memoria:-0}" -lt "$UMBRAL_MEMORIA_MB" ] && agregar memoria "Memoria disponible baja: ${memoria} MB"

ultimo=$(ls -1t respaldos/gabinete-*.dump 2>/dev/null | head -1)
horas=""
if [ -z "$ultimo" ]; then
  agregar respaldo "No hay respaldos de la base en infra/respaldos"
else
  horas=$(( ( $(date +%s) - $(stat -c %Y "$ultimo") ) / 3600 ))
  [ "$horas" -ge "$UMBRAL_RESPALDO_HORAS" ] && agregar respaldo "El último respaldo tiene ${horas} horas ($(basename "$ultimo"))"
fi

atascados=$(printf '%s\n' "SELECT (SELECT count(*) FROM procesos.outbox WHERE enviado_en IS NULL AND creado < now() - interval '10 minutes')
  + (SELECT count(*) FROM vigilancia.outbox WHERE enviado_en IS NULL AND creado < now() - interval '10 minutes');" | psql_q)
if [ -z "$atascados" ]; then
  agregar base "La base de datos no responde"
elif [ "$atascados" -gt 0 ]; then
  agregar outbox "$atascados evento(s) llevan más de 10 minutos sin salir del outbox (¿RabbitMQ?)"
fi

colas=$(timeout 40 docker compose exec -T rabbitmq rabbitmqctl -q list_queues name messages consumers 2>/dev/null)
if [ -z "$colas" ]; then
  agregar rabbitmq "RabbitMQ no responde"
else
  for cola in notificaciones.avisos procesos.actuaciones vigilancia.procesos; do
    fila=$(printf '%s\n' "$colas" | awk -v c="$cola" '$1 == c {print $2, $3}')
    mensajes=${fila% *}; consumidores=${fila#* }
    if [ -z "$fila" ]; then
      agregar "cola-$cola" "No existe la cola $cola"
    elif [ "$consumidores" -eq 0 ]; then
      agregar "cola-$cola" "La cola $cola no tiene quién la atienda ($mensajes mensaje(s) esperando)"
    elif [ "$mensajes" -ge "$UMBRAL_COLA" ]; then
      agregar "cola-$cola" "La cola $cola acumula $mensajes mensajes"
    fi
  done
fi

url=$(sed -n 's/^URL_PUBLICA=//p' .env | tail -1)
app_ok=true
if [ -n "$url" ]; then
  codigo=$(curl -s -o /dev/null -m 20 -w '%{http_code}' "$url/")
  [ "$codigo" = "200" ] || { app_ok=false; agregar https "La app no responde por HTTPS ($url → ${codigo:-sin respuesta})"; }
fi

# F15: llaves respaldadas y cifradas (respaldo/respaldar-llaves.sh crea un paquete nuevo si cambiaron o cada 7 días).
if [ ! -f secretos/respaldo-certificado.pem ]; then
  agregar llaves "Falta el certificado para cifrar el respaldo de las llaves (ver docs/06-recuperacion.md)"
else
  [ "$MODO" = revisar ] && { sh respaldo/respaldar-llaves.sh > /dev/null 2>&1 || agregar llaves "No se pudo crear el respaldo cifrado de las llaves"; }
  paquete=$(ls -1t respaldos/llaves-*.p7m 2>/dev/null | head -1)
  if [ -z "$paquete" ] || [ -n "$(find "$paquete" -mtime +8)" ]; then
    agregar llaves "El respaldo cifrado de las llaves tiene más de 8 días o no existe"
  fi
fi

# F15: la prueba semanal de restauración de la base (la hace el contenedor respaldo los domingos).
prueba=respaldos/prueba-restauracion.txt
if [ ! -f "$prueba" ] || [ -n "$(find "$prueba" -mtime +8)" ]; then
  agregar restauracion "La prueba de restauración de la base no se ha hecho en los últimos 8 días"
elif grep -q ' FALLO ' "$prueba"; then
  agregar restauracion "La última prueba de restauración de la base falló: $(cut -c1-150 "$prueba")"
fi

# F15: líneas de error de los registros de cada servicio desde la vuelta anterior (hasta 30 por servicio). Se guardan
# en operacion.errores; una ráfaga (UMBRAL_ERRORES o más en una vuelta) es un problema.
errores=""
if [ "$MODO" = revisar ]; then
  desde=$(cat "$ESTADO/errores-desde" 2>/dev/null || echo "5m")
  ahora_iso=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  for s in $esperados; do
    lineas=$(timeout 30 docker compose logs --since "$desde" --no-color --no-log-prefix "$s" 2>/dev/null \
      | grep -E '(^|[ [])(ERROR|FATAL|CRITICAL)([] :]|$)|\[error\]|Traceback \(most recent' | cut -c1-400)
    [ -z "$lineas" ] && continue
    n=$(printf '%s\n' "$lineas" | wc -l)
    [ "$n" -ge "$UMBRAL_ERRORES" ] && agregar "errores-$s" "El servicio $s registró $n errores en los últimos minutos"
    errores="${errores}$(printf '%s\n' "$lineas" | tail -30 | sed "s/^/$s$TAB/")
"
  done
  printf '%s\n' "$ahora_iso" > "$ESTADO/errores-desde"
fi

problemas=$(printf '%s' "$problemas" | sed '/^$/d' | sort)

# ---------------------------------------------------------------- avisos

destinatarios() {
  lista=$(printf '%s\n' "SELECT DISTINCT p.destino FROM notificaciones.preferencias p
    JOIN identidad.usuarios u ON u.id_usuario = p.id_persona AND u.activo
    JOIN identidad.usuario_rol ur ON ur.id_usuario = u.id_usuario
    JOIN identidad.roles r ON r.id_rol = ur.id_rol AND r.codigo = 'ADMIN'
    WHERE p.canal = 'TELEGRAM' AND p.activo AND p.destino IS NOT NULL;" | psql_q)
  if [ -n "$lista" ]; then
    printf '%s\n' "$lista" > "$ESTADO/destinatarios" && chmod 600 "$ESTADO/destinatarios"
  fi
  cat "$ESTADO/destinatarios" 2>/dev/null
}

enviar() {
  token=$(sed -n 's/^TELEGRAM_TOKEN=//p' .env | tail -1)
  if [ -z "$token" ]; then echo "Sin TELEGRAM_TOKEN: no se puede avisar"; return 1; fi
  for chat in $(destinatarios); do
    # La URL con el token va por la configuración de curl (entrada estándar), no en la línea de comandos.
    codigo=$(printf 'url = "https://api.telegram.org/bot%s/sendMessage"\n' "$token" \
      | curl -s -m 20 -o /dev/null -w '%{http_code}' -K - --data-urlencode "chat_id=$chat" --data-urlencode "text=$1")
    if [ "$codigo" = "200" ]; then echo "Aviso enviado por Telegram"; else echo "Telegram no aceptó el aviso (HTTP ${codigo:-sin respuesta})"; fi
  done
}

touch "$ESTADO/problemas"
printf '%s\n' "$problemas" | sed '/^$/d' > "$ESTADO/actuales"
# Se compara por clave (primera columna): nuevo = está ahora y no antes; resuelto = estaba antes y ya no.
# (FILENAME, no NR == FNR: con el primer archivo vacío, NR == FNR también valdría para el segundo)
nuevos=$(awk -F'\t' 'FILENAME == ARGV[1] {antes[$1]; next} !($1 in antes) {print $2}' "$ESTADO/problemas" "$ESTADO/actuales")
resueltos=$(awk -F'\t' 'FILENAME == ARGV[1] {ahora[$1]; next} !($1 in ahora) {print $2}' "$ESTADO/actuales" "$ESTADO/problemas")
abiertos=$(cut -f2 "$ESTADO/actuales")

case "$MODO" in
  estado)
    if [ -z "$abiertos" ]; then echo "Todo en orden (disco ${disco} %, memoria disponible ${memoria} MB)"; else printf '%s\n' "$abiertos"; fi
    exit 0 ;;
  probar)
    enviar "🔔 Gestión Jurídica: mensaje de prueba del vigía del servidor. Los avisos llegarán aquí."
    exit 0 ;;
esac

# Historial (F12). Los incidentes se concilian con la lista actual (no con los cambios de esta vuelta), así se
# corrigen solos si una vuelta no pudo escribir porque la base no respondía.
psql_v -v "disco=${disco:-0}" -v "memoria=${memoria:-0}" -v "respaldo=$horas" -v "app_ok=$app_ok" \
  -v "problemas=$(wc -l < "$ESTADO/actuales")" -v "detalle=$abiertos" \
  -v "actuales=$(cat "$ESTADO/actuales")" <<'SQL' || echo "No se pudo guardar la revisión en la base"
INSERT INTO operacion.revisiones (disco, memoria_mb, respaldo_horas, app_ok, problemas, detalle)
VALUES (:'disco'::smallint, :'memoria'::int, NULLIF(:'respaldo', '')::int, :'app_ok'::boolean,
        :'problemas'::smallint, NULLIF(:'detalle', ''));
WITH actuales AS (
  SELECT split_part(l, E'\t', 1) AS clave, split_part(l, E'\t', 2) AS mensaje
  FROM unnest(string_to_array(:'actuales', E'\n')) AS l WHERE l <> ''
), cerrados AS (
  UPDATE operacion.incidentes SET fin = now()
  WHERE fin IS NULL AND clave NOT IN (SELECT clave FROM actuales) RETURNING 1
)
INSERT INTO operacion.incidentes (clave, mensaje)
SELECT left(clave, 80), left(mensaje, 300) FROM actuales a
WHERE NOT EXISTS (SELECT 1 FROM operacion.incidentes i WHERE i.fin IS NULL AND i.clave = left(a.clave, 80));
DELETE FROM operacion.revisiones WHERE momento < now() - interval '90 days';
DELETE FROM operacion.incidentes WHERE fin < now() - interval '365 days';
SQL

# Errores de los servicios (F15), aparte: si falla, la revisión ya quedó guardada.
psql_v -v "errores=$errores" <<'SQL' || echo "No se pudieron guardar los errores de los servicios"
INSERT INTO operacion.errores (servicio, linea)
SELECT left(split_part(l, E'\t', 1), 40), left(substr(l, strpos(l, E'\t') + 1), 500)
FROM unnest(string_to_array(:'errores', E'\n')) AS l WHERE l LIKE '%' || E'\t' || '%';
DELETE FROM operacion.errores WHERE momento < now() - interval '30 days';
SQL

if [ -n "$(printf '%s' "$nuevos" | sed '/^$/d')" ]; then
  enviar "⚠️ Gestión Jurídica — problema en el servidor:
$(printf '%s\n' "$nuevos" | sed '/^$/d; s/^/• /')"
fi
if [ -n "$resueltos" ]; then
  enviar "✅ Gestión Jurídica — resuelto:
$(printf '%s\n' "$resueltos" | sed 's/^/• /')"
fi
mv "$ESTADO/actuales" "$ESTADO/problemas"

hoy=$(date +%F)
if [ "$(date +%H)" -ge 7 ] && [ "$(cat "$ESTADO/resumen" 2>/dev/null)" != "$hoy" ]; then
  if [ -z "$problemas" ]; then
    enviar "🟢 Gestión Jurídica — resumen diario: todo en orden. Disco ${disco} %, memoria disponible ${memoria} MB, último respaldo $(basename "${ultimo:-ninguno}")."
  else
    enviar "🟠 Gestión Jurídica — resumen diario, problemas abiertos:
$(printf '%s\n' "$abiertos" | sed 's/^/• /')"
  fi
  echo "$hoy" > "$ESTADO/resumen"
fi

if [ -z "$problemas" ]; then echo "Revisión: todo en orden"; else echo "Revisión: $(printf '%s\n' "$problemas" | wc -l) problema(s)"; fi
