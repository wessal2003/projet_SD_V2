package com.smartcity.collector;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.config.KafkaTopics;
import com.smartcity.common.json.JsonUtils;
import com.smartcity.common.model.SensorEvent;
import com.smartcity.common.model.SensorType;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpCollectorMain {
    private static final Logger LOG = LoggerFactory.getLogger(TcpCollectorMain.class);

    public static void main(String[] args) throws Exception {
        int port = AppConfig.collectorPort();
        ExecutorService pool = Executors.newFixedThreadPool(8);

        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, AppConfig.kafkaBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props);
             ServerSocket serverSocket = new ServerSocket(port)) {
            LOG.info("TCP collector listening on {}", port);
            while (true) {
                Socket client = serverSocket.accept();
                pool.submit(() -> handleClient(client, producer));
            }
        }
    }

    private static void handleClient(Socket client, KafkaProducer<String, String> producer) {
        try (client;
             BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                SensorEvent event = JsonUtils.MAPPER.readValue(line, SensorEvent.class);
                String topic = resolveTopic(event.getSensorType());
                producer.send(new ProducerRecord<>(topic, event.getIntersectionId(), line));
                LOG.info("Published event {} -> topic {}", event.getSensorType(), topic);
            }
        } catch (Exception ex) {
            LOG.warn("TCP client disconnected or invalid payload: {}", ex.getMessage());
        }
    }

    private static String resolveTopic(SensorType sensorType) {
        if (sensorType == null) {
            return KafkaTopics.TRAFFIC_FLOW;
        }
        return switch (sensorType) {
            case TRAFFIC_FLOW -> KafkaTopics.TRAFFIC_FLOW;
            case POLLUTION -> KafkaTopics.ENV_POLLUTION;
            case NOISE -> KafkaTopics.ENV_NOISE;
            case ACCIDENT_CAMERA -> KafkaTopics.INCIDENT_ACCIDENT;
        };
    }
}
