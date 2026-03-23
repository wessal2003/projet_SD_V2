$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
$mvn = "C:\Capache-maven-3.9.13\bin\mvn.cmd"
$kafkaHome = "C:\kafka_2.13-4.2.0"
$runDir = Join-Path $projectRoot ".run"
$logDir = Join-Path $runDir "logs"
$kafkaDir = Join-Path $runDir "kafka"
$kafkaDataDir = Join-Path $kafkaDir "data"
$kafkaConfig = Join-Path $kafkaDir "server.properties"
$localConfig = Join-Path $projectRoot "smarttraffic.properties"
$simulatorProfile = if ($env:SMART_TRAFFIC_SIMULATOR_PROFILE) { $env:SMART_TRAFFIC_SIMULATOR_PROFILE } else { "demo" }
$requiredTopics = @(
    "traffic.flow",
    "environment.pollution",
    "environment.noise",
    "incident.accident",
    "city.alerts",
    "city.recommendations"
)

New-Item -ItemType Directory -Force -Path $logDir | Out-Null
New-Item -ItemType Directory -Force -Path $kafkaDir | Out-Null
New-Item -ItemType Directory -Force -Path $kafkaDataDir | Out-Null

function Test-PortListening {
    param([int]$Port)
    return $null -ne (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue)
}

function Wait-Port {
    param(
        [int]$Port,
        [int]$TimeoutSeconds,
        [string]$Label
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListening -Port $Port) {
            Write-Host "$Label is listening on port $Port"
            return
        }
        Start-Sleep -Seconds 1
    }

    throw "$Label did not open port $Port within $TimeoutSeconds seconds."
}

function Start-LoggedProcess {
    param(
        [string]$Name,
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory
    )

    $outLog = Join-Path $logDir "$Name.out.log"
    $errLog = Join-Path $logDir "$Name.err.log"
    Start-Process -FilePath $FilePath `
        -ArgumentList $ArgumentList `
        -WorkingDirectory $WorkingDirectory `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -WindowStyle Hidden | Out-Null
}

function Build-KafkaConfig {
    $baseConfig = Get-Content (Join-Path $kafkaHome "config\server.properties") -Raw
    $logPath = $kafkaDataDir -replace "\\", "/"
    $updated = $baseConfig -replace '(?m)^log\.dirs=.*$', "log.dirs=$logPath"
    Set-Content -Path $kafkaConfig -Value $updated -Encoding ASCII
}

function Ensure-KafkaFormatted {
    $metaFile = Join-Path $kafkaDataDir "meta.properties"
    if (Test-Path $metaFile) {
        return
    }

    $storageTool = Join-Path $kafkaHome "bin\windows\kafka-storage.bat"
    $uuid = (& $storageTool random-uuid | Select-Object -Last 1).Trim()
    if (-not $uuid) {
        throw "Unable to generate Kafka cluster UUID."
    }

    & $storageTool format --standalone -t $uuid -c $kafkaConfig | Out-Null
}

function Ensure-KafkaTopic {
    param([string]$Topic)

    $topicsTool = Join-Path $kafkaHome "bin\windows\kafka-topics.bat"
    & $topicsTool --create `
        --if-not-exists `
        --topic $Topic `
        --bootstrap-server "localhost:9092" `
        --partitions 1 `
        --replication-factor 1 | Out-Null
}

function Start-ServiceIfNeeded {
    param(
        [string]$Name,
        [int]$Port,
        [string]$FilePath,
        [string[]]$ArgumentList,
        [string]$WorkingDirectory,
        [int]$WaitSeconds = 20
    )

    if (Test-PortListening -Port $Port) {
        Write-Host "$Name already running on port $Port"
        return
    }

    Start-LoggedProcess -Name $Name -FilePath $FilePath -ArgumentList $ArgumentList -WorkingDirectory $WorkingDirectory
    Wait-Port -Port $Port -TimeoutSeconds $WaitSeconds -Label $Name
}

