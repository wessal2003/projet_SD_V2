package com.smartcity.common.config;

public final class Thresholds {
    private Thresholds() {
    }

    // Values are intended for simulated urban data (student-level project defaults).
    public static final int CONGESTION_VEHICLE_THRESHOLD = Integer.parseInt(
            System.getProperty("threshold.congestion.vehicles", "85"));
    public static final double CONGESTION_SPEED_THRESHOLD = Double.parseDouble(
            System.getProperty("threshold.congestion.speed", "20.0"));
    public static final double POLLUTION_PM25_THRESHOLD = Double.parseDouble(
            System.getProperty("threshold.pollution.pm25", "75.0"));
    public static final double NOISE_DB_THRESHOLD = Double.parseDouble(
            System.getProperty("threshold.noise.db", "85.0"));
}
