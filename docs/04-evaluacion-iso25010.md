# Evaluación de calidad ISO/IEC 25010 (fase F5)

Esta evaluación repite la del laboratorio #1 (`docs/Lab1Corte2 - Gabinete de abogados.docx`) sobre el sistema de microservicios. Usa los mismos pesos, la misma escala y el mismo criterio de decisión para que los resultados se puedan comparar.

- **Marco:**
  - ISO/IEC 25010:2023 (modelo).
  - ISO/IEC 25023 (métricas).
  - ISO/IEC 25040 (proceso).
- **Propósito:** decidir si el sistema puede usarse con datos reales de una firma.
- **Alcance:**
  - Servicios: `gateway`, `identidad`, `gabinete`, `procesos`, `vigilancia`, `notificaciones` y `asistente-ia`.
  - Frontend Angular servido por nginx.
  - Infraestructura Docker Compose en la VM del servidor.
- **Fecha:** 2026-10-16. Rama `feature/f5-seguridad`.
- **Criterio de decisión:**
  - 9 a 10: excelente.
  - 7 a 8,9: satisfactorio.
  - 5 a 6,9: aceptable con mejoras.
  - Menos de 5: insatisfactorio.
  - Además, ninguna característica puede quedar por debajo de 5.

## 1. Seguridad (detalle)

La característica más débil del laboratorio (4/10) motivó la fase F5. Se mide con las subcaracterísticas de la norma.

| Subcaracterística | Métrica (ISO/IEC 25023) | Valor | Umbral | Cumple | Evidencia |
|---|---|---|---|---|---|
| Autenticidad | Rutas de la API que exigen token válido | Todas las `/api/**` menos `POST /api/auth/login` | 100 % | ✔ | `ConfiguracionSeguridad` (gateway): la regla por defecto es `denyAll`. `SeguridadTest`: sin token → 401; token falso o de otro emisor → 401. |
| Autenticidad | Firma del token y verificación | RS256, clave de 2048 bits, verificación por JWKS y emisor | Algoritmo asimétrico | ✔ | `EmisorJwtRsa`. `AutenticacionTest` verifica la firma con la JWKS publicada y que la JWKS no contiene partes privadas. |
| Autenticidad | Resistencia a fuerza bruta | 5 fallos → cuenta bloqueada 15 min (423). Más de 20 intentos por IP en 10 min → 429 | Bloqueo + límite | ✔ | `Usuario.registrarFallo`, `LimiteIntentosPorIp`, `LimiteIntentosTest`, `AuthRecursoTest`. |
| Confidencialidad | Contraseñas almacenadas sin texto plano | BCrypt (costo 10); ningún endpoint devuelve el hash | 100 % | ✔ | `CifradorBCrypt`, `AuthRecurso.Perfil` (sin hash). |
| Confidencialidad | Control de acceso por rol | 6 roles; tabla de reglas en el gateway. Por defecto se niega | Mínimo privilegio | ✔ | `PoliticaAcceso`, matriz en `SeguridadTest`: CLIENTE → 403 en la API interna; DEPENDIENTE → 403 en honorarios. |
| Confidencialidad | Acceso a datos de otra persona | Los avisos solo se ven si `idPersona` = usuario del token (salvo ADMIN) | 0 fugas | ✔ | `persona_autorizada` en `notificaciones`, pruebas 403 en `test_api.py`. |
| Confidencialidad | Secretos fuera del repositorio | Claves de BD, RabbitMQ, Telegram, Mailpit y la llave JWT viven en `.env` / `infra/secretos/` (ignorados por git), generadas en el servidor sin mostrarse | 0 secretos en git | ✔ | `.gitignore`, `infra/.env.example` (solo nombres). |
| Integridad | Suplantación de identidad entre servicios | El gateway borra `X-Usuario-Id` / `X-Usuario-Roles` del cliente y los vuelve a poner desde el token | 0 cabeceras aceptadas del cliente | ✔ | `IdentidadEnCabeceras`; `SeguridadTest` envía cabeceras falsas. |
| Integridad | Consultas parametrizadas / salida escapada | JDBC/JPA con parámetros; psycopg con parámetros; Angular escapa por defecto; CSP sin `unsafe-inline` en scripts | 100 % | ✔ | Revisión del código; `nginx-seguridad.inc`. |
| Responsabilidad (accountability) | Acciones atribuibles a una persona | Bitácora de ingresos en BD (éxito/fallo, motivo, IP). Línea `AUDITORIA` por petición en el gateway (usuario, rol, método, ruta sin parámetros, estado) | Toda acción con autor | ✔ | `identidad.ingresos` (migración 003), `Auditoria.java`. |
| No repudio | Registros a prueba de manipulación | Los logs del contenedor rotan (10 MB × 3) y no están firmados | Registro inalterable | ✘ | Brecha conocida (ver plan de mejora). |
| Resistencia | Superficie expuesta | Solo se publican 8090 (app), 8091 (gateway) y 8092 (Mailpit, con usuario y clave), únicamente en la VPN Tailscale. BD, RabbitMQ, Ollama y servicios internos no se publican. Actuator expone solo `health` | Mínima | ✔ | `infra/docker-compose.yml`, `application.yml` del gateway. |
| Resistencia | Cabeceras de seguridad HTTP | CSP, `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy: no-referrer`, `Permissions-Policy`, COOP; `server_tokens off` | Presentes | ✔ | `frontend/nginx-seguridad.inc`. |

