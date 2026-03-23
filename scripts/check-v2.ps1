$ErrorActionPreference = "Stop"

$ports = @(7070, 7071, 8080, 8082, 8083, 8084, 8085, 9092, 1099)

function Assert-Port {
    param([int]$Port)
    if ($null -eq (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)) {
        throw "Port $Port is not listening."
    }
}

foreach ($port in $ports) {
    Assert-Port -Port $port
}

$dashboardHealth = Invoke-RestMethod -Uri "http://localhost:8080/api/dashboard/health"
$pollutionHealth = Invoke-RestMethod -Uri "http://localhost:8082/api/pollution/health"

if ($dashboardHealth.status -ne "UP") {
    throw "Dashboard REST is not healthy."
}

if ($pollutionHealth.status -ne "UP") {
    throw "ServicePollution is not healthy."
}

$pipelineBefore = Invoke-RestMethod -Uri "http://localhost:8080/api/dashboard/live-metrics"
Start-Sleep -Seconds 8
$pipelineAfter = Invoke-RestMethod -Uri "http://localhost:8080/api/dashboard/live-metrics"

if ([long]$pipelineAfter.lastMeasurementId -le [long]$pipelineBefore.lastMeasurementId) {
    throw "No new measurements detected in the pipeline."
}

Write-Host "SmartTraffic V2 is healthy."
Write-Host "Measurements are moving: $($pipelineBefore.lastMeasurementId) -> $($pipelineAfter.lastMeasurementId)"
