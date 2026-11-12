# Copia al PC los respaldos diarios que deja el contenedor "respaldo" en la VM (F10).
# Asi una copia queda en otro equipo fisico: si el servidor se dana, la base y los documentos se pueden restaurar.
#
# Uso:   powershell -NoProfile -ExecutionPolicy Bypass -File copiar-al-pc.ps1 [-Destino D:\respaldos\abogados]
# Lo ejecuta una tarea programada de Windows (ver README, "Copias fuera de la VM").
#
# - Descarga por SSH (alias "docker" de ~/.ssh/config) los archivos que aun no tiene.
# - Comprueba el SHA-256 de cada archivo contra el del servidor; si no coincide, lo borra y lo reintenta otro dia.
# - Conserva RetenerDias dias en el PC (por defecto 30), salvo los sellos de auditoria, que se guardan todos.
# - Deja un registro en <Destino>\copias.log.
# - F15: tambien descarga el paquete cifrado de las llaves (llaves-*.p7m) y comprueba que la llave privada del PC
#   (<Destino>\llave\privada.pem) lo abre y que trae .env y secretos/. Lo descifrado va a un archivo temporal que se
#   borra enseguida. Ver docs/06-recuperacion.md.
# - Bitacora de auditoria: descarga los sellos (sello-auditoria-*.txt, numero y hash del ultimo registro de cada dia),
#   los conserva todos y comprueba que el servidor todavia tiene esos mismos hashes. Si alguien rehiciera o recortara
#   la bitacora en el servidor, un sello guardado aqui dejaria de coincidir. Ver docs/14-auditoria.md.
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

# Cada sello guardado en el PC debe seguir igual en el servidor. Resultado en $script:sellosOk.
$script:sellosOk = $true
function Probar-Sellos {
    $sellos = @(Get-ChildItem $Destino -Filter 'sello-auditoria-*.txt' -File | ForEach-Object {
        $p = @((Get-Content $_.FullName -TotalCount 1) -split '\s+')
        if ($p.Count -ge 2 -and $p[0] -match '^\d+$' -and $p[1] -match '^[0-9a-f]{64}$') {
            [pscustomobject]@{ Id = [long]$p[0]; Hash = $p[1]; Nombre = $_.Name }
        }
    })
    if ($sellos.Count -eq 0) { Anotar 'AVISO: aun no hay sellos de la bitacora de auditoria'; return }
    $ids = ($sellos | ForEach-Object { $_.Id } | Sort-Object -Unique) -join ','
    $sql = "SELECT id || ' ' || hash FROM auditoria.registros WHERE id IN ($ids);"
    # Sin comillas dobles dentro: PowerShell 5.1 las pierde al pasarlas a ssh
    $remoto = "cd $Carpeta/.. && docker compose exec -T postgres sh -c 'psql -tA -U `$POSTGRES_USER -d `$POSTGRES_DB'"
    $filas = $sql | & ssh -o BatchMode=yes -o ConnectTimeout=15 $Servidor $remoto
    if ($LASTEXITCODE -ne 0) {
        Anotar "ERROR: no se pudieron comprobar los sellos de la bitacora de auditoria (codigo $LASTEXITCODE)"
        $script:sellosOk = $false
        return
    }
    $enServidor = @{}
    foreach ($f in @($filas)) {
        $p = @("$f".Trim() -split ' ')
        if ($p.Count -eq 2) { $enServidor[[long]$p[0]] = $p[1] }
    }
    $malos = @($sellos | Where-Object { $enServidor[$_.Id] -ne $_.Hash })
    if ($malos.Count -gt 0) {
        $script:sellosOk = $false
        foreach ($m in $malos) {
            Anotar ("ERROR: la bitacora de auditoria ya no coincide con el sello {0} (registro {1}): alguien la modifico en el servidor" -f $m.Nombre, $m.Id)
        }
    } else {
        $ultimo = ($sellos | Measure-Object -Property Id -Maximum).Maximum
        Anotar ("sellos de auditoria verificados: {0} sello(s) coinciden con el servidor (hasta el registro {1})" -f $sellos.Count, $ultimo)
    }
}

try {
    # Nombre y SHA-256 de cada respaldo en el servidor
    $lista = & ssh -o BatchMode=yes -o ConnectTimeout=15 $Servidor "cd $Carpeta && sha256sum gabinete-*.dump documentos-*.tar.gz llaves-*.p7m sello-auditoria-*.txt 2>/dev/null"
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
    Probar-Sellos
    Anotar "copia terminada: $nuevos archivo(s) nuevo(s)"
    if (-not $script:llavesOk -or -not $script:sellosOk) { exit 1 }
    exit 0
} catch {
    Anotar "ERROR: $($_.Exception.Message)"
    exit 1
}
