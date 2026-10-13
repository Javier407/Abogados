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

## Ejecutar

Requisitos: JDK 11 y Maven (o IntelliJ IDEA).

**Con MySQL** (perfil por defecto):

1. Ejecutar [`sql/gabinete.sql`](sql/gabinete.sql) en MySQL Workbench. Crea la base `gabinete`, el usuario `admin` / `admin123` y datos de ejemplo. Se puede ejecutar varias veces.
2. `mvn spring-boot:run`
3. Abrir http://localhost:8080/gabinete.html

**Sin instalar nada**: cambiar `spring.profiles.active=mysql` por `h2` en `src/main/resources/application.properties`. La base queda en memoria y se carga con `data-h2.sql`.

> Las credenciales de `application-mysql.properties` son solo para desarrollo local.

## Interfaz

`gabinete.html` es un panel con barra lateral (Asuntos, Clientes, Abogados), indicadores, buscador, filtro por estado y formularios en un panel lateral. El botón **Vista celular** abre un teléfono flotante con la aplicación funcionando a 390 px.

## Pruebas

```bash
mvn test
```

27 pruebas de integración (MockMvc sobre H2) que recorren las tres capas: validaciones, reglas de negocio, filtros, archivado, conflictos 409 y la relación abogado–usuario.

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
