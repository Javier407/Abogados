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
