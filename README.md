# Gabinete de Abogados · ciclo3back

Aplicación web para un gabinete de abogados que registra **clientes**, **abogados** y los **asuntos** (expedientes) que se tramitan. Laboratorio #1 del segundo corte de Arquitectura de Sistemas I, construido con **arquitectura en tres capas** sobre Spring Boot.

## Modelo

- Cada asunto tiene un número de expediente, un periodo (fecha de inicio y de archivo) y un estado: `EN_TRAMITE`, `SUSPENDIDO`, `ARCHIVADO` o `FINALIZADO`.
- **Cliente 1 : N Asunto**: cada asunto pertenece a un solo cliente.
- **Asunto N : M Abogado**: un asunto puede ser llevado por ninguno, uno o varios abogados (tabla `asunto_abogado`).
- **Abogado 1 : 0..1 Usuario**: un abogado puede tener una cuenta para entrar al sistema (`abogados.cedula_usuario`).

## Arquitectura

| Capa | Paquete | Clases |
|---|---|---|
| Presentación | `api`, `resources/static` | `ClientesAPI`, `AbogadosAPI`, `AsuntosAPI`, `ManejadorErroresAPI`, `gabinete.html` |
| Negocio | `service` | `ClientesService`, `AbogadosService`, `AsuntosService` y excepciones de negocio |
| Persistencia | `dao`, `model` | `ClientesDAO`, `AbogadosDAO`, `AsuntosDAO`, entidades JPA |

Las reglas del gabinete (expediente único, cliente existente, coherencia entre estado y fechas, tarjeta profesional única, cuenta de usuario única) viven en la capa de negocio. `ManejadorErroresAPI` las traduce a respuestas 400, 404 y 409.

## Ejecutar en otro computador

No hace falta instalar Maven: el proyecto trae el Maven Wrapper (`mvnw` / `mvnw.cmd`), que lo descarga solo la primera vez.

### 1. Instalar lo necesario

| Programa | Para qué | Dónde |
|---|---|---|
| Git | Descargar el proyecto | https://git-scm.com |
| JDK 11 | Compilar y ejecutar (Spring Boot 2.5 no funciona con JDK 17 o superior) | https://adoptium.net (Temurin 11) o desde IntelliJ |
| MySQL 8 + Workbench | Base de datos (opcional: ver paso 3B) | https://dev.mysql.com/downloads/installer |
| IntelliJ IDEA | Opcional, para abrir y ejecutar el proyecto | https://www.jetbrains.com/idea |

### 2. Descargar el proyecto

```bash
git clone https://github.com/Javier407/Abogados.git
cd Abogados
```

### 3A. Con MySQL (configuración por defecto)

1. Abrir MySQL Workbench, entrar con el usuario **root** y ejecutar [`sql/gabinete.sql`](sql/gabinete.sql) (File → Open SQL Script → rayo ⚡). Crea la base `gabinete`, el usuario `admin` / `admin123` que usa la aplicación y datos de ejemplo. Se puede ejecutar varias veces.
2. Ejecutar la aplicación:
   - Windows: `mvnw.cmd spring-boot:run`
   - Linux / macOS: `./mvnw spring-boot:run`
3. Abrir http://localhost:8080/gabinete.html

### 3B. Sin MySQL (base en memoria, para probar rápido)

En `src/main/resources/application.properties` cambiar `spring.profiles.active=mysql` por `spring.profiles.active=h2` y ejecutar igual que en el paso 3A. La base se crea en memoria con los datos de `data-h2.sql` y se borra al cerrar la aplicación.

### Con IntelliJ IDEA

File → Open → carpeta `Abogados` (abrir como proyecto Maven). En File → Project Structure → SDK elegir un **JDK 11** (si no hay, *Download JDK* → versión 11). Luego ejecutar `Ciclo3backApplication` con ▶.

### Problemas frecuentes

| Mensaje | Solución |
|---|---|
| `Access denied for user 'admin'` o `Unknown database 'gabinete'` | Ejecutar `sql/gabinete.sql` como root (paso 3A.1). |
| `Communications link failure` | El servicio de MySQL está detenido: iniciarlo (Windows: `services.msc` → MySQL80). |
| `Port 8080 was already in use` | Agregar `server.port=8081` en `application.properties` y abrir http://localhost:8081/gabinete.html |
| Errores raros al compilar o con `javax` | Verificar que se está usando JDK 11: `java -version`. |

> Las credenciales de `application-mysql.properties` son solo para desarrollo local.

## Interfaz

