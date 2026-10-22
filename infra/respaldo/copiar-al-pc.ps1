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
param(
    [string]$Destino = 'D:\respaldos\abogados',
    [string]$Servidor = 'docker',
    [string]$Carpeta = 'proyectos/abogados/infra/respaldos',   # relativa a la carpeta del usuario en la VM
    [int]$RetenerDias = 30
)

$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $Destino | Out-Null
$registro = Join-Path $Destino 'copias.log'

function Anotar([string]$texto) {
    $linea = '{0} {1}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $texto
    Add-Content -Path $registro -Value $linea -Encoding UTF8
    Write-Output $linea
}

try {
    # Nombre y SHA-256 de cada respaldo en el servidor
    $lista = & ssh -o BatchMode=yes -o ConnectTimeout=15 $Servidor "cd $Carpeta && sha256sum gabinete-*.dump documentos-*.tar.gz 2>/dev/null"
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
        ($_.Name -like 'gabinete-*.dump' -or $_.Name -like 'documentos-*.tar.gz') -and $_.LastWriteTime -lt $limite
    } | ForEach-Object { Remove-Item $_.FullName -Force; Anotar "borrado por antiguedad: $($_.Name)" }

    Anotar "copia terminada: $nuevos archivo(s) nuevo(s)"
    exit 0
} catch {
    Anotar "ERROR: $($_.Exception.Message)"
    exit 1
}
