package com.smartcity.analysis.common;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.config.KafkaTopics;
import com.smartcity.common.json.JsonUtils;
import com.smartcity.common.model.SensorEvent;
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
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public abstract class MonitoringServiceRunner {
    private static final Logger LOG = LoggerFactory.getLogger(MonitoringServiceRunner.class);

    private final String serviceName;
    private final String sourceTopic;
    private final MonitoringRepository repository = new MonitoringRepository();
    private final DistributedActuator actuator = new DistributedActuator();

    protected MonitoringServiceRunner(String serviceName, String sourceTopic) {
        this.serviceName = serviceName;
        this.sourceTopic = sourceTopic;
    }

    public void start() {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps());
             KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps())) {
            consumer.subscribe(java.util.List.of(sourceTopic));
            LOG.info("{} started. Kafka bootstrap={} topic={}",
                    serviceName, AppConfig.kafkaBootstrapServers(), sourceTopic);

            while (true) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                for (ConsumerRecord<String, String> record : records) {
                    handleRecord(record.value(), producer);
                }
            }
        }
    }

    protected abstract Optional<DetectionResult> detect(SensorEvent event);

    private void handleRecord(String payload, KafkaProducer<String, String> producer) {
        try {
            SensorEvent event = JsonUtils.MAPPER.readValue(payload, SensorEvent.class);
            repository.insertMeasurement(event);

            Optional<DetectionResult> detection = detect(event);
            if (detection.isEmpty()) {
                return;
            }

            DetectionResult result = detection.get();
            long alertId = repository.insertAlert(result.alertType(), event, result.message());
            repository.insertRecommendation(alertId, event, result.recommendation());

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

            actuator.apply(result.alertType(), event, result.recommendation(), result.message());
            LOG.info("{} detection={} intersection={}",
                    serviceName, result.alertType(), event.getIntersectionId());
        } catch (Exception ex) {
            LOG.error("{} failed to process record: {}", serviceName, ex.getMessage(), ex);
        }
    }

    private Properties consumerProps() {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, AppConfig.kafkaBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, serviceName);
        props.put(ConsumerConfig.CLIENT_ID_CONFIG, serviceName + "-client");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        return props;
    }

    private Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, AppConfig.kafkaBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        return props;
    }
}