**Brechas que se aceptan por ahora (honestas):**

1. **Confianza en la red interna.** Los servicios detrás del gateway confían en las cabeceras `X-Usuario-*` sin validar el token por su cuenta. Es seguro mientras solo el gateway llegue a ellos (no tienen puerto publicado), pero un contenedor comprometido en la misma red podría suplantar a un usuario.
2. **Sin TLS propio.** Dentro de la VPN el tráfico va cifrado por WireGuard (Tailscale), pero la app habla HTTP. Si algún día se publica fuera de la VPN, se necesita HTTPS (por ejemplo, `tailscale serve` o un proxy con certificado).
3. **Sin revocación de tokens ni *refresh token*.** Un token robado vale hasta 8 h. Cerrar sesión borra el token del navegador, pero no lo invalida en el servidor.
4. **Sin segundo factor (MFA)** ni pantalla de administración de usuarios. Las cuentas se crean por SQL.
5. **Token en la URL** solo para el canal de avisos en vivo (EventSource no admite cabeceras). Se mitiga: el gateway lo quita antes de reenviar y nginx no registra esa ruta.
6. **Sin análisis de dependencias** (Dependabot / OWASP Dependency-Check) en el pipeline.

**Nota de Seguridad: 8 / 10.** Se cumplen 12 de 13 métricas. La única que no se cumple (no repudio) y las brechas 1 a 6 son de severidad media o baja dentro de una VPN privada. No queda ninguna brecha alta del laboratorio: autenticación, contraseñas en texto plano y credenciales en el código están resueltas. No se da 9 porque faltan revocación, MFA y defensa en profundidad entre servicios.

## 2. Métricas de las demás características

| Característica | Métrica | Valor | Umbral | Cumple |
|---|---|---|---|---|
| Adecuación funcional | Criterios de fase cumplidos (F1–F4) | 4 / 4 | 100 % | ✔ |
| Adecuación funcional | Corrección: pruebas automáticas aprobadas en CI | ≈ 194 / 194 (identidad 16, gabinete 36, procesos 52, gateway 10+, notificaciones 36, vigilancia 21, asistente-ia 14, frontend 9) | 100 % | ✔ |
| Adecuación funcional | Completitud frente al dominio (`docs/01-dominio.md`) | Falta el portal del cliente y la administración de usuarios | Sin huecos críticos | ~ |
| Eficiencia de desempeño | Borrador de contestación con el modelo local (CPU, sin GPU) | 229 s (F4) | ≤ 300 s | ✔ |
| Eficiencia de desempeño | Memoria del sistema completo | Límites de `mem_limit` ≈ 6 GB en total, incluyendo 3 GB para Ollama | Dejar ≥ 1,5 GB libres en la VM | ✔ |
| Eficiencia de desempeño | Prueba de carga concurrente | No realizada | 50 usuarios | ✘ |
| Compatibilidad | Interfaces estándar | REST/JSON con contratos OpenAPI, eventos AMQP documentados, JWKS (RFC 7517) y JWT (RFC 7519) | 100 % | ✔ |
| Capacidad de interacción | Protección contra errores | Validación por campo, mensajes en español, menú y rutas filtrados por rol, aviso de sesión vencida | Formularios validados | ✔ |
| Capacidad de interacción | Accesibilidad | Sin auditoría WCAG 2.1 AA | Auditoría hecha | ✘ |
| Fiabilidad | Recuperación ante fallos | `restart: unless-stopped`, *healthchecks*, reconexión de pools (`check_connection`), TTL DNS de 5 s en la JVM. Comprobado tras reinicios de contenedores y de Postgres | Se recupera solo | ✔ |
| Fiabilidad | Entrega garantizada de eventos | Sin *outbox*: si RabbitMQ está caído al crear un proceso, el evento se pierde (queda en el log) | 0 eventos perdidos | ✘ |
| Fiabilidad | Disponibilidad | Un solo servidor; hubo 2 cortes de red o energía del host durante el desarrollo | Sin punto único de falla | ✘ |
| Mantenibilidad | Modularidad | Arquitectura hexagonal en los 7 servicios (dominio / aplicación / infraestructura); un esquema de BD por servicio | 0 dependencias cruzadas | ✔ |
| Mantenibilidad | Capacidad de ser probado | CI en GitHub Actions: compila y prueba cada servicio y el frontend en cada *push*; las imágenes Docker no se construyen si fallan las pruebas | Pipeline en verde | ✔ |
| Mantenibilidad | Cobertura medida | JaCoCo / coverage.py no publicados en CI | ≥ 70 % ramas | ✘ |
| Flexibilidad | Vigencia de la plataforma | Spring Boot 3.3 / Java 17, Python 3.12, Angular 21, PostgreSQL 16 | Versiones con soporte | ✔ |
| Flexibilidad | Reemplazabilidad | Fuente de la Rama Judicial y modelo de IA detrás de interfaces (`FuenteRamaJudicial`, proveedor de modelo). El gateway solo valida JWT, así que permite cambiar a Keycloak | Sin tocar el núcleo | ✔ |

