# Smart Traffic - Mini Projet Systemes Distribues

Projet distribue de gestion intelligente du trafic urbain, base sur:
- Java 21
- Sockets TCP
- Java RMI
- JAX-WS (SOAP)
- JAX-RPC
- JAX-RS (REST)
- Kafka
- MySQL

## Seuils par defaut (realistes)
- Congestion: `vehicle_count > 85` ET `average_speed < 20 km/h`
- Pollution elevee: `PM2.5 > 75`
- Bruit eleve: `noise_db > 85 dB`
- Accident: evenement camera `accidentDetected = true`

## Prerequis
- Java 21
- Maven
- MySQL local avec base `smart_traffic`
- Kafka local (`localhost:9092`)

## 1) Initialisation base MySQL
Executer le script:
- `sql/schema.sql`

## 2) Topics Kafka
Creer les topics:
```powershell
C:\kafka_2.13-4.2.0\bin\windows\kafka-topics.bat --create --topic traffic.flow --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
C:\kafka_2.13-4.2.0\bin\windows\kafka-topics.bat --create --topic environment.pollution --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
C:\kafka_2.13-4.2.0\bin\windows\kafka-topics.bat --create --topic environment.noise --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
C:\kafka_2.13-4.2.0\bin\windows\kafka-topics.bat --create --topic incident.accident --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
C:\kafka_2.13-4.2.0\bin\windows\kafka-topics.bat --create --topic city.alerts --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
C:\kafka_2.13-4.2.0\bin\windows\kafka-topics.bat --create --topic city.recommendations --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
```

## 3) Build Maven
```powershell
mvn -DskipTests package
```

## 4) Lancer les services (1 terminal par service)
Configurer la base via:
- `smarttraffic.properties`
- ou `.env`
- ou variables d'environnement `SMART_TRAFFIC_*`

### 4.1 RMI Traffic Light
```powershell
mvn -pl traffic-light-rmi-service -am exec:java -Dexec.mainClass=com.smartcity.rmi.TrafficLightRmiServerMain
```

### 4.2 SOAP Integration
```powershell
mvn -pl integration-soap-service -am exec:java -Dexec.mainClass=com.smartcity.soap.SoapServerMain
```

### 4.3 TCP Collector
```powershell
mvn -pl tcp-collector-service -am exec:java -Dexec.mainClass=com.smartcity.collector.TcpCollectorMain -Dkafka.bootstrap=localhost:9092
```

### 4.4 Traffic Monitoring
```powershell
mvn -pl traffic-monitoring-service -am exec:java -Dexec.mainClass=com.smartcity.traffic.TrafficMonitoringMain -Dkafka.bootstrap=localhost:9092
```

### 4.5 Pollution Monitoring
```powershell
mvn -pl pollution-monitoring-service -am exec:java -Dexec.mainClass=com.smartcity.pollution.PollutionMonitoringMain -Dkafka.bootstrap=localhost:9092
```

### 4.6 Noise Monitoring
```powershell
mvn -pl noise-monitoring-service -am exec:java -Dexec.mainClass=com.smartcity.noise.NoiseMonitoringMain -Dkafka.bootstrap=localhost:9092
```

### 4.7 Accident Monitoring
```powershell
mvn -pl accident-monitoring-service -am exec:java -Dexec.mainClass=com.smartcity.accident.AccidentMonitoringMain -Dkafka.bootstrap=localhost:9092
```

### 4.8 Dashboard REST + UI
```powershell
mvn -pl dashboard-rest-service -am exec:java -Dexec.mainClass=com.smartcity.dashboard.DashboardServerMain -Drest.base=http://localhost:8080/
```
Ouvrir: `http://localhost:8080/`

### 4.9 Operations REST
```powershell
mvn -pl operations-rest-service -am exec:java -Dexec.mainClass=com.smartcity.operations.OperationsServerMain -Doperations.base=http://localhost:8081/
```
Tester: `http://localhost:8081/api/operations/health`

### 4.10 Sensor Simulator
```powershell
mvn -pl sensor-simulator-service -am exec:java -Dexec.mainClass=com.smartcity.simulator.SensorSimulatorMain -Dcollector.host=localhost -Dcollector.port=7070
```

## Modules
- `common-model`: DTO, config, interfaces partagees (RMI/SOAP)
- `legacy-jaxrpc-client`: client legacy JAX-RPC pour l'integration SOAP
- `analysis-common`: logique commune de monitoring, persistence, publication et actionneurs
- `sensor-simulator-service`: capteurs simules multi-threads
- `tcp-collector-service`: reception TCP et publication Kafka
- `traffic-monitoring-service`: surveillance du flux vehicules et detection de congestion
- `pollution-monitoring-service`: surveillance pollution et detection PM2.5 eleve
- `noise-monitoring-service`: surveillance bruit et detection nuisance sonore
- `accident-monitoring-service`: surveillance camera et detection d'accident
- `traffic-light-rmi-service`: gestion distante des feux
- `integration-soap-service`: service SOAP JAX-WS d'integration
- `operations-rest-service`: service REST metier pour supervision operationnelle et commande RMI
- `dashboard-rest-service`: API REST + interface graphique web


