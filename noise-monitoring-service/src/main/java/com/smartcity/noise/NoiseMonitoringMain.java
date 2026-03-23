package com.smartcity.noise;

import com.smartcity.analysis.common.DetectionResult;
import com.smartcity.analysis.common.SensorEventProcessor;
import com.smartcity.common.config.AppConfig;
import com.smartcity.common.config.Thresholds;
import com.smartcity.common.json.JsonUtils;
import com.smartcity.common.model.AlertType;
import com.smartcity.common.model.SensorEvent;
import com.smartcity.common.model.SensorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NoiseMonitoringMain {
    private static final Logger LOG = LoggerFactory.getLogger(NoiseMonitoringMain.class);
    private static final SensorEventProcessor PROCESSOR = new SensorEventProcessor("ServiceBruit");

    public static void main(String[] args) throws Exception {
        int port = AppConfig.noiseServicePort();
        ExecutorService pool = Executors.newFixedThreadPool(6);

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            LOG.info("ServiceBruit Socket TCP listening on {}", port);
            while (true) {
                Socket client = serverSocket.accept();
                pool.submit(() -> handleClient(client));
            }
        }
    }

    private static void handleClient(Socket client) {
        try (client;
             BufferedReader reader = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                SensorEvent event = JsonUtils.MAPPER.readValue(line, SensorEvent.class);
                PROCESSOR.process(event, NoiseMonitoringMain::detectNoise);
            }
        } catch (Exception ex) {
            LOG.warn("ServiceBruit socket error: {}", ex.getMessage());
        }
    }

    private static Optional<DetectionResult> detectNoise(SensorEvent event) {
        if (event.getSensorType() == SensorType.NOISE
                && event.getNoiseDb() > Thresholds.NOISE_DB_THRESHOLD) {
            return Optional.of(new DetectionResult(
                    AlertType.HIGH_NOISE,
                    "Niveau de bruit eleve (" + event.getNoiseDb() + " dB) a " + event.getIntersectionId(),
                    "Lancer une alerte locale et adapter le plan de circulation."));
        }
        return Optional.empty();
    }
}
