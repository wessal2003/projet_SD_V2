# Smart Traffic - Mini Projet Systemes Distribues

Projet distribue de gestion intelligente du trafic urbain, base sur:
- Java 21
- Sockets TCP
- Java RMI
- JAX-WS (SOAP)
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
Utiliser les memes parametres DB partout:
- `-Dmysql.url="jdbc:mysql://localhost:3306/smart_traffic?serverTimezone=UTC"`
- `-Dmysql.user=root`
- `-Dmysql.password="Wessal*2003"`

### 4.1 RMI Traffic Light
```powershell
mvn -pl traffic-light-rmi-service -am exec:java -Dexec.mainClass=com.smartcity.rmi.TrafficLightRmiServerMain -Dmysql.url="jdbc:mysql://localhost:3306/smart_traffic?serverTimezone=UTC" -Dmysql.user=root -Dmysql.password="Wessal*2003"
```

### 4.2 SOAP Integration
```powershell
mvn -pl integration-soap-service -am exec:java -Dexec.mainClass=com.smartcity.soap.SoapServerMain
```

### 4.3 TCP Collector
```powershell
mvn -pl tcp-collector-service -am exec:java -Dexec.mainClass=com.smartcity.collector.TcpCollectorMain -Dkafka.bootstrap=localhost:9092
```

### 4.4 Analytics Engine
```powershell
mvn -pl analytics-recommendation-service -am exec:java -Dexec.mainClass=com.smartcity.analytics.AnalyticsMain -Dkafka.bootstrap=localhost:9092 -Dmysql.url="jdbc:mysql://localhost:3306/smart_traffic?serverTimezone=UTC" -Dmysql.user=root -Dmysql.password="Wessal*2003"
```

### 4.5 Dashboard REST + UI
```powershell
mvn -pl dashboard-rest-service -am exec:java -Dexec.mainClass=com.smartcity.dashboard.DashboardServerMain -Dmysql.url="jdbc:mysql://localhost:3306/smart_traffic?serverTimezone=UTC" -Dmysql.user=root -Dmysql.password="Wessal*2003" -Drest.base=http://localhost:8080/
```
Ouvrir: `http://localhost:8080/`

### 4.6 Sensor Simulator
```powershell
mvn -pl sensor-simulator-service -am exec:java -Dexec.mainClass=com.smartcity.simulator.SensorSimulatorMain -Dcollector.host=localhost -Dcollector.port=7070
```

## Modules
- `common-model`: DTO, config, interfaces partagees (RMI/SOAP)
- `sensor-simulator-service`: capteurs simules multi-threads
- `tcp-collector-service`: reception TCP et publication Kafka
- `analytics-recommendation-service`: detection + recommandations + MySQL + appels RMI/SOAP
- `traffic-light-rmi-service`: gestion distante des feux
- `integration-soap-service`: service SOAP d'integration
- `dashboard-rest-service`: API REST + interface graphique web
