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
    @{ id = "58934340"; url = "https://painel.faws.net.br" },
    @{ id = "44514b9d"; url = "https://painel.faws.net.br" }
)

foreach ($server in $servers) {
    try {
        $serverId = $server.id
        $serverUrl = $server.url
        
        $fileBytes = [System.IO.File]::ReadAllBytes($jarPath)
        
        $headers = @{ 
            Authorization = "Bearer $token"
        }
        
        $uploadUri = "$serverUrl/api/client/servers/$serverId/files/write?file=plugins%2F$jarFile&raw=true"
        
        Invoke-RestMethod -Uri $uploadUri -Headers $headers -Method POST -Body $fileBytes -ContentType "application/octet-stream" -ErrorAction Stop | Out-Null
        Write-Host "Plugin enviado para servidor $serverId"
        
        $restartHeaders = @{ Authorization = "Bearer $token"; "Content-Type" = "application/json" }
        Invoke-RestMethod -Uri "$serverUrl/api/client/servers/$serverId/power" -Method POST -Headers $restartHeaders -Body '{"signal":"restart"}' -ErrorAction Stop | Out-Null
        Write-Host "Server $serverId reiniciado!"
    } catch {
        Write-Warning "Falha ao processar servidor $serverId`: $($_.Exception.Message)"
    }
    Start-Sleep -Seconds 5
}

Write-Host "Deploy completo para $jarFile"