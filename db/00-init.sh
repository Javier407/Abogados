#!/bin/sh
# Lo ejecuta la imagen oficial de PostgreSQL la primera vez que se crea el volumen de datos.
# 1) crea un usuario de base de datos por servicio, 2) crea los esquemas, 3) carga los datos de prueba.
set -eu

DIR=/docker-entrypoint-initdb.d
SVC_PASSWORD="${SVC_PASSWORD:-cambiar_esta_clave_tambien}"

psql_run() {
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" "$@"
}

echo ">> Usuarios de base de datos por servicio"
for svc in identidad gabinete procesos vigilancia notificaciones; do
  psql_run -v svc="svc_$svc" -v pass="$SVC_PASSWORD" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN PASSWORD %L', :'svc', :'pass')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'svc') \gexec
SQL
done

echo ">> Esquemas"
for f in "$DIR"/schema/*.sql; do
  echo "   $f"
  psql_run -f "$f"
done

echo ">> Datos de prueba"
for f in "$DIR"/seed/*.sql; do
  echo "   $f"
  psql_run -f "$f"
done

echo ">> Base de datos de pruebas lista"
