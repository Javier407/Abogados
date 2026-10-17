# Compila Angular y lo sirve con nginx (que además reenvía /api al gateway).
FROM node:22.12.0-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.27.3-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY nginx-seguridad.inc /etc/nginx/seguridad.inc
COPY proxy-api.inc /etc/nginx/proxy-api.inc
COPY --from=build /app/dist/gestion-juridica-frontend/browser /usr/share/nginx/html
EXPOSE 80