`gabinete.html` es un panel con barra lateral (Asuntos, Clientes, Abogados), indicadores, buscador, filtro por estado y formularios en un panel lateral. El botón **Vista celular** abre un teléfono flotante con la aplicación funcionando a 390 px.

## Pruebas

```bash
./mvnw test        # Windows: mvnw.cmd test
```

27 pruebas de integración (MockMvc sobre H2) que recorren las tres capas: validaciones, reglas de negocio, filtros, archivado, conflictos 409 y la relación abogado–usuario.

## Hacia el software jurídico (fase 0: planteamiento)

El proyecto evoluciona hacia un sistema de microservicios para firmas de abogados que vigila los radicados en la Rama Judicial y avisa antes de que se venzan los términos. Antes de programar se dejó el planteamiento:

| Documento | Contenido |
|---|---|
| [`docs/01-dominio.md`](docs/01-dominio.md) | Problema, glosario, ramas y tipos de proceso, estructura del radicado (23 dígitos), etapas, términos y tarifas Conalbos 2026 |
| [`docs/02-arquitectura.md`](docs/02-arquitectura.md) | Opciones evaluadas con pros y contras, microservicios (Java + Python), eventos, orden del repositorio y despliegue |
| [`docs/03-roadmap.md`](docs/03-roadmap.md) | Fases F0–F5 |
| [`docs/04-evaluacion-iso25010.md`](docs/04-evaluacion-iso25010.md) | Evaluación ISO/IEC 25010 de F5 (Seguridad 4 → 8; total 8,15) con brechas y plan de mejora |
| [`docs/05-mapa-procesos.md`](docs/05-mapa-procesos.md) | Mapa de procesos de la firma: qué está hecho, qué falta, dónde va el código y fases F6–F9 propuestas |
| [`docs/adr/`](docs/adr/) | Decisiones de arquitectura (políglota, RabbitMQ, PostgreSQL por esquemas, JWT propio, fuente Rama Judicial) |

### Base de datos de pruebas (PostgreSQL 16)

Firma ficticia *Rodríguez & Asociados Abogados SAS*: 2 sedes, 14 personas, 29 clientes, 8 peritos, 30 procesos en las 7 ramas con providencias, términos (vencidos, por vencer y al día, calculados en días hábiles), honorarios, pagos, vigilancia y notificaciones. Todos los datos son ficticios.

```bash
cd infra
cp .env.example .env        # escribir las claves en .env
docker compose up -d        # crea esquemas y carga los datos la primera vez
docker compose exec -T postgres psql -U gabinete_admin -d gabinete < ../db/consultas/ejemplos.sql
```

- Esquemas: `identidad`, `gabinete`, `procesos`, `vigilancia`, `notificaciones` (uno por microservicio, cada uno con su usuario `svc_*`).
- Usuarios de prueba de la aplicación (tabla `identidad.usuarios`): contraseña `Prueba2026*`.
- Para recargar desde cero: `docker compose down -v && docker compose up -d` (borra el volumen).

## Microservicios (fases 1 a 9)

```
navegador ──► frontend (nginx :8090) ──/api──► gateway (:8091, valida el JWT y el rol) ─┬─► identidad (login, JWKS)
                                                                 ├─► gabinete       (clientes, abogados, sedes)
                                                                 ├─► procesos       (procesos, radicado, términos, calendario)
                                                                 ├─► notificaciones (avisos en la app, en vivo por SSE; Telegram)
                                                                 └─► vigilancia     (radicados vigilados en la Rama Judicial)
 procesos ──termino.por_vencer / termino.vencido──► RabbitMQ ──► notificaciones ──► correo (Mailpit :8092) y Telegram
 vigilancia ──actuacion.detectada──► RabbitMQ ──► procesos (providencia) y notificaciones (aviso)
 asistente-ia ──► Ollama (modelo local qwen2.5:3b): borradores sin que los datos salgan del servidor
 todos ──► PostgreSQL (un esquema cada uno)
```

