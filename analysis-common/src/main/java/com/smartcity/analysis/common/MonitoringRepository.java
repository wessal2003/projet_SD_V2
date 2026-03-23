package com.smartcity.analysis.common;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.model.AlertType;
import com.smartcity.common.model.SensorEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;

public class MonitoringRepository {
    private static final Logger LOG = LoggerFactory.getLogger(MonitoringRepository.class);

    public void insertMeasurement(SensorEvent event) {
        String sql = """
                INSERT INTO sensor_measurements
                (zone_id, intersection_id, sensor_type, vehicle_count, avg_speed_kmh, pm25, noise_db, accident_detected, event_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, event.getZoneId());
            ps.setString(2, event.getIntersectionId());
            ps.setString(3, event.getSensorType() == null ? null : event.getSensorType().name());
            ps.setInt(4, event.getVehicleCount());
            ps.setDouble(5, event.getAverageSpeedKmh());
            ps.setDouble(6, event.getPm25());
            ps.setDouble(7, event.getNoiseDb());
            ps.setBoolean(8, event.isAccidentDetected());
            ps.setTimestamp(9, new Timestamp(event.getTimestamp()));
            ps.executeUpdate();
        } catch (Exception ex) {
            LOG.error("insertMeasurement failed: {}", ex.getMessage());
        }
    }

    public long insertAlert(AlertType alertType, SensorEvent event, String message) {
        String sql = """
                INSERT INTO alerts
                (alert_type, zone_id, intersection_id, message, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, alertType.name());
            ps.setString(2, event.getZoneId());
            ps.setString(3, event.getIntersectionId());
            ps.setString(4, message);
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception ex) {
            LOG.error("insertAlert failed: {}", ex.getMessage());
        }
        return -1L;
    }

    public void insertRecommendation(long alertId, SensorEvent event, String recommendation) {
        String sql = """
                INSERT INTO recommendations
                (alert_id, zone_id, intersection_id, recommendation, created_at)
                VALUES (?, ?, ?, ?, ?)
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            if (alertId > 0) {
                ps.setLong(1, alertId);
            } else {
                ps.setNull(1, java.sql.Types.BIGINT);
            }
            ps.setString(2, event.getZoneId());
            ps.setString(3, event.getIntersectionId());
            ps.setString(4, recommendation);
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (Exception ex) {
            LOG.error("insertRecommendation failed: {}", ex.getMessage());
        }
    }

    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(AppConfig.mysqlUrl(), AppConfig.mysqlUser(), AppConfig.mysqlPassword());
    }
}