## 3. Resultado ponderado

| Característica | Peso | Lab #1 | F5 | Aporte F5 | Justificación |
|---|---|---|---|---|---|
| Adecuación funcional | 20 % | 10 | 9 | 1,80 | Criterios de F1 a F4 cumplidos y pruebas en verde. Faltan el portal del cliente y la administración de usuarios. |
| Eficiencia de desempeño | 10 % | 8 | 7 | 0,70 | Dentro de los límites de memoria. La IA local es lenta en CPU. Sin prueba de carga. |
| Compatibilidad | 5 % | 9 | 9 | 0,45 | Contratos OpenAPI, eventos y estándares JWT/JWKS. |
| Capacidad de interacción | 15 % | 8 | 8 | 1,20 | Menú por rol, validación y avisos en vivo. Sin auditoría WCAG. |
| Fiabilidad | 10 % | 7 | 7 | 0,70 | Se recupera sola de reinicios. Sin *outbox* y con un único servidor. |
| **Seguridad** | 15 % | **4** | **8** | **1,20** | Ver sección 1. |
| Mantenibilidad | 15 % | 8 | 8 | 1,20 | Hexagonal y CI en cada *push*. Falta medir la cobertura. |
| Flexibilidad | 10 % | 7 | 9 | 0,90 | Plataforma vigente y piezas reemplazables. |
| Protección (safety) | — | N/A | N/A | — | No aplica: el sistema no controla equipos. |
| **Total** | 100 % | 7,65 | **8,15** | | **Satisfactorio**; ninguna característica por debajo de 5. |

**Conclusión:** Seguridad sube de 4 a 8 y supera el mínimo de 7 que pedía F5. El sistema puede usarse con datos reales **dentro de la VPN**, con las brechas de la sección 1 documentadas y aceptadas. Publicarlo en internet exige antes resolver los puntos 1 a 3 del plan.

## 4. Plan de mejora

| Prioridad | Característica | Hallazgo | Acción propuesta |
|---|---|---|---|
| 1 · Alta (antes de salir de la VPN) | Seguridad | HTTP sin TLS propio | HTTPS con `tailscale serve` o un proxy con certificado; HSTS. |
| 2 · Alta (antes de salir de la VPN) | Seguridad | Tokens de 8 h sin revocación | Access token de 15 min + *refresh token* rotatorio guardado en BD; lista de `jti` revocados al cerrar sesión. |
| 3 · Alta (antes de salir de la VPN) | Seguridad | Servicios internos confían en cabeceras | Cada servicio valida el JWT con la JWKS (defensa en profundidad) o usa mTLS entre contenedores. |
| 4 · Media | Seguridad | Sin análisis de dependencias | Dependabot y `pip-audit` / OWASP Dependency-Check en el pipeline. |
| 5 · Media | Adecuación funcional | Cuentas creadas por SQL | Pantalla de administración de usuarios y roles (solo ADMIN), con restablecimiento de contraseña. |
| 6 · Media | Fiabilidad | Eventos que se pueden perder | Tabla *outbox* en `procesos` y `vigilancia`. |
| 7 · Media | Mantenibilidad | Cobertura no medida | Publicar JaCoCo y coverage.py en CI, con un umbral del 70 % de ramas. |
| 8 · Baja | Seguridad | No repudio | Enviar la bitácora `AUDITORIA` a un almacén de solo anexar (tabla con *hash* encadenado o Loki). |
| 9 · Baja | Eficiencia | Sin prueba de carga | k6 o JMeter con 50 usuarios concurrentes contra el gateway. |
| 10 · Baja | Capacidad de interacción | Accesibilidad | Revisión WCAG 2.1 AA (teclado, contraste, lector de pantalla). |

## 5. Verificación en el servidor

Pruebas manuales del criterio de F5 contra el despliegue real (`http://100.65.152.112:8090`). Los resultados se registran en `docs/03-roadmap.md` al desplegar.

- [ ] Todas las rutas `/api/*` responden 401 sin token, también con cabeceras `X-Usuario-Id` falsas.
- [ ] El inicio de sesión funciona con un usuario de prueba; DEPENDIENTE recibe 403 en honorarios.
- [ ] 5 fallos seguidos devuelven 423; muchos intentos desde una IP devuelven 429.
- [ ] El canal de avisos en vivo funciona con token y el token no aparece en los logs.
- [ ] `curl -I` muestra las cabeceras de seguridad.
- [ ] Solo están publicados 8090, 8091 y 8092; Mailpit pide usuario y clave.
