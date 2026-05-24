# tunnels.ps1 — Abre los tuneles SSH al servidor central de BD antes de levantar Docker.
#
# Uso:
#   .\tunnels.ps1              → abre tunnels (pide contrasena una vez)
#   .\tunnels.ps1 -Cerrar      → mata los procesos ssh de tunnel
#   .\tunnels.ps1 -Estado      → muestra si los puertos 15434 y 15985 estan activos
#
# Los tunnels mapean:
#   localhost:15434  →  10.43.98.254:5434  (PostgreSQL central — bd_nacional_vote4tech)
#   localhost:15985  →  10.43.98.254:5985  (CouchDB central)
#
# Requiere: OpenSSH instalado (viene con Windows 10/11).
# Nota: los tunnels mueren si cierras esta ventana de PowerShell.
#       Dejala abierta mientras uses docker compose.

param(
    [switch]$Cerrar,
    [switch]$Estado
)

$VM_HOST  = "10.43.98.254"
$VM_USER  = "estudiante"
$TUNNEL_DB     = "0.0.0.0:15434:localhost:5434"
$TUNNEL_COUCH  = "0.0.0.0:15985:localhost:5985"

function Test-Port($port) {
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect("127.0.0.1", $port)
        $tcp.Close()
        return $true
    } catch { return $false }
}

if ($Estado) {
    $db    = if (Test-Port 15434) { "ACTIVO (OK)" } else { "NO activo" }
    $couch = if (Test-Port 15985) { "ACTIVO (OK)" } else { "NO activo" }
    Write-Host "Puerto 15434 (PostgreSQL central): $db"
    Write-Host "Puerto 15985 (CouchDB central):    $couch"
    exit
}

if ($Cerrar) {
    Get-Process ssh -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -eq "" } |
        Stop-Process -Force
    Write-Host "Tunnels SSH cerrados."
    exit
}

# ── Verificar si ya estan activos ──────────────────────────────────────────────
$dbOk    = Test-Port 15434
$couchOk = Test-Port 15985

if ($dbOk -and $couchOk) {
    Write-Host "Los tunnels ya estan activos:" -ForegroundColor Green
    Write-Host "  localhost:15434 -> ${VM_HOST}:5434  (PostgreSQL)"
    Write-Host "  localhost:15985 -> ${VM_HOST}:5985  (CouchDB)"
    Write-Host ""
    Write-Host "Ahora puedes ejecutar: docker compose up -d --build"
    exit
}

# ── Solicitar contrasena ────────────────────────────────────────────────────────
Write-Host "Servidor central: $VM_USER@$VM_HOST" -ForegroundColor Cyan
$secPass = Read-Host "Contrasena SSH" -AsSecureString
$bstr    = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($secPass)
$pass    = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)

# Escribir askpass temporal
$askpass = "$env:TEMP\v4t_askpass.sh"
$passFile = "$env:TEMP\v4t_pass.txt"
Set-Content -Path $passFile -Value $pass -Encoding UTF8

# Abrir tunnels usando ssh con -o SSH_ASKPASS (via WSL si disponible, o directo)
# Intentamos primero con ssh nativo de Windows usando proceso separado
Write-Host "Abriendo tunnel PostgreSQL (15434)..." -ForegroundColor Yellow
if (-not $dbOk) {
    $procDB = Start-Process -FilePath "ssh" `
        -ArgumentList @(
            "-o", "StrictHostKeyChecking=no",
            "-o", "ServerAliveInterval=30",
            "-o", "ServerAliveCountMax=3",
            "-N", "-L", $TUNNEL_DB,
            "${VM_USER}@${VM_HOST}"
        ) `
        -PassThru -WindowStyle Hidden
    Write-Host "  PID del proceso SSH (DB): $($procDB.Id)"
}

Write-Host "Abriendo tunnel CouchDB (15985)..." -ForegroundColor Yellow
if (-not $couchOk) {
    $procCouch = Start-Process -FilePath "ssh" `
        -ArgumentList @(
            "-o", "StrictHostKeyChecking=no",
            "-o", "ServerAliveInterval=30",
            "-o", "ServerAliveCountMax=3",
            "-N", "-L", $TUNNEL_COUCH,
            "${VM_USER}@${VM_HOST}"
        ) `
        -PassThru -WindowStyle Hidden
    Write-Host "  PID del proceso SSH (CouchDB): $($procCouch.Id)"
}

# Nota: ssh de Windows pedira la contrasena en una ventana emergente o en consola.
# Si tienes clave publica instalada en la VM, no pedira contrasena.

Write-Host ""
Write-Host "Esperando que los tunnels inicien..." -ForegroundColor Cyan
Start-Sleep -Seconds 5

$dbOk2    = Test-Port 15434
$couchOk2 = Test-Port 15985

Write-Host ""
if ($dbOk2 -and $couchOk2) {
    Write-Host "Tunnels activos:" -ForegroundColor Green
    Write-Host "  localhost:15434 -> ${VM_HOST}:5434  (PostgreSQL central)"
    Write-Host "  localhost:15985 -> ${VM_HOST}:5985  (CouchDB central)"
    Write-Host ""
    Write-Host "Ahora puedes ejecutar en otra terminal:" -ForegroundColor White
    Write-Host "  docker compose up -d --build" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "IMPORTANTE: No cierres esta ventana, los tunnels se cerraran." -ForegroundColor Yellow
} else {
    Write-Host "Advertencia: alguno de los tunnels no respondio aun." -ForegroundColor Red
    Write-Host "  Puerto 15434: $(if ($dbOk2) {'OK'} else {'NO responde'})"
    Write-Host "  Puerto 15985: $(if ($couchOk2) {'OK'} else {'NO responde'})"
    Write-Host ""
    Write-Host "Puede que ssh haya pedido la contrasena en una ventana minimizada."
    Write-Host "Busca ventanas de ssh abiertas e ingresa: estudiante@$VM_HOST"
    Write-Host "Contrasena: ArquiSoftVote4Tech"
}

Remove-Item -Path $passFile -ErrorAction SilentlyContinue
