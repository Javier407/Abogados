#!/bin/sh
# Respaldo cifrado de las llaves del servidor (F15): infra/.env (claves de la base, de los servicios, del bot y
# MFA_CLAVE_CIFRADO) e infra/secretos/ (llave de firma de los tokens). Sin ellas, los respaldos de la base no bastan
# para volver a montar el sistema: nadie podría entrar con su verificación en dos pasos y habría que volver a crear
# todas las claves.
#
#   respaldar-llaves.sh            crea un paquete solo si las llaves cambiaron o el último tiene más de 7 días
#   respaldar-llaves.sh ahora      crea un paquete ya
#   respaldar-llaves.sh estado     dice cuándo fue el último y si hay certificado
#
# Cifrado: CMS (openssl cms) con AES-256 y la llave pública del certificado infra/secretos/respaldo-certificado.pem.
# La llave privada que lo abre NO está en la VM: está en el PC (D:\respaldos\abogados\llave), así que robar la VM o
# sus respaldos no revela las llaves. La copia diaria al PC lo descarga junto con los respaldos y comprueba que se
# puede abrir. Lo llama el vigía en cada vuelta (cada 5 minutos).
set -eu

INFRA="$(cd "$(dirname "$0")/.." && pwd)"
CERT="$INFRA/secretos/respaldo-certificado.pem"
DESTINO="$INFRA/respaldos"
ESTADO="${XDG_STATE_HOME:-$HOME/.local/state}/abogados-vigia"
RETENER_DIAS=60
mkdir -p "$ESTADO" && chmod 700 "$ESTADO"

ultimo() {
  ls -1t "$DESTINO"/llaves-*.p7m 2>/dev/null | head -1
}

# Huella de lo que se respalda (contenido, no fechas): si no cambia, no hace falta otro paquete.
huella() {
  (cd "$INFRA" && cat .env && find secretos -type f ! -name 'respaldo-certificado.pem' | sort | xargs cat) \
    | sha256sum | cut -d' ' -f1
}

crear() {
  [ -f "$CERT" ] || { echo "Falta $CERT: no se puede cifrar el respaldo de las llaves"; exit 2; }
  archivo="$DESTINO/llaves-$(date +%Y%m%d-%H%M%S).p7m"
  # tar a la salida estándar y cifrado sin archivos intermedios: las llaves nunca quedan en claro en el disco.
  tar -C "$INFRA" -czf - .env secretos \
    | openssl cms -encrypt -binary -aes256 -outform DER -out "$archivo.parcial" \
        -recip "$CERT" -keyopt rsa_padding_mode:oaep -keyopt rsa_oaep_md:sha256
  [ -s "$archivo.parcial" ] || { rm -f "$archivo.parcial"; echo "El paquete cifrado quedó vacío"; exit 1; }
  mv "$archivo.parcial" "$archivo"
  chmod 600 "$archivo"
  huella > "$ESTADO/llaves-huella"
  find "$DESTINO" -name 'llaves-*.p7m' -mtime +"$RETENER_DIAS" -delete
  echo "Llaves respaldadas y cifradas: $(basename "$archivo")"
}

case "${1:-}" in
  ahora)
    crear ;;
  estado)
    [ -f "$CERT" ] && echo "Certificado: $(openssl x509 -in "$CERT" -noout -subject -enddate | tr '\n' ' ')" \
      || echo "Sin certificado ($CERT)"
    u=$(ultimo); if [ -n "$u" ]; then echo "Último paquete: $(basename "$u")"; else echo "Último paquete: ninguno"; fi ;;
  *)
    [ -f "$CERT" ] || exit 0          # sin certificado no hay nada que hacer (el vigía lo avisa)
    u=$(ultimo)
    if [ -z "$u" ] || [ -n "$(find "$u" -mtime +7)" ] || [ "$(huella)" != "$(cat "$ESTADO/llaves-huella" 2>/dev/null)" ]; then
      crear
    fi ;;
esac