| Carpeta | Qué es | Tecnología |
|---|---|---|
| [`servicios/procesos`](servicios/procesos) | Procesos con validación del radicado (23 dígitos y despacho registrado), responsable y catálogos. Expediente: partes con aviso de conflicto de intereses, actuaciones, providencias manuales, sentencia, recursos y línea de tiempo. Términos con vencimiento en días hábiles (festivos, Semana Santa y vacancia), calendario judicial y revisión diaria a las 7:00 que publica los avisos | Spring Boot 3.3 · Java 17 · JDBC |
| [`servicios/gabinete`](servicios/gabinete) | Clientes, abogados, peritos, tarifas de referencia en pesos, acuerdos de honorarios y pagos (evolución de este laboratorio: `javax` → `jakarta`, MySQL → PostgreSQL) | Spring Boot 3.3 · Java 17 · JPA |
| [`servicios/asistente-ia`](servicios/asistente-ia) | Borradores de contestación y resúmenes para el cliente con un modelo local; encabezado puesto por el sistema, texto por partes (ADR 0006) | Python 3.12 · FastAPI · Ollama |
| [`servicios/notificaciones`](servicios/notificaciones) | Avisos de términos y de actuaciones nuevas: en la app (en vivo con SSE), por correo y por Telegram (vinculación con código de un solo uso); no repite avisos | Python 3.12 · FastAPI · aio-pika |
| [`servicios/vigilancia`](servicios/vigilancia) | Consulta cada radicado en su fuente (manual, simulada o portal de la Rama Judicial, ver ADR 0005), detecta actuaciones nuevas y publica `actuacion.detectada` | Python 3.12 · FastAPI · httpx |
| [`servicios/identidad`](servicios/identidad) | Inicio de sesión (BCrypt), JWT RS256 de 8 h, JWKS, cambio de contraseña, bloqueo tras 5 fallos, límite por IP y bitácora de ingresos | Spring Boot 3.3 · Java 17 · JDBC |
| [`servicios/gateway`](servicios/gateway) | Entrada única: `/api/<recurso>` → servicio dueño. Exige token en toda la API, aplica permisos por rol, pone la identidad en cabeceras (borra las del cliente) y deja una línea `AUDITORIA` por petición | Spring Cloud Gateway · OAuth2 Resource Server |
| [`frontend`](frontend) | Panel, términos, calendario, avisos, procesos, clientes y abogados (basado en `gestion-juridica-frontend`) | Angular 21 · nginx |
| [`docs/contratos`](docs/contratos) | OpenAPI de cada servicio y formato de los eventos | |

Cada servicio tiene arquitectura hexagonal: `dominio` (reglas, sin frameworks), `aplicacion` (casos de uso) e `infraestructura` (REST, base de datos, HTTP y RabbitMQ).

**Levantar todo** (solo necesita Docker; Maven y Node corren dentro de los contenedores):

```bash
cd infra
cp .env.example .env              # escribir las claves
docker compose up -d --build      # compila, prueba y arranca los 11 contenedores
docker compose exec ollama ollama pull qwen2.5:3b   # una sola vez: descarga el modelo (~2 GB)
```

Luego abrir http://localhost:8090 (en el servidor: https://docker-server.tail76e9a3.ts.net, solo dentro de la VPN; http://100.65.152.112:8090 redirige ahí). El buzón de pruebas Mailpit, donde llegan los correos de aviso, queda en el 8092. El gateway (8091) solo escucha en la propia máquina.

**Inicio de sesión:** cada persona entra con su usuario (por ejemplo `arodriguez`, socio; `scastro`, dependiente). En la base de pruebas todos tienen la contraseña `Prueba2026*`, que se puede cambiar en **Mi cuenta** (mínimo 10 caracteres con letras y números). Las cuentas se crean y administran en **Usuarios** (solo ADMIN): la contraseña temporal que escribe el administrador se debe cambiar al entrar. El menú muestra solo lo que permite el rol, y los avisos son los de la persona conectada.

Antes del primer arranque en un servidor hay que crear la llave de firma de los tokens (nunca va a git):

```bash
mkdir -m 700 infra/secretos
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out infra/secretos/jwt-privada.pem
```

Mailpit pide usuario y clave si se define `MAILPIT_UI_AUTH=usuario:clave` en `infra/.env`.

**Claves de los servicios (F9).** Cada servicio verifica el token de cada petición y, para llamar a otro, pide su propio token a identidad con una clave. Se generan en el servidor sin mostrarlas:

```bash
cd infra && umask 077
for s in PROCESOS GABINETE NOTIFICACIONES VIGILANCIA ASISTENTE; do
  grep -q "^CLAVE_SVC_$s=" .env || echo "CLAVE_SVC_$s=$(openssl rand -hex 32)" >> .env
done
```

**HTTPS (F9)** con `tailscale serve` (el certificado lo emite Tailscale; nada se abre a internet):

1. En la consola de Tailscale (DNS) activar **MagicDNS** y **HTTPS Certificates**.
2. En la VM: `sudo tailscale serve --bg --https=443 http://127.0.0.1:8093` (el puerto 8093 es la entrada HTTPS del frontend, solo local).
3. En `infra/.env`: `URL_PUBLICA=https://<nombre-de-la-vm>.<tailnet>.ts.net` y `COOKIE_SEGURA=true`; luego `docker compose up -d`.

