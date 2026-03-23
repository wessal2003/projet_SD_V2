package com.smartcity.analysis.common;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.config.KafkaTopics;
import com.smartcity.common.json.JsonUtils;
import com.smartcity.common.model.SensorEvent;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

public class SensorEventProcessor {
    private static final Logger LOG = LoggerFactory.getLogger(SensorEventProcessor.class);

    private final String serviceName;
    private final MonitoringRepository repository = new MonitoringRepository();
    private final DistributedActuator actuator = new DistributedActuator();
    private final KafkaProducer<String, String> producer;

    public SensorEventProcessor(String serviceName) {
        this.serviceName = serviceName;
        this.producer = new KafkaProducer<>(producerProps());
    }

    public String process(SensorEvent event, Function<SensorEvent, Optional<DetectionResult>> detector) {
        repository.insertMeasurement(event);

        Optional<DetectionResult> detection = detector.apply(event);
        if (detection.isEmpty()) {
            LOG.info("{} processed {} at {} with no alert",
                    serviceName, event.getSensorType(), event.getIntersectionId());
            return "OK";
        }

        DetectionResult result = detection.get();
        long alertId = repository.insertAlert(result.alertType(), event, result.message());
        repository.insertRecommendation(alertId, event, result.recommendation());
        publishKafkaNotifications(event, result);
        actuator.apply(result.alertType(), event, result.recommendation(), result.message());

        LOG.info("{} detection={} intersection={}",
                serviceName, result.alertType(), event.getIntersectionId());
        return result.alertType().name();
    }

    private void publishKafkaNotifications(SensorEvent event, DetectionResult result) {
        try {
            String alertPayload = JsonUtils.MAPPER.writeValueAsString(Map.of(
                    "alertType", result.alertType().name(),
                    "zoneId", event.getZoneId(),
                    "intersectionId", event.getIntersectionId(),
                    "message", result.message(),
                    "timestamp", event.getTimestamp()));

            String recommendationPayload = JsonUtils.MAPPER.writeValueAsString(Map.of(
                    "zoneId", event.getZoneId(),
                    "intersectionId", event.getIntersectionId(),
                    "recommendation", result.recommendation(),
                    "timestamp", event.getTimestamp()));

            producer.send(new ProducerRecord<>(KafkaTopics.CITY_ALERTS, event.getIntersectionId(), alertPayload));
            producer.send(new ProducerRecord<>(KafkaTopics.CITY_RECOMMENDATIONS, event.getIntersectionId(), recommendationPayload));
        } catch (Exception ex) {
            LOG.warn("{} failed to publish Kafka notifications: {}", serviceName, ex.getMessage());
        }
    }

    private Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, AppConfig.kafkaBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return props;
    }
}
