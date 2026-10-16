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

## Microservicios (fases 1 a 4)

```
navegador ──► frontend (nginx :8090) ──/api──► gateway (:8091) ─┬─► gabinete       (clientes, abogados, sedes)
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
| [`servicios/procesos`](servicios/procesos) | Procesos con validación del radicado (23 dígitos y despacho registrado), responsable y catálogos. Términos con vencimiento en días hábiles (festivos, Semana Santa y vacancia), calendario judicial y revisión diaria a las 7:00 que publica los avisos | Spring Boot 3.3 · Java 17 · JDBC |
| [`servicios/gabinete`](servicios/gabinete) | Clientes, abogados, peritos, tarifas de referencia en pesos, acuerdos de honorarios y pagos (evolución de este laboratorio: `javax` → `jakarta`, MySQL → PostgreSQL) | Spring Boot 3.3 · Java 17 · JPA |
| [`servicios/asistente-ia`](servicios/asistente-ia) | Borradores de contestación y resúmenes para el cliente con un modelo local; encabezado puesto por el sistema, texto por partes (ADR 0006) | Python 3.12 · FastAPI · Ollama |
| [`servicios/notificaciones`](servicios/notificaciones) | Avisos de términos y de actuaciones nuevas: en la app (en vivo con SSE), por correo y por Telegram (vinculación con código de un solo uso); no repite avisos | Python 3.12 · FastAPI · aio-pika |
| [`servicios/vigilancia`](servicios/vigilancia) | Consulta cada radicado en su fuente (manual, simulada o portal de la Rama Judicial, ver ADR 0005), detecta actuaciones nuevas y publica `actuacion.detectada` | Python 3.12 · FastAPI · httpx |
| [`servicios/gateway`](servicios/gateway) | Entrada única: `/api/<recurso>` → servicio dueño | Spring Cloud Gateway |
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

Luego abrir http://localhost:8090 (en el servidor: http://100.65.152.112:8090). El gateway queda en el puerto 8091 y el buzón de pruebas Mailpit, donde llegan los correos de aviso, en el 8092.

En la app, elegir en "Ver como" (abajo en el menú) el abogado cuyos avisos se quieren ver; es provisional hasta el inicio de sesión de F5. Los avisos salen solos cada día hábil a las 7:00, o al pulsar **Enviar avisos ahora** en Términos.

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
