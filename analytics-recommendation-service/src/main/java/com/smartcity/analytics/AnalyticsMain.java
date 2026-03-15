package com.smartcity.analytics;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.config.KafkaTopics;
import com.smartcity.common.config.Thresholds;
import com.smartcity.common.json.JsonUtils;
import com.smartcity.common.model.AlertType;
import com.smartcity.common.model.SensorEvent;
import com.smartcity.common.model.SensorType;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class AnalyticsMain {
    private static final Logger LOG = LoggerFactory.getLogger(AnalyticsMain.class);

    public static void main(String[] args) {
        DatabaseRepository repository = new DatabaseRepository();
        ExternalActuator actuator = new ExternalActuator();

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps());
             KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {

            consumer.subscribe(List.of(
                    KafkaTopics.TRAFFIC_FLOW,
                    KafkaTopics.ENV_POLLUTION,
                    KafkaTopics.ENV_NOISE,
                    KafkaTopics.INCIDENT_ACCIDENT));

            LOG.info("Analytics engine started. Kafka bootstrap={}", AppConfig.kafkaBootstrapServers());

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    handleRecord(record.value(), repository, actuator, producer);
                }
            }
        }
    }

    private static void handleRecord(
            String payload,
            DatabaseRepository repository,
            ExternalActuator actuator,
            KafkaProducer<String, String> producer) {
        try {
            SensorEvent event = JsonUtils.MAPPER.readValue(payload, SensorEvent.class);
            repository.insertMeasurement(event);

            Optional<DetectionResult> detection = detect(event);
            if (detection.isEmpty()) {
                return;
            }

            DetectionResult result = detection.get();
            long alertId = repository.insertAlert(result.alertType, event, result.message);
            repository.insertRecommendation(alertId, event, result.recommendation);

            String alertPayload = JsonUtils.MAPPER.writeValueAsString(Map.of(
                    "alertType", result.alertType.name(),
                    "zoneId", event.getZoneId(),
                    "intersectionId", event.getIntersectionId(),
                    "message", result.message,
                    "timestamp", event.getTimestamp()));

            String recommendationPayload = JsonUtils.MAPPER.writeValueAsString(Map.of(
                    "zoneId", event.getZoneId(),
                    "intersectionId", event.getIntersectionId(),
                    "recommendation", result.recommendation,
                    "timestamp", event.getTimestamp()));

            producer.send(new ProducerRecord<>(KafkaTopics.CITY_ALERTS, event.getIntersectionId(), alertPayload));
            producer.send(new ProducerRecord<>(KafkaTopics.CITY_RECOMMENDATIONS, event.getIntersectionId(), recommendationPayload));

            actuator.apply(result.alertType, event, result.recommendation, result.message);
            LOG.info("Detection={} intersection={} recommendation={}",
                    result.alertType, event.getIntersectionId(), result.recommendation);
        } catch (Exception ex) {
            LOG.error("Failed to process record: {}", ex.getMessage(), ex);
        }
    }

    private static Optional<DetectionResult> detect(SensorEvent event) {
        if (event.getSensorType() == SensorType.TRAFFIC_FLOW
                && event.getVehicleCount() > Thresholds.CONGESTION_VEHICLE_THRESHOLD
                && event.getAverageSpeedKmh() < Thresholds.CONGESTION_SPEED_THRESHOLD) {
            return Optional.of(new DetectionResult(
                    AlertType.CONGESTION,
                    "Congestion detectee a " + event.getIntersectionId()
                            + " (vehicules=" + event.getVehicleCount()
                            + ", vitesse=" + event.getAverageSpeedKmh() + " km/h)",
                    "Allonger le feu vert de 15 secondes et proposer un itineraire alternatif."));
        }

        if (event.getSensorType() == SensorType.POLLUTION
                && event.getPm25() > Thresholds.POLLUTION_PM25_THRESHOLD) {
            return Optional.of(new DetectionResult(
                    AlertType.HIGH_POLLUTION,
                    "Pollution elevee (PM2.5=" + event.getPm25() + ") a " + event.getIntersectionId(),
                    "Limiter le trafic sur la zone et favoriser les transports publics."));
        }

        if (event.getSensorType() == SensorType.NOISE
                && event.getNoiseDb() > Thresholds.NOISE_DB_THRESHOLD) {
            return Optional.of(new DetectionResult(
                    AlertType.HIGH_NOISE,
                    "Niveau de bruit eleve (" + event.getNoiseDb() + " dB) a " + event.getIntersectionId(),
                    "Lancer une alerte locale et adapter le plan de circulation."));
        }

        if (event.getSensorType() == SensorType.ACCIDENT_CAMERA && event.isAccidentDetected()) {
            return Optional.of(new DetectionResult(
                    AlertType.ACCIDENT,
                    "Accident detecte par camera a " + event.getIntersectionId(),
                    "Alerter les services d'urgence et devier le trafic."));
        }

        return Optional.empty();
    }

    private static Properties consumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, AppConfig.kafkaBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "smart-traffic-analytics");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return props;
    }

    private static Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, AppConfig.kafkaBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return props;
    }

    private record DetectionResult(AlertType alertType, String message, String recommendation) {
    }
}
