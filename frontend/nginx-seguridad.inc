# Cabeceras de seguridad. Se incluyen en el server y en cada location que use add_header
# (en nginx, un add_header dentro de una location anula los del server).
add_header Content-Security-Policy "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'" always;
add_header X-Content-Type-Options "nosniff" always;
add_header X-Frame-Options "DENY" always;
add_header Referrer-Policy "no-referrer" always;
add_header Permissions-Policy "camera=(), microphone=(), geolocation=(), payment=()" always;
add_header Cross-Origin-Opener-Policy "same-origin" always;
# HTTPS obligatorio en adelante para este nombre (los navegadores ignoran la cabecera si llega por HTTP).
add_header Strict-Transport-Security "max-age=31536000" always;
