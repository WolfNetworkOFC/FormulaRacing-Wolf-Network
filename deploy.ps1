param([string]$jarPath, [switch]$SkipRestart)

$jarFile = Split-Path $jarPath -Leaf

$pluginsDir = Join-Path $PSScriptRoot "plugins"
if (Test-Path $jarPath) {
    Copy-Item -Path $jarPath -Destination (Join-Path $pluginsDir $jarFile) -Force
    Write-Host "Plugin copiado para plugins/$jarFile"
} else {
    Write-Warning "JAR nao encontrado: $jarPath"
    exit 1
}

if ($SkipRestart) {
    Write-Host "SkipRestart ativado - pulando reinicio"
    exit 0
}

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

$servers = @(
    "https://painel.faws.net.br/server/58934340",
    "https://painel.faws.net.br/server/44514b9d"
)

foreach ($serverUrl in $servers) {
    try {
        $uri = [System.Uri]$serverUrl
        $panelUrl = $uri.GetLeftPart([System.UriPartial]::Authority)
        $serverId = $uri.Segments[-1]

        $restartHeaders = @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" }
        Invoke-RestMethod -Uri "$panelUrl/api/client/servers/$serverId/power" -Method POST -Headers $restartHeaders -Body '{"signal":"restart"}' -ErrorAction Stop | Out-Null
        Write-Host "Server $serverId reiniciado!"
    } catch {
        $errMsg = $_.Exception.Message
        Write-Warning "Falha ao reiniciar servidor $serverUrl`: $errMsg"
    }
    Start-Sleep -Seconds 5
}

Write-Host "Deploy completo para $jarFile"