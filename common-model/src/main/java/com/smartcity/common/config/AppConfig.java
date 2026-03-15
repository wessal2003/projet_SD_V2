package com.smartcity.common.config;

public final class AppConfig {
    private AppConfig() {
    }

    public static String kafkaBootstrapServers() {
        return System.getProperty("kafka.bootstrap", "localhost:9092");
    }

    public static String mysqlUrl() {
        return System.getProperty("mysql.url", "jdbc:mysql://localhost:3306/smart_traffic?serverTimezone=UTC");
    }

    public static String mysqlUser() {
        return System.getProperty("mysql.user", "root");
    }

    public static String mysqlPassword() {
        return System.getProperty("mysql.password", "Wessal*2003");
    }

    public static String collectorHost() {
        return System.getProperty("collector.host", "localhost");
    }

    public static int collectorPort() {
        return Integer.parseInt(System.getProperty("collector.port", "7070"));
    }

    public static String rmiHost() {
        return System.getProperty("rmi.host", "localhost");
    }

    public static int rmiPort() {
        return Integer.parseInt(System.getProperty("rmi.port", "1099"));
    }

    public static String rmiServiceName() {
        return System.getProperty("rmi.service", "TrafficLightService");
    }

    public static String soapEndpoint() {
        return System.getProperty("soap.endpoint", "http://localhost:8083/ws/CityNotificationSoapService");
    }

    public static String restBaseUrl() {
        return System.getProperty("rest.base", "http://localhost:8080/");
    }
}