Desde ahí http://IP:8090 redirige a HTTPS, el navegador recibe HSTS y la cookie de la sesión solo viaja cifrada. Mientras no haya HTTPS, `COOKIE_SEGURA=false` (si no, el navegador no guarda la cookie y la sesión dura 15 minutos).

**Facturación electrónica (F14).** Pantalla **Facturación**. Hoy con el proveedor **SIMULADO** y el rango del set de pruebas de la DIAN: el flujo es el real, pero nada se envía a la DIAN ni tiene validez fiscal. Para producción hay que contratar un proveedor tecnológico, escribir su adaptador de `ProveedorFacturacion` y cargar la resolución de numeración real en **Facturación → Configuración**.

**Verificación en dos pasos (F13).** Cada persona la activa en **Mi cuenta** con una app autenticadora. `MFA_OBLIGATORIO_ROLES` en `infra/.env` (p. ej. `ADMIN,SOCIO`) la exige a esos roles. Los secretos se cifran con `MFA_CLAVE_CIFRADO` (se genera una vez en el servidor, ver `.env.example`, y se respalda con las demás llaves). Quien pierda el teléfono y los códigos de recuperación pide al administrador que la reinicie en **Usuarios**.

**Historial del servidor (F12).** El vigía guarda cada revisión (90 días) y cada incidente en el esquema `operacion`; el administrador los ve en la pantalla **Servidor**. **Reglas de términos (F12):** pantalla **Reglas**; solo un socio las cambia o valida.

**Vigía del servidor (F11).** `infra/monitoreo/vigia.sh` revisa cada 5 minutos contenedores, disco, memoria, respaldos, outbox, colas de RabbitMQ y HTTPS, y avisa por Telegram a los administradores que vincularon Telegram en la app (problema nuevo, resuelto y un resumen diario). Se instala como timer de systemd con `sh infra/monitoreo/instalar.sh` (`quitar` para desinstalarlo). Para revisar a mano: `sh infra/monitoreo/vigia.sh estado`.

**Respaldo de las llaves y recuperación (F15).** Las llaves del servidor (`infra/.env`, `infra/secretos/`) se respaldan cifradas en `llaves-*.p7m`. Solo las abre la llave privada del PC (`D:\respaldos\abogados\llave`). La copia diaria comprueba que se abren y el contenedor `respaldo` prueba cada domingo que la base se restaura. Cómo reconstruir todo: `docs/06-recuperacion.md`.

