param([string]$jarPath)

# 1. Copy to local plugins folder
$pluginsDir = Join-Path $PSScriptRoot "plugins"
if (Test-Path $jarPath) {
    Copy-Item -Path $jarPath -Destination (Join-Path $pluginsDir (Split-Path $jarPath -Leaf)) -Force
    Write-Host "Plugin copiado para plugins/"
} else {
    Write-Warning "JAR nao encontrado: $jarPath"
    exit 1
}

# 2. Restart server via panel API
$tokenPath = Join-Path $PSScriptRoot "cp.txt"
if (-not (Test-Path $tokenPath)) {
    Write-Warning "cp.txt not found - skipping restart"
    exit 0
}

$lines = Get-Content $tokenPath
$token = $lines[0].Trim()
if (-not $token) {
    Write-Warning "Empty token in cp.txt - skipping restart"
    exit 0
}

$serverUrl = $lines[1].Trim()
if (-not $serverUrl) {
    Write-Warning "Server URL not found in cp.txt line 2 - skipping restart"
    exit 0
}

$uri = [System.Uri]$serverUrl
$panelUrl = $uri.GetLeftPart([System.UriPartial]::Authority)
$serverId = $uri.Segments[-1]

try {
    $restartHeaders = @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" }
    Invoke-RestMethod -Uri "$panelUrl/api/client/servers/$serverId/power" -Method POST -Headers $restartHeaders -Body '{"signal":"restart"}' -ErrorAction Stop | Out-Null
    Write-Host "Server reiniciado!"
} catch {
    Write-Warning "Falha ao reiniciar servidor: $($_.Exception.Message)"
}
