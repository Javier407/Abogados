#!/bin/sh
# Copias de seguridad de PostgreSQL para el contenedor "respaldo" (imagen postgres:16-alpine).
#   respaldar.sh            bucle: una copia al arrancar si no hay una reciente y otra cada día a HORA_RESPALDO
#   respaldar.sh ahora      una copia ya
#   respaldar.sh probar     restaura la copia más reciente en una base temporal, cuenta filas y la borra
# Formato custom de pg_dump (-Fc): comprimido y restaurable tabla por tabla con pg_restore.
set -eu

DIR=/respaldos
HORA="${HORA_RESPALDO:-02:30}"
RETENER="${RETENER_DIAS:-14}"
export PGHOST=postgres PGUSER="$POSTGRES_USER" PGPASSWORD="$POSTGRES_PASSWORD"

respaldar() {
  archivo="$DIR/gabinete-$(date +%Y%m%d-%H%M%S).dump"
  pg_dump -d "$POSTGRES_DB" -Fc -f "$archivo.parcial"
  pg_restore --list "$archivo.parcial" > /dev/null      # el archivo se puede leer de vuelta
  mv "$archivo.parcial" "$archivo"
  chmod 600 "$archivo"
  find "$DIR" -name 'gabinete-*.dump' -mtime +"$RETENER" -delete
  echo "$(date '+%F %T') respaldo listo: $archivo ($(du -h "$archivo" | cut -f1))"
}

probar() {
  ultimo=$(ls -1t "$DIR"/gabinete-*.dump 2>/dev/null | head -1)
  [ -n "$ultimo" ] || { echo "No hay respaldos en $DIR"; exit 1; }
  echo "Restaurando $ultimo en la base temporal restauracion_prueba"
  dropdb --if-exists restauracion_prueba
  createdb restauracion_prueba
  # --no-owner: los usuarios svc_* pueden no existir en otro servidor; los datos son lo que se verifica
  pg_restore --no-owner --no-privileges -d restauracion_prueba "$ultimo"
  psql -d restauracion_prueba -tA -c "
    SELECT 'procesos ' || count(*) FROM procesos.procesos UNION ALL
    SELECT 'clientes ' || count(*) FROM gabinete.clientes UNION ALL
    SELECT 'terminos ' || count(*) FROM procesos.terminos UNION ALL
    SELECT 'usuarios ' || count(*) FROM identidad.usuarios"
  dropdb restauracion_prueba
  echo "Restauración correcta (base temporal borrada)"
}

case "${1:-}" in
  ahora) respaldar; exit 0 ;;
  probar) probar; exit 0 ;;
esac

mkdir -p "$DIR"
# Al arrancar (por ejemplo, después de un corte de luz), copia inmediata si la última tiene más de un día.
if [ -z "$(find "$DIR" -name 'gabinete-*.dump' -mmin -1440 2>/dev/null)" ]; then
  respaldar || echo "$(date '+%F %T') ERROR: falló el respaldo inicial"
fi
hecho=""
while true; do
  hoy=$(date +%F)
  if [ "$(date +%H:%M)" = "$HORA" ] && [ "$hecho" != "$hoy" ]; then
    respaldar || echo "$(date '+%F %T') ERROR: falló el respaldo diario"
    hecho="$hoy"
  fi
  sleep 30
done
