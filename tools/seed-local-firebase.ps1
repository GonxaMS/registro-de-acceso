param(
    [string]$ProjectId = "registro-guardias-408cb",
    [string]$HostName = "127.0.0.1",
    [int]$Port = 8080,
    [string]$AdminUid = "",
    [switch]$KeepData
)

$ErrorActionPreference = "Stop"
$baseUrl = "http://${HostName}:${Port}/v1/projects/${ProjectId}/databases/(default)/documents"
$headers = @{ Authorization = "Bearer owner" }
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ss.fffZ")

if (-not $KeepData) {
    $resetUrl = "http://${HostName}:${Port}/emulator/v1/projects/${ProjectId}/databases/(default)/documents"
    Invoke-RestMethod -Method Delete -Uri $resetUrl -Headers $headers | Out-Null
}

function String-Field([string]$value) { return @{ stringValue = $value } }
function Bool-Field([bool]$value) { return @{ booleanValue = $value } }
function Int-Field([long]$value) { return @{ integerValue = [string]$value } }
function Time-Field([string]$value) { return @{ timestampValue = $value } }
function Put-Document([string]$path, [hashtable]$fields) {
    $body = @{ fields = $fields } | ConvertTo-Json -Depth 8
    Invoke-RestMethod -Method Patch -Uri "${baseUrl}/${path}" -Headers $headers -ContentType "application/json" -Body $body | Out-Null
}

Put-Document "meta/config" @{
    siguienteId = Int-Field 4
    siguienteMovimiento = Int-Field 1
    siguienteLlave = Int-Field 3
    siguienteMovimientoLlave = Int-Field 1
}

if ($AdminUid) {
    Put-Document "administradores/$AdminUid" @{
        activo = Bool-Field $true
        nombre = String-Field "Administrador local"
    }
}

$people = @(
    @{ id = "P0001"; name = "ACOSTA MARTINA" },
    @{ id = "P0002"; name = "BENITEZ LAUTARO" },
    @{ id = "P0003"; name = "FERNANDEZ AGUSTINA" }
)
foreach ($person in $people) {
    Put-Document "personal/$($person.id)" @{
        nombre = String-Field $person.name
        estado = String-Field "Fuera"
        ultimoMovimiento = String-Field ""
        fecha = String-Field ""
        hora = String-Field ""
        activo = Bool-Field $true
        retirado = Bool-Field $false
        actualizado = Time-Field $timestamp
    }
}

$keys = @(
    @{ id = "K0001"; name = "PORTON PRINCIPAL" },
    @{ id = "K0002"; name = "DEPOSITO" }
)
foreach ($key in $keys) {
    Put-Document "llaves/$($key.id)" @{
        nombre = String-Field $key.name
        estado = String-Field "Disponible"
        quienTiene = String-Field ""
        quienTieneId = String-Field ""
        fechaRetiro = String-Field ""
        horaRetiro = String-Field ""
        ultimoMovimiento = String-Field ""
        ultimoMovimientoId = String-Field ""
        ultimaFecha = String-Field ""
        ultimaHora = String-Field ""
        activo = Bool-Field $true
        actualizado = Time-Field $timestamp
    }
}

Write-Host "Firebase local cargado: 3 operarios y 2 llaves."
if ($AdminUid) { Write-Host "Administrador local habilitado: $AdminUid" }
