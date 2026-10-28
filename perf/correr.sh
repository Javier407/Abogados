#!/usr/bin/env bash
# F16: corre la prueba de carga del gateway en la VM (docs/09-pruebas-carga.md).
#   K6_USUARIO=... K6_CLAVE=... perf/correr.sh k6|jmeter
# Contenedores de una sola corrida (--rm) en la red del host: el gateway solo escucha en 127.0.0.1:8091.
# Los resultados quedan en perf/resultados/ (fuera de git).
set -euo pipefail
cd "$(dirname "$0")"
: "${K6_USUARIO:?Falta K6_USUARIO}" "${K6_CLAVE:?Falta K6_CLAVE}"
mkdir -p resultados
fecha=$(date +%Y%m%d-%H%M)
case "${1:-k6}" in
  k6)
    docker run --rm --network host -e K6_USUARIO -e K6_CLAVE -v "$PWD:/perf" \
      grafana/k6:1.3.0 run --summary-export "/perf/resultados/k6-$fecha.json" /perf/k6/carga-gateway.js ;;
  jmeter)
    docker run --rm --network host -e K6_USUARIO -e K6_CLAVE -v "$PWD:/perf" \
      justb4/jmeter:5.5 -n -t /perf/jmeter/carga-gateway.jmx \
      -l "/perf/resultados/jmeter-$fecha.jtl" -e -o "/perf/resultados/jmeter-$fecha" ;;
  *) echo "Uso: $0 k6|jmeter" >&2; exit 2 ;;
esac
