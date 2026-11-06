# Compila Angular y lo sirve con nginx (que además reenvía /api al gateway).
FROM node:22.12.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.27.3-alpine
# Plantilla: al arrancar se reemplaza ${URL_PUBLICA} (vacía = sin redirección a HTTPS)
ENV URL_PUBLICA=""
COPY nginx.conf.template /etc/nginx/templates/default.conf.template
COPY nginx-seguridad.inc /etc/nginx/seguridad.inc
COPY proxy-api.inc /etc/nginx/proxy-api.inc
COPY --from=build /app/dist/gestion-juridica-frontend/browser /usr/share/nginx/html
EXPOSE 80 81
