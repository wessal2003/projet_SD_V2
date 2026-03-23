package com.smartcity.common.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

public final class AppConfig {
    private static final Properties FILE_PROPS = loadProperties();

    private AppConfig() {
    }

    public static String kafkaBootstrapServers() {
        return stringValue("kafka.bootstrap", "SMART_TRAFFIC_KAFKA_BOOTSTRAP", "localhost:9092");
    }

    public static String mysqlUrl() {
        return stringValue("mysql.url", "SMART_TRAFFIC_DB_URL",
                "jdbc:mysql://localhost:3306/smart_traffic?serverTimezone=UTC");
    }

    public static String mysqlUser() {
        return stringValue("mysql.user", "SMART_TRAFFIC_DB_USER", "root");
    }

    public static String mysqlPassword() {
        return stringValue("mysql.password", "SMART_TRAFFIC_DB_PASSWORD", "");
    }

    public static String collectorHost() {
        return stringValue("collector.host", "SMART_TRAFFIC_COLLECTOR_HOST", "localhost");
    }

    public static int collectorPort() {
        return intValue("collector.port", "SMART_TRAFFIC_COLLECTOR_PORT", 7070);
    }

    public static String rmiHost() {
        return stringValue("rmi.host", "SMART_TRAFFIC_RMI_HOST", "localhost");
    }

    public static int rmiPort() {
        return intValue("rmi.port", "SMART_TRAFFIC_RMI_PORT", 1099);
    }

    public static String rmiServiceName() {
        return stringValue("rmi.service", "SMART_TRAFFIC_RMI_SERVICE", "TrafficLightService");
    }

    public static String soapEndpoint() {
        return stringValue("soap.endpoint", "SMART_TRAFFIC_SOAP_ENDPOINT",
                "http://localhost:8083/ws/CityNotificationSoapService");
    }

    public static String trafficWsEndpoint() {
        return stringValue("traffic.ws.endpoint", "SMART_TRAFFIC_TRAFFIC_WS_ENDPOINT",
                "http://localhost:8085/ws/ServiceFluxVehicules");
    }

    public static String pollutionRestBaseUrl() {
        return stringValue("pollution.rest.base", "SMART_TRAFFIC_POLLUTION_REST_BASE",
                "http://localhost:8082/");
    }

    public static String cameraRmiHost() {
        return stringValue("camera.rmi.host", "SMART_TRAFFIC_CAMERA_RMI_HOST", "localhost");
    }

    public static int cameraRmiPort() {
        return intValue("camera.rmi.port", "SMART_TRAFFIC_CAMERA_RMI_PORT", 1099);
    }

    public static String cameraRmiServiceName() {
        return stringValue("camera.rmi.service", "SMART_TRAFFIC_CAMERA_RMI_SERVICE", "ServiceCamera");
    }

    public static String noiseServiceHost() {
        return stringValue("noise.service.host", "SMART_TRAFFIC_NOISE_SERVICE_HOST", "localhost");
    }

    public static int noiseServicePort() {
        return intValue("noise.service.port", "SMART_TRAFFIC_NOISE_SERVICE_PORT", 7071);
    }

    public static String trafficLightJaxRpcEndpoint() {
        return stringValue("traffic.light.jaxrpc.endpoint", "SMART_TRAFFIC_TRAFFIC_LIGHT_JAXRPC_ENDPOINT",
                "http://localhost:8084/axis/services/ServiceFeuxSignalisation");
    }

    public static String restBaseUrl() {
        return stringValue("rest.base", "SMART_TRAFFIC_DASHBOARD_BASE", "http://localhost:8080/");
    }

    public static String operationsBaseUrl() {
        return stringValue("operations.base", "SMART_TRAFFIC_OPERATIONS_BASE", "http://localhost:8081/");
    }

    private static String stringValue(String propertyKey, String envKey, String defaultValue) {
        String systemValue = System.getProperty(propertyKey);
        if (systemValue != null && !systemValue.isBlank()) {
            return systemValue;
        }

        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isBlank()) {
            return envValue;
        }

        String fileValue = FILE_PROPS.getProperty(propertyKey);
        if (fileValue != null && !fileValue.isBlank()) {
            return fileValue;
        }

        String envStyleFileValue = FILE_PROPS.getProperty(envKey);
        if (envStyleFileValue != null && !envStyleFileValue.isBlank()) {
            return envStyleFileValue;
        }

        return defaultValue;
    }

    private static int intValue(String propertyKey, String envKey, int defaultValue) {
        return Integer.parseInt(stringValue(propertyKey, envKey, String.valueOf(defaultValue)));
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        for (Path path : candidatePaths()) {
            if (!Files.exists(path)) {
                continue;
            }
            try {
                if (isDotEnv(path)) {
                    loadDotEnv(path, properties);
                } else {
                    try (InputStream inputStream = Files.newInputStream(path)) {
                        properties.load(inputStream);
                    }
                }
                break;
            } catch (IOException ignored) {
                // Fall back to next candidate.
            }
        }
        return properties;
    }

    private static List<Path> candidatePaths() {
        return List.of(
                Path.of("smarttraffic.properties"),
                Path.of(".env.properties"),
                Path.of(".env"),
                Path.of("config", ".env"),
                Path.of("config", "smarttraffic.properties"));
    }

    private static boolean isDotEnv(Path path) {
        return path.getFileName().toString().equalsIgnoreCase(".env");
    }

    private static void loadDotEnv(Path path, Properties properties) throws IOException {
        for (String rawLine : Files.readAllLines(path)) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int separatorIndex = line.indexOf('=');
            if (separatorIndex <= 0) {
                continue;
            }
            String key = line.substring(0, separatorIndex).trim();
            String value = line.substring(separatorIndex + 1).trim();
            if ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'"))) {
                value = value.substring(1, value.length() - 1);
            }
            properties.setProperty(key, value);
        }
    }
}
