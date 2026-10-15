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
