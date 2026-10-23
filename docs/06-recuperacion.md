# 6. Respaldos y recuperación

> F15. Qué se respalda, dónde queda, cómo se comprueba y qué hacer si se pierde el servidor o el PC.

## Qué se respalda

| Qué | Cuándo | En la VM (`infra/respaldos/`) | En el PC (`D:\respaldos\abogados`) |
|---|---|---|---|
| Base de datos (`gabinete-*.dump`, `pg_dump -Fc`) | Cada día a las 02:30 y al arrancar si la última tiene más de un día | 14 días | 30 días |
| Documentos del expediente (`documentos-*.tar.gz`) | Junto con la base | 14 días | 30 días |
| **Llaves** (`llaves-*.p7m`, cifradas): `infra/.env` (claves de la base, de RabbitMQ, de los servicios, del bot, `MFA_CLAVE_CIFRADO`) e `infra/secretos/` (llave que firma los tokens) | Cuando cambian (el vigía lo revisa cada 5 minutos) y al menos cada 7 días | 60 días | 30 días |
| Llave privada que abre el paquete de llaves (`llave\privada.pem` y `certificado.pem`) | Se creó una vez (F15) | **No está** | `D:\respaldos\abogados\llave` (solo el usuario de Windows de Javier puede leerla) |

Sin el paquete de llaves, los respaldos de la base no bastan: habría que crear de nuevo todas las claves, y nadie podría entrar con su verificación en dos pasos (los secretos están cifrados con `MFA_CLAVE_CIFRADO`).

**El paquete de llaves está cifrado** (CMS con AES-256 y RSA-OAEP de 4096 bits) con el certificado `infra/secretos/respaldo-certificado.pem`. La llave privada que lo abre solo está en el PC. Quien se lleve la VM o sus respaldos no puede leer las llaves.

> **Guarde una copia de `D:\respaldos\abogados\llave\privada.pem` fuera del PC** (una memoria USB guardada aparte o un gestor de contraseñas). Si se pierden el PC y esa llave, el paquete de llaves no se puede abrir. En ese caso hay que usar las llaves que siguen en la VM.

## Qué se comprueba solo

| Comprobación | Quién | Si falla |
|---|---|---|
| Cada copia llega al PC con el mismo SHA-256 | Tarea programada "Abogados - copia de respaldos" (9:00 y al iniciar sesión) | Se borra y se reintenta. Queda `ERROR` en `copias.log` |
| El paquete de llaves más reciente se abre con la llave del PC y trae `.env` y `secretos/` | La misma tarea | `ERROR al comprobar` en `copias.log` y código de salida 1 |
| La última copia de la base se restaura en una base temporal (cuenta procesos, clientes, términos y usuarios) | Contenedor `respaldo`, cada domingo a las 04:00. Deja `respaldos/prueba-restauracion.txt` | El vigía avisa por Telegram si falló o si lleva más de 8 días sin hacerse |
| Hay respaldo de la base de menos de 26 horas y paquete de llaves de menos de 8 días | Vigía, cada 5 minutos | Aviso por Telegram |

Para hacerlas a mano:

```bash
docker compose exec respaldo sh /respaldo/respaldar.sh prueba-semanal   # en infra/, en la VM
sh infra/respaldo/respaldar-llaves.sh estado                            # certificado y último paquete
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File infra\respaldo\copiar-al-pc.ps1
```

## Recuperar el sistema en una VM nueva

1. **VM con Docker y Tailscale**, y el repositorio en `~/proyectos/abogados`, en la rama que estaba desplegada.
2. **Llaves**. En el PC, desde Git Bash, abra el paquete de llaves más reciente y súbalo:
   ```bash
   cd /d/respaldos/abogados
   openssl cms -decrypt -binary -inform DER -in llaves-AAAAMMDD-HHMMSS.p7m \
     -inkey llave/privada.pem -recip llave/certificado.pem -out llaves.tar.gz
   scp llaves.tar.gz docker:/tmp/ && rm llaves.tar.gz
   ```
   En la VM:
   ```bash
   cd ~/proyectos/abogados/infra && tar -xzf /tmp/llaves.tar.gz && rm /tmp/llaves.tar.gz
   chmod 600 .env && chmod 700 secretos
   ```
3. **Base de datos**. Copie a `infra/respaldos/` el `gabinete-*.dump` más reciente (de la VM vieja o del PC). Luego:
   ```bash
   docker compose up -d postgres
   docker compose exec -T postgres sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists' \
     < respaldos/gabinete-AAAAMMDD-HHMMSS.dump
   ```
4. **Documentos**:
   ```bash
   docker compose up -d procesos && docker compose stop procesos      # crea el volumen con su dueño
   docker run --rm -v abogados_documentos:/d -v "$PWD/respaldos":/r alpine:3.20 \
     sh -c 'tar -xzf /r/documentos-AAAAMMDD-HHMMSS.tar.gz -C /d && chown -R 100:101 /d'
   ```
   El dueño es el usuario `app` de la imagen de procesos (vea `id` dentro del contenedor si cambia).
5. **Todo lo demás**: `docker compose up -d --build`, `sh monitoreo/instalar.sh` (vigía) y
   `sudo tailscale serve --bg --https=443 http://127.0.0.1:8093`.
6. **Comprobar**: entrar a la app, `sh monitoreo/vigia.sh estado` y `docker compose exec respaldo sh /respaldo/respaldar.sh probar`.

## Si se pierde el PC

La VM sigue con todo, y las llaves en claro siguen en `infra/`. Hay que crear un par de llaves nuevo en el PC nuevo, cambiar el certificado en la VM y crear un paquete:

```bash
mkdir -p /d/respaldos/abogados/llave && cd /d/respaldos/abogados/llave
MSYS_NO_PATHCONV=1 openssl req -x509 -newkey rsa:4096 -nodes -keyout D:/respaldos/abogados/llave/privada.pem \
  -out D:/respaldos/abogados/llave/certificado.pem -days 3650 -subj "/CN=Respaldo de llaves - Gestion Juridica"
scp certificado.pem docker:proyectos/abogados/infra/secretos/respaldo-certificado.pem
ssh docker 'sh ~/proyectos/abogados/infra/respaldo/respaldar-llaves.sh ahora'
```

Limite el acceso a la carpeta `llave` a su usuario: `icacls D:\respaldos\abogados\llave /inheritance:r /grant:r "%USERNAME%:(OI)(CI)F"`. Después, vuelva a programar la tarea de copia (ver README).

## Si se cambia una llave

Al cambiar `infra/.env` o `infra/secretos/`, el vigía crea un paquete nuevo en máximo 5 minutos y la siguiente copia lo baja al PC. Nunca cambie `MFA_CLAVE_CIFRADO` en un sistema en uso: con otra llave nadie podría entrar con su código y habría que reiniciar la verificación de cada cuenta.
