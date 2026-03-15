package com.smartcity.common.model;

public class TrafficLightState {
    private String intersectionId;
    private String currentPhase;
    private int greenDurationSeconds;
    private long updatedAt;

    public TrafficLightState() {
    }

    public String getIntersectionId() {
        return intersectionId;
    }

    public void setIntersectionId(String intersectionId) {
        this.intersectionId = intersectionId;
    }

    public String getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(String currentPhase) {
        this.currentPhase = currentPhase;
    }

    public int getGreenDurationSeconds() {
        return greenDurationSeconds;
    }

    public void setGreenDurationSeconds(int greenDurationSeconds) {
        this.greenDurationSeconds = greenDurationSeconds;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
