package com.smartcity.collector;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.config.KafkaTopics;
import com.smartcity.common.json.JsonUtils;
import com.smartcity.common.model.SensorEvent;
import com.smartcity.common.model.SensorType;
import com.smartcity.common.rmi.CameraRemoteService;
import com.smartcity.common.soap.ServiceFluxVehicules;
import jakarta.xml.ws.BindingProvider;
import jakarta.xml.ws.Service;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.rmi.Naming;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TcpCollectorMain {
    private static final Logger LOG = LoggerFactory.getLogger(TcpCollectorMain.class);
    private static final String TRAFFIC_NS = "http://traffic.soap.smartcity.com/";
    private static final String TRAFFIC_SERVICE_NAME = "ServiceFluxVehiculesService";
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS = 4000;
    private static volatile Service trafficSoapService;
    private static volatile CameraRemoteService cameraRemoteService;

    public static void main(String[] args) throws Exception {
        int port = AppConfig.collectorPort();
        ExecutorService pool = Executors.newFixedThreadPool(24);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps());
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
            client.setSoTimeout(READ_TIMEOUT_MS);
            LOG.info("TCP client connected from {}", client.getRemoteSocketAddress());

            String line;
            while ((line = reader.readLine()) != null) {
                SensorEvent event = JsonUtils.MAPPER.readValue(line, SensorEvent.class);
                String topic = resolveTopic(event.getSensorType());
                producer.send(new ProducerRecord<>(topic, event.getIntersectionId(), line));
                try {
                    dispatch(event, line);
                    LOG.info("TCP_RECEIVED sensor={} intersection={} -> Kafka topic={} -> service dispatch",
                            event.getSensorType(), event.getIntersectionId(), topic);
                } catch (Exception dispatchError) {
                    LOG.warn("Dispatch failed for sensor={} intersection={}: {}",
                            event.getSensorType(), event.getIntersectionId(), dispatchError.getMessage());
                }
            }
        } catch (Exception ex) {
            LOG.warn("TCP client disconnected or invalid payload: {}", ex.getMessage());
        }
    }

    private static void dispatch(SensorEvent event, String payload) throws Exception {
        if (event.getSensorType() == null) {
            return;
        }
        switch (event.getSensorType()) {
            case TRAFFIC_FLOW -> dispatchTraffic(event);
            case POLLUTION -> dispatchPollution(payload);
            case NOISE -> dispatchNoise(payload);
            case ACCIDENT_CAMERA -> dispatchCamera(event);
        }
    }

    private static void dispatchTraffic(SensorEvent event) throws Exception {
        Service service = trafficSoapService();
        ServiceFluxVehicules port = service.getPort(ServiceFluxVehicules.class);
        if (port instanceof BindingProvider bindingProvider) {
            bindingProvider.getRequestContext().put("jakarta.xml.ws.client.connectionTimeout", CONNECT_TIMEOUT_MS);
            bindingProvider.getRequestContext().put("jakarta.xml.ws.client.receiveTimeout", READ_TIMEOUT_MS);
            bindingProvider.getRequestContext().put("com.sun.xml.ws.connect.timeout", CONNECT_TIMEOUT_MS);
            bindingProvider.getRequestContext().put("com.sun.xml.ws.request.timeout", READ_TIMEOUT_MS);
        }
        port.submitTrafficEvent(event);
    }

    private static void dispatchPollution(String payload) throws Exception {
        URL url = new URL(AppConfig.pollutionRestBaseUrl() + "api/pollution/events");
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setRequestProperty("Content-Type", "application/json");
        try (OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        connection.getResponseCode();
        connection.disconnect();
    }

    private static void dispatchNoise(String payload) throws Exception {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(AppConfig.noiseServiceHost(), AppConfig.noiseServicePort()), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);
            try (OutputStream outputStream = socket.getOutputStream()) {
            outputStream.write((payload + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            }
        }
    }

    private static void dispatchCamera(SensorEvent event) throws Exception {
        cameraRemoteService().submitAccidentEvent(event);
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

    private static Properties producerProps() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, AppConfig.kafkaBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        return props;
    }

    private static Service trafficSoapService() throws Exception {
        if (trafficSoapService == null) {
            synchronized (TcpCollectorMain.class) {
                if (trafficSoapService == null) {
                    URL wsdlUrl = new URL(AppConfig.trafficWsEndpoint() + "?wsdl");
                    QName serviceName = new QName(TRAFFIC_NS, TRAFFIC_SERVICE_NAME);
                    trafficSoapService = Service.create(wsdlUrl, serviceName);
                }
            }
        }
        return trafficSoapService;
    }

    private static CameraRemoteService cameraRemoteService() throws Exception {
        if (cameraRemoteService == null) {
            synchronized (TcpCollectorMain.class) {
                if (cameraRemoteService == null) {
                    String url = "rmi://" + AppConfig.cameraRmiHost() + ":" + AppConfig.cameraRmiPort()
                            + "/" + AppConfig.cameraRmiServiceName();
                    cameraRemoteService = (CameraRemoteService) Naming.lookup(url);
                }
            }
        }
        return cameraRemoteService;
    }
}
