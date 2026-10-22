# Cabeceras que nginx pasa al gateway en todas las rutas /api (ver nginx.conf)
proxy_set_header Host $http_host;
proxy_set_header X-Forwarded-For $ip_cliente;
proxy_set_header X-Real-IP $ip_cliente;
proxy_set_header X-Forwarded-Proto $proto_original;
