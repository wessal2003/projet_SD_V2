package com.smartcity.simulator;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.json.JsonUtils;
import com.smartcity.common.model.SensorEvent;
import com.smartcity.common.model.SensorType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SensorSimulatorMain {
    private static final Logger LOG = LoggerFactory.getLogger(SensorSimulatorMain.class);
    private static final String DEFAULT_PROFILE = "realistic";

    public static void main(String[] args) {
        String collectorHost = AppConfig.collectorHost();
        int collectorPort = AppConfig.collectorPort();
        String zoneId = System.getProperty("zone.id", "zone-centre-ville");
        String profile = System.getProperty("simulator.profile", DEFAULT_PROFILE);
        List<String> intersections = List.of("I-101", "I-102", "I-103", "I-104");

        LOG.info("Starting sensor simulator -> {}:{} profile={}", collectorHost, collectorPort, profile);
        ExecutorService executor = Executors.newFixedThreadPool(4);

        executor.submit(new SensorWorker(collectorHost, collectorPort, zoneId, intersections, SensorType.TRAFFIC_FLOW, 1200, profile));
        executor.submit(new SensorWorker(collectorHost, collectorPort, zoneId, intersections, SensorType.POLLUTION, 1800, profile));
        executor.submit(new SensorWorker(collectorHost, collectorPort, zoneId, intersections, SensorType.NOISE, 1500, profile));
        executor.submit(new SensorWorker(collectorHost, collectorPort, zoneId, intersections, SensorType.ACCIDENT_CAMERA, 2200, profile));
    }

    private static class SensorWorker implements Runnable {
        private final String host;
        private final int port;
        private final String zoneId;
        private final List<String> intersections;
        private final SensorType sensorType;
        private final long sleepMs;
        private final String profile;
        private final Random random = new Random();
        private int index;

        private SensorWorker(String host, int port, String zoneId, List<String> intersections, SensorType sensorType,
                             long sleepMs, String profile) {
            this.host = host;
            this.port = port;
            this.zoneId = zoneId;
            this.intersections = intersections;
            this.sensorType = sensorType;
            this.sleepMs = sleepMs;
            this.profile = profile;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    SensorEvent event = buildEvent();
                    String payload = JsonUtils.MAPPER.writeValueAsString(event);
                    try (Socket socket = new Socket(host, port);
                         PrintWriter writer = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {
                        writer.println(payload);
                    }
                    LOG.info("TCP_SEND sensor={} intersection={} profile={}",
                            sensorType, event.getIntersectionId(), profile);
                } catch (Exception ex) {
                    LOG.warn("Failed to send {} event: {}", sensorType, ex.getMessage());
                }

                try {
                    Thread.sleep(sleepMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        private SensorEvent buildEvent() {
            SensorEvent event = new SensorEvent();
            event.setZoneId(zoneId);
            event.setIntersectionId(intersections.get(index++ % intersections.size()));
            event.setSensorType(sensorType);
            event.setTimestamp(System.currentTimeMillis());

            switch (sensorType) {
                case TRAFFIC_FLOW -> {
                    int peakBonus = isPeakHour() ? 35 : 0;
                    int profileBonus = isDemoProfile() ? 25 : 0;
                    int vehicleCount = 25 + peakBonus + profileBonus + random.nextInt(95);
                    double speed = Math.max(8.0, 70.0 - (vehicleCount * 0.45) + random.nextDouble(8));
                    event.setVehicleCount(vehicleCount);
                    event.setAverageSpeedKmh(round(speed));
                }
                case POLLUTION -> {
                    double pm25 = (isDemoProfile() ? 45 : 22) + random.nextDouble(isDemoProfile() ? 110 : 95);
                    event.setPm25(round(pm25));
                }
                case NOISE -> {
                    double noise = (isDemoProfile() ? 60 : 48) + random.nextDouble(isDemoProfile() ? 42 : 48);
                    event.setNoiseDb(round(noise));
                }
                case ACCIDENT_CAMERA -> {
                    double accidentProbability = switch (profile.toLowerCase()) {
                        case "demo", "accident-demo" -> 0.18;
                        case "stress" -> 0.12;
                        default -> 0.03;
                    };
                    event.setAccidentDetected(random.nextDouble() < accidentProbability);
                }
            }
            return event;
        }

        private boolean isDemoProfile() {
            return "demo".equalsIgnoreCase(profile) || "stress".equalsIgnoreCase(profile);
        }

        private boolean isPeakHour() {
            LocalTime now = LocalTime.now();
            return (now.getHour() >= 7 && now.getHour() <= 9) || (now.getHour() >= 17 && now.getHour() <= 19);
        }

        private double round(double value) {
            return Math.round(value * 10.0) / 10.0;
        }
    }
}
