#!/bin/sh
# Vigía del servidor (F11). Lo ejecuta cada 5 minutos el timer de systemd abogados-vigia (ver instalar.sh).
#
#   vigia.sh           revisa y avisa por Telegram solo cuando algo cambia (problema nuevo o resuelto)
#   vigia.sh estado    solo muestra la revisión, sin avisar
#   vigia.sh probar    envía un mensaje de prueba
#
# Revisa: contenedores corriendo y sanos, disco, memoria, edad del último respaldo, eventos atascados en los
# outbox, colas de RabbitMQ sin consumidor o acumuladas y la app por HTTPS. Una vez al día (después de las 7:00)
# manda un resumen aunque todo esté bien, para saber que el vigía sigue vivo.
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

problemas=""
# agregar CLAVE MENSAJE: la clave identifica el problema (el mensaje puede cambiar, p. ej. el % de disco).
TAB=$(printf '\t')
agregar() { problemas="${problemas}${1}${TAB}${2}
"; }

psql_q() {
  timeout 20 docker compose exec -T postgres sh -c 'psql -tA -U "$POSTGRES_USER" -d "$POSTGRES_DB"' 2>/dev/null
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
if [ -n "$url" ]; then
  codigo=$(curl -s -o /dev/null -m 20 -w '%{http_code}' "$url/")
  [ "$codigo" = "200" ] || agregar https "La app no responde por HTTPS ($url → ${codigo:-sin respuesta})"
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
