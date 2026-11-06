#!/bin/sh
# Instala el vigía (F11) como timer de systemd: cada 5 minutos, con el usuario que lo instala (debe poder usar docker).
#   sh infra/monitoreo/instalar.sh            instala o actualiza
#   sh infra/monitoreo/instalar.sh quitar     lo desinstala
set -eu
SCRIPT="$(cd "$(dirname "$0")" && pwd)/vigia.sh"
USUARIO="$(id -un)"

if [ "${1:-}" = "quitar" ]; then
  sudo systemctl disable --now abogados-vigia.timer 2>/dev/null || true
  sudo rm -f /etc/systemd/system/abogados-vigia.service /etc/systemd/system/abogados-vigia.timer
  sudo systemctl daemon-reload
  echo "Vigía desinstalado"
  exit 0
fi

sudo tee /etc/systemd/system/abogados-vigia.service > /dev/null <<EOF
[Unit]
Description=Vigía del servidor de Gestión Jurídica (revisión y avisos por Telegram)
After=docker.service network-online.target

[Service]
Type=oneshot
User=$USUARIO
ExecStart=/bin/sh $SCRIPT
TimeoutStartSec=180
EOF

sudo tee /etc/systemd/system/abogados-vigia.timer > /dev/null <<EOF
[Unit]
Description=Vigía de Gestión Jurídica cada 5 minutos

[Timer]
OnBootSec=3min
OnUnitActiveSec=5min
Persistent=true

[Install]
WantedBy=timers.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable --now abogados-vigia.timer
systemctl list-timers abogados-vigia.timer --no-pager