**Copias fuera de la VM (F10).** Cada día el contenedor `respaldo` deja la base (`gabinete-*.dump`) y los documentos del expediente (`documentos-*.tar.gz`) en `infra/respaldos/`. En el PC de Javier, la tarea programada "Abogados - copia de respaldos" ejecuta `infra/respaldo/copiar-al-pc.ps1` a las 9:00 y al iniciar sesión. El script baja lo nuevo a `D:\respaldos\abogados`, comprueba el SHA-256 y guarda 30 días. Para ejecutarla a mano:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra\respaldo\copiar-al-pc.ps1
```

Restaurar los documentos: detener `procesos` y descomprimir el paquete dentro del volumen (`docker run --rm -v abogados_documentos:/d -v "$PWD/respaldos":/r alpine tar -xzf /r/documentos-AAAAMMDD-HHMMSS.tar.gz -C /d`).

**Expediente digital y consultas (F10).** Cada proceso tiene su tarjeta "Documentos" (PDF, Word, Excel, imágenes y texto de hasta 20 MB); lo que se marque "visible para el cliente" aparece en su portal. La pantalla **Consultas** registra a quien llega a la firma, avisa el conflicto de intereses y, si contrata, crea su ficha de cliente. El asistente redacta también demandas (con los hechos que escribe el abogado) y recursos contra una providencia.

**Portal del cliente (F9).** Una cuenta con rol CLIENTE se enlaza con su ficha de cliente (en **Usuarios**). Entra a **Mis procesos** (etapa, abogado, próxima audiencia, novedades y el resumen que publicó su abogado) y **Mis pagos**; recibe avisos cuando su abogado publica un resumen y el día hábil antes de una audiencia. En la base de pruebas: `cmendez` (Carolina Méndez) y `andina` (Inversiones Andina).

**Copias de seguridad:** el contenedor `respaldo` hace un `pg_dump` cada día a las 02:30 (y al arrancar, si la última copia tiene más de un día) en `infra/respaldos/`, fuera de git, y guarda 14 días. Para probar que la última copia se restaura bien (en una base temporal que luego borra):

```bash
docker compose exec respaldo sh /respaldo/respaldar.sh probar
```

Restaurar de verdad (reemplaza los datos actuales; detener antes los servicios):

```bash
docker compose exec -T postgres sh -c 'pg_restore --clean --if-exists -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < respaldos/gabinete-AAAAMMDD-HHMMSS.dump
```

**Integración continua:** [`.github/workflows/ci.yml`](.github/workflows/ci.yml) compila y prueba cada servicio Java y Python, el frontend y el `docker-compose.yml` en cada *push*.

Los avisos salen solos cada día hábil a las 7:00, o al pulsar **Enviar avisos ahora** en Términos.

Los correos van a Mailpit y no salen a internet. Para enviarlos de verdad, definir `SMTP_*` en `infra/.env` (ver `.env.example`), nunca con la base de pruebas: sus correos son ficticios.

**Telegram:** crear un bot en @BotFather y escribir `TELEGRAM_TOKEN=...` en `infra/.env` (nunca en git). Luego cada abogado lo vincula en **Avisos → Vincular Telegram**.

**Vigilancia:** todo proceso entra como `MANUAL`. En **Vigilancia** se elige la fuente por radicado. Usar `SIMULADA` para demostraciones; `Portal Rama Judicial` solo con radicados reales de la firma, porque los de la base de pruebas pueden coincidir con procesos reales de otras personas.

**Base de datos ya creada:** las migraciones de `db/migraciones/` se aplican solas en una base nueva. En una existente:

```bash
docker compose exec -T postgres psql -U gabinete_admin -d gabinete < ../db/migraciones/001-f2-terminos-y-avisos.sql
docker compose exec -T postgres psql -U gabinete_admin -d gabinete < ../db/migraciones/002-f3-vigilancia.sql
```

**Desarrollo del frontend** con recarga en vivo, contra el gateway del servidor:

```bash
cd frontend
npm ci
npx ng serve                      # http://localhost:4200, /api va al gateway (proxy.conf.json)
```

**Pruebas:**

| Dónde | Cuántas | Qué cubren |
|---|---|---|
| `servicios/procesos` | 55 | Radicado, reglas del proceso, calendario judicial (festivos, Semana Santa, vacancia), términos y revisión de avisos, actuaciones detectadas (sin duplicar), API (400/404/409/422/503) |
| `servicios/notificaciones` | 36 | Eventos de términos y actuaciones, redacción, canales (app, correo, Telegram), no repetir avisos, fallos, vinculación de Telegram, bot sin filtrar el token, API y avisos en vivo |
| `servicios/vigilancia` | 21 | Cuándo consultar, línea base, actuaciones nuevas sin repetir, fallos y alerta, no perder avisos si RabbitMQ falla, fuente del portal con respuestas simuladas, API |
| `servicios/gabinete` | 25 | Reglas de clientes y abogados, casos de uso, API y persistencia JPA sobre H2 |
| `servicios/gateway` | 1 | Rutas hacia cada servicio |
| `frontend` | 5 | Arranque de la app, etiquetas y mensajes de error |

Las pruebas Java y Python se ejecutan solas al construir cada imagen (`docker compose build`); si fallan, no hay imagen. Las del frontend, con `npx ng test --watch=false`.

## Documento del laboratorio

El informe completo (requerimientos, diagramas, pruebas, QA y evaluación ISO/IEC 25000) está en [`docs/`](docs/).

## Servicios REST

| Método | URL | Descripción |
|---|---|---|
| POST / PUT | `/clientes/guardar`, `/clientes/actualizar` | Crear / actualizar cliente |
| GET | `/clientes/listar`, `/clientes/{id}` | Listar / consultar |
| DELETE | `/clientes/eliminar/{id}` | Eliminar (409 si tiene asuntos) |
| * | `/abogados/...` | Las mismas operaciones para abogados |
| POST / PUT | `/asuntos/guardar`, `/asuntos/actualizar` | Crear / actualizar asunto |
| GET | `/asuntos/listar`, `/asuntos/{expediente}` | Listar / consultar |
| GET | `/asuntos/cliente/{id}`, `/asuntos/abogado/{id}`, `/asuntos/estado/{estado}` | Filtros |
| PUT | `/asuntos/{expediente}/archivar?fecha=` | Archivar un asunto abierto |
| DELETE | `/asuntos/eliminar/{expediente}` | Eliminar asunto |
