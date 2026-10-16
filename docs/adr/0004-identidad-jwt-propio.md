# ADR 0004: Identidad con JWT propio

- **Estado:** aceptada
- **Fecha:** 2026-10-15

## Contexto

La evaluación ISO/IEC 25010 del laboratorio dio **4/10 en Seguridad**: ningún servicio pedía autenticación y las contraseñas se guardaban en texto plano. El proyecto ya tiene la tabla `usuarios` y la relación abogado → cuenta de usuario (1 : 0..1).

## Decisión

Servicio `identidad` con **Spring Security**:
- Login con usuario y contraseña (guardada con **BCrypt**).
- Emite un **JWT** firmado con los roles (`ADMIN`, `SOCIO`, `ABOGADO`, `DEPENDIENTE`, `CLIENTE`).
- El `gateway` valida el token en cada petición y pasa el usuario y los roles a los servicios en cabeceras.

## Alternativas consideradas

| Alternativa | Por qué no |
|---|---|
| Keycloak | Muy completo (SSO, OAuth2), pero ≈ 700 MB de RAM y mucha configuración para esta etapa. Se puede adoptar después, porque el gateway solo valida JWT. |
| Sesiones con cookie por servicio | No escala a varios servicios y complica el frontend. |

## Consecuencias

- **Positivas:**
  - Liviano.
  - Reutiliza la tabla de usuarios.
  - Resuelve los puntos 1 y 2 del plan de mejora ISO.
- **Negativas:**
  - La rotación de claves y la revocación de tokens quedan a cargo del proyecto.
  - Los tokens deben ser de vida corta, con *refresh token*.

## Implementación (F5, 2026-10-16)

- **Firma:** RS256 en lugar de HMAC. Solo `identidad` tiene la llave privada (archivo montado de solo lectura). El gateway descarga la pública de `/.well-known/jwks.json`, así que no comparte ningún secreto.
- **Roles:** se agregó `ADMINISTRATIVO` (contador, secretaria). Los permisos por ruta están en una sola tabla del gateway (`PoliticaAcceso`); lo que no aparece se niega.
- **Vida del token:** 8 h (una jornada), **sin** *refresh token* ni revocación todavía. Es aceptable dentro de la VPN; queda como prioridad 2 del plan de mejora (`docs/04-evaluacion-iso25010.md`) antes de publicar fuera de ella.
- **Fuerza bruta:** bloqueo por cuenta (5 fallos → 15 min) y límite por IP (20 intentos / 10 min, en memoria: válido con una sola instancia).
