package com.smartcity.common.model;

import java.io.Serial;
import java.io.Serializable;

public class SensorEvent implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private String zoneId;
    private String intersectionId;
    private SensorType sensorType;
    private int vehicleCount;
    private double averageSpeedKmh;
    private double pm25;
    private double noiseDb;
    private boolean accidentDetected;
    private long timestamp;

    public SensorEvent() {
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getIntersectionId() {
        return intersectionId;
    }

    public void setIntersectionId(String intersectionId) {
        this.intersectionId = intersectionId;
    }

    public SensorType getSensorType() {
        return sensorType;
    }

    public void setSensorType(SensorType sensorType) {
        this.sensorType = sensorType;
    }

    public int getVehicleCount() {
        return vehicleCount;
    }

    public void setVehicleCount(int vehicleCount) {
        this.vehicleCount = vehicleCount;
    }

    public double getAverageSpeedKmh() {
        return averageSpeedKmh;
    }

    public void setAverageSpeedKmh(double averageSpeedKmh) {
        this.averageSpeedKmh = averageSpeedKmh;
    }

    public double getPm25() {
        return pm25;
    }

    public void setPm25(double pm25) {
        this.pm25 = pm25;
    }

    public double getNoiseDb() {
        return noiseDb;
    }

    public void setNoiseDb(double noiseDb) {
        this.noiseDb = noiseDb;
    }

    public boolean isAccidentDetected() {
        return accidentDetected;
    }

    public void setAccidentDetected(boolean accidentDetected) {
        this.accidentDetected = accidentDetected;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
