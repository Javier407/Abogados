# Copia al PC los respaldos diarios que deja el contenedor "respaldo" en la VM (F10).
# Asi una copia queda en otro equipo fisico: si el servidor se dana, la base y los documentos se pueden restaurar.
#
# Uso:   powershell -NoProfile -ExecutionPolicy Bypass -File copiar-al-pc.ps1 [-Destino D:\respaldos\abogados]
# Lo ejecuta una tarea programada de Windows (ver README, "Copias fuera de la VM").
#
# - Descarga por SSH (alias "docker" de ~/.ssh/config) los archivos que aun no tiene.
# - Comprueba el SHA-256 de cada archivo contra el del servidor; si no coincide, lo borra y lo reintenta otro dia.
# - Conserva RetenerDias dias en el PC (por defecto 30).
# - Deja un registro en <Destino>\copias.log.
# - F15: tambien descarga el paquete cifrado de las llaves (llaves-*.p7m) y comprueba que la llave privada del PC
#   (<Destino>\llave\privada.pem) lo abre y que trae .env y secretos/. Lo descifrado va a un archivo temporal que se
#   borra enseguida. Ver docs/06-recuperacion.md.
param(
    [string]$Destino = 'D:\respaldos\abogados',
    [string]$Servidor = 'docker',
    [string]$Carpeta = 'proyectos/abogados/infra/respaldos',   # relativa a la carpeta del usuario en la VM
    [int]$RetenerDias = 30,
    [string]$OpenSsl = 'C:\Program Files\Git\usr\bin\openssl.exe'
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $Destino | Out-Null
$registro = Join-Path $Destino 'copias.log'

function Anotar([string]$texto) {
    $linea = '{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $texto
    Add-Content -Path $registro -Value $linea -Encoding UTF8
    Write-Output $linea
}

# El paquete de llaves mas reciente se abre con la llave privada del PC y trae lo necesario para reconstruir.
# Deja el resultado en $script:llavesOk (Anotar escribe en la salida, asi que no se usa el valor de retorno).
$script:llavesOk = $true
function Probar-Llaves {
    $paquete = Get-ChildItem $Destino -Filter 'llaves-*.p7m' -File | Sort-Object Name -Descending | Select-Object -First 1
    $privada = Join-Path $Destino 'llave\privada.pem'
    $certificado = Join-Path $Destino 'llave\certificado.pem'
    if (-not $paquete) { Anotar 'AVISO: aun no hay paquete de llaves'; return }
    if (-not (Test-Path $privada) -or -not (Test-Path $OpenSsl)) {
        Anotar "ERROR: no se puede comprobar $($paquete.Name): falta la llave privada o openssl"
        $script:llavesOk = $false
        return
    }
    $temporal = Join-Path $env:TEMP ('llaves-' + [guid]::NewGuid().ToString('N') + '.tar.gz')
    try {
        & $OpenSsl cms -decrypt -binary -inform DER -in $paquete.FullName -inkey $privada -recip $certificado -out $temporal 2>$null
        if ($LASTEXITCODE -ne 0) { throw "openssl no pudo descifrarlo (codigo $LASTEXITCODE)" }
        # El tar de Windows por su ruta: el de Git (si va antes en el PATH) no entiende rutas como C:\...
        $contenido = & (Join-Path $env:SystemRoot 'System32\tar.exe') -tzf $temporal
        if (-not ($contenido -contains '.env') -or -not ($contenido | Where-Object { $_ -like 'secretos/*.pem' })) {
            throw 'no trae .env y secretos/'
        }
        $archivos = @($contenido | Where-Object { $_ -notlike '*/' }).Count
        Anotar ("llaves verificadas: {0} se abre con la llave del PC ({1} archivos)" -f $paquete.Name, $archivos)
    } catch {
        Anotar "ERROR al comprobar $($paquete.Name): $($_.Exception.Message)"
        $script:llavesOk = $false
    } finally {
        if (Test-Path $temporal) { Remove-Item $temporal -Force }
    }
}

try {
    # Nombre y SHA-256 de cada respaldo en el servidor
    $lista = & ssh -o BatchMode=yes -o ConnectTimeout=15 $Servidor "cd $Carpeta && sha256sum gabinete-*.dump documentos-*.tar.gz llaves-*.p7m 2>/dev/null"
    if ($LASTEXITCODE -ne 0 -and -not $lista) { throw "No se pudo listar los respaldos en $Servidor (codigo $LASTEXITCODE)" }

    $nuevos = 0
    foreach ($fila in $lista) {
        $partes = $fila -split '\s+', 2
        if ($partes.Count -lt 2) { continue }
        $hash = $partes[0].ToUpper()
        $nombre = $partes[1].Trim()
        $local = Join-Path $Destino $nombre
        if (Test-Path $local) { continue }

        & scp -q -o BatchMode=yes "${Servidor}:$Carpeta/$nombre" "$local.parcial"
        if ($LASTEXITCODE -ne 0) { Anotar "ERROR al descargar $nombre (codigo $LASTEXITCODE)"; continue }
        $copiado = (Get-FileHash -Algorithm SHA256 "$local.parcial").Hash
        if ($copiado -ne $hash) {
            Remove-Item "$local.parcial" -Force
            Anotar "ERROR: $nombre llego distinto (SHA-256 no coincide); se reintenta en la proxima copia"
            continue
        }
        Move-Item "$local.parcial" $local
        $nuevos++
        Anotar ("copiado {0} ({1:N1} MB)" -f $nombre, ((Get-Item $local).Length / 1MB))
    }

    $limite = (Get-Date).AddDays(-$RetenerDias)
    Get-ChildItem $Destino -File | Where-Object {
        ($_.Name -like 'gabinete-*.dump' -or $_.Name -like 'documentos-*.tar.gz' -or $_.Name -like 'llaves-*.p7m') -and $_.LastWriteTime -lt $limite
    } | ForEach-Object { Remove-Item $_.FullName -Force; Anotar "borrado por antiguedad: $($_.Name)" }

    Probar-Llaves
    Anotar "copia terminada: $nuevos archivo(s) nuevo(s)"
    if (-not $script:llavesOk) { exit 1 }
    exit 0
} catch {
    Anotar "ERROR: $($_.Exception.Message)"
    exit 1
}