function Start-WorkerProcess {
    param(
        [string]$Name,
        [string]$PomPath,
        [string]$MainClass,
        [string[]]$ExtraArgs = @()
    )

    Start-LoggedProcess -Name $Name `
        -FilePath $mvn `
        -ArgumentList (@("-f", $PomPath, "exec:java", "-Dexec.mainClass=$MainClass") + $ExtraArgs) `
        -WorkingDirectory $projectRoot
}

if (-not $env:SMART_TRAFFIC_DB_PASSWORD -and -not (Test-Path $localConfig) -and -not (Test-Path (Join-Path $projectRoot ".env"))) {
    Write-Warning "No SMART_TRAFFIC_DB_PASSWORD env var and no local smarttraffic.properties/.env file found. MySQL authentication may fail."
}

Push-Location $projectRoot
& $mvn "-DskipTests" "install" | Out-Null
Pop-Location

if (-not (Test-PortListening -Port 9092)) {
    Build-KafkaConfig
    Ensure-KafkaFormatted
    Start-LoggedProcess -Name "kafka" `
        -FilePath (Join-Path $kafkaHome "bin\windows\kafka-server-start.bat") `
        -ArgumentList @($kafkaConfig) `
        -WorkingDirectory $projectRoot
    Wait-Port -Port 9092 -TimeoutSeconds 40 -Label "Kafka"
} else {
    Write-Host "Kafka already running on port 9092"
}

foreach ($topic in $requiredTopics) {
    Ensure-KafkaTopic -Topic $topic
}

Start-ServiceIfNeeded -Name "feux-jaxrpc" -Port 8084 -FilePath $mvn `
    -ArgumentList @("-f", ".\traffic-light-rmi-service\pom.xml", "exec:java", "-Dexec.mainClass=com.smartcity.trafficlight.TrafficLightJaxRpcServerMain") `
    -WorkingDirectory $projectRoot

Start-ServiceIfNeeded -Name "soap" -Port 8083 -FilePath $mvn `
    -ArgumentList @("-f", ".\integration-soap-service\pom.xml", "exec:java", "-Dexec.mainClass=com.smartcity.soap.SoapServerMain") `
    -WorkingDirectory $projectRoot

Start-ServiceIfNeeded -Name "traffic-jaxws" -Port 8085 -FilePath $mvn `
    -ArgumentList @("-f", ".\traffic-monitoring-service\pom.xml", "exec:java", "-Dexec.mainClass=com.smartcity.traffic.TrafficMonitoringMain") `
    -WorkingDirectory $projectRoot

Start-ServiceIfNeeded -Name "pollution-rest" -Port 8082 -FilePath $mvn `
    -ArgumentList @("-f", ".\pollution-monitoring-service\pom.xml", "exec:java", "-Dexec.mainClass=com.smartcity.pollution.PollutionMonitoringMain") `
    -WorkingDirectory $projectRoot

Start-ServiceIfNeeded -Name "camera-rmi" -Port 1099 -FilePath $mvn `
    -ArgumentList @("-f", ".\accident-monitoring-service\pom.xml", "exec:java", "-Dexec.mainClass=com.smartcity.accident.AccidentMonitoringMain") `
    -WorkingDirectory $projectRoot

Start-ServiceIfNeeded -Name "noise-socket" -Port 7071 -FilePath $mvn `
    -ArgumentList @("-f", ".\noise-monitoring-service\pom.xml", "exec:java", "-Dexec.mainClass=com.smartcity.noise.NoiseMonitoringMain") `
    -WorkingDirectory $projectRoot

Start-ServiceIfNeeded -Name "collector" -Port 7070 -FilePath $mvn `
    -ArgumentList @("-f", ".\tcp-collector-service\pom.xml", "exec:java", "-Dexec.mainClass=com.smartcity.collector.TcpCollectorMain") `
    -WorkingDirectory $projectRoot

Start-ServiceIfNeeded -Name "dashboard" -Port 8080 -FilePath $mvn `
    -ArgumentList @("-f", ".\dashboard-rest-service\pom.xml", "exec:java", "-Dexec.mainClass=com.smartcity.dashboard.DashboardServerMain") `
    -WorkingDirectory $projectRoot

Start-WorkerProcess -Name "simulator" `
    -PomPath ".\sensor-simulator-service\pom.xml" `
    -MainClass "com.smartcity.simulator.SensorSimulatorMain" `
    -ExtraArgs @("-Dsimulator.profile=$simulatorProfile")

Write-Host "SmartTraffic V2 started."
Write-Host "Dashboard: http://localhost:8080/"
Write-Host "ServicePollution REST: http://localhost:8082/api/pollution/health"
Write-Host "ServiceFluxVehicules WSDL: http://localhost:8085/ws/ServiceFluxVehicules?wsdl"
Write-Host "Logs: $logDir"
