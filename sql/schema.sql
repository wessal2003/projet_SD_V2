CREATE DATABASE IF NOT EXISTS smart_traffic;
USE smart_traffic;

CREATE TABLE IF NOT EXISTS sensor_measurements (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    zone_id VARCHAR(64) NOT NULL,
    intersection_id VARCHAR(64) NOT NULL,
    sensor_type VARCHAR(32) NOT NULL,
    vehicle_count INT DEFAULT 0,
    avg_speed_kmh DOUBLE DEFAULT 0,
    pm25 DOUBLE DEFAULT 0,
    noise_db DOUBLE DEFAULT 0,
    accident_detected BOOLEAN DEFAULT FALSE,
    event_time TIMESTAMP NOT NULL,
    INDEX idx_event_time (event_time),
    INDEX idx_zone_intersection (zone_id, intersection_id)
);

CREATE TABLE IF NOT EXISTS alerts (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    alert_type VARCHAR(32) NOT NULL,
    zone_id VARCHAR(64) NOT NULL,
    intersection_id VARCHAR(64) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    INDEX idx_alert_type (alert_type),
    INDEX idx_alert_created (created_at)
);

CREATE TABLE IF NOT EXISTS recommendations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    alert_id BIGINT NULL,
    zone_id VARCHAR(64) NOT NULL,
    intersection_id VARCHAR(64) NOT NULL,
    recommendation VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_recommendation_alert
        FOREIGN KEY (alert_id)
        REFERENCES alerts(id)
        ON DELETE SET NULL,
    INDEX idx_rec_created (created_at)
);

CREATE TABLE IF NOT EXISTS traffic_light_state (
    intersection_id VARCHAR(64) PRIMARY KEY,
    green_duration_seconds INT NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    INDEX idx_light_updated (updated_at)
);
