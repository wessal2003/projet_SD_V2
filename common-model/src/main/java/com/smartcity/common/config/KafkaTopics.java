package com.smartcity.common.config;

public final class KafkaTopics {
    private KafkaTopics() {
    }

    public static final String TRAFFIC_FLOW = "traffic.flow";
    public static final String ENV_POLLUTION = "environment.pollution";
    public static final String ENV_NOISE = "environment.noise";
    public static final String INCIDENT_ACCIDENT = "incident.accident";
    public static final String CITY_ALERTS = "city.alerts";
    public static final String CITY_RECOMMENDATIONS = "city.recommendations";
}
