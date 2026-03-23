package com.smartcity.operations;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.model.AlertRecord;
import com.smartcity.common.model.AlertType;
import com.smartcity.common.model.RecommendationRecord;
import com.smartcity.common.model.SoapNotificationRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class OperationsRepository {
    private static final Logger LOG = LoggerFactory.getLogger(OperationsRepository.class);

    public List<AlertRecord> loadActiveAlerts(int limit, int minutes) {
        String sql = """
                SELECT id, alert_type, zone_id, intersection_id, message, created_at
                FROM alerts
                WHERE created_at >= (CURRENT_TIMESTAMP - INTERVAL ? MINUTE)
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<AlertRecord> records = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, minutes);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AlertRecord record = new AlertRecord();
                    record.setId(rs.getLong("id"));
                    record.setAlertType(AlertType.valueOf(rs.getString("alert_type")));
                    record.setZoneId(rs.getString("zone_id"));
                    record.setIntersectionId(rs.getString("intersection_id"));
                    record.setMessage(rs.getString("message"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    record.setTimestamp(createdAt == null ? 0L : createdAt.getTime());
                    records.add(record);
                }
            }
        } catch (Exception ex) {
            LOG.warn("loadActiveAlerts failed: {}", ex.getMessage());
        }
        return records;
    }

    public List<RecommendationRecord> loadLatestRecommendations(int limit) {
        String sql = """
                SELECT id, zone_id, intersection_id, recommendation, created_at
                FROM recommendations
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<RecommendationRecord> records = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    RecommendationRecord record = new RecommendationRecord();
                    record.setId(rs.getLong("id"));
                    record.setZoneId(rs.getString("zone_id"));
                    record.setIntersectionId(rs.getString("intersection_id"));
                    record.setRecommendation(rs.getString("recommendation"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    record.setTimestamp(createdAt == null ? 0L : createdAt.getTime());
                    records.add(record);
                }
            }
        } catch (Exception ex) {
            LOG.warn("loadLatestRecommendations failed: {}", ex.getMessage());
        }
        return records;
    }

    public List<Map<String, Object>> loadIntersectionStatus() {
        String sql = """
                SELECT base.intersection_id,
                       (
                           SELECT m.vehicle_count
                           FROM sensor_measurements m
                           WHERE m.intersection_id = base.intersection_id
                             AND m.sensor_type = 'TRAFFIC_FLOW'
                           ORDER BY m.id DESC LIMIT 1
                       ) AS vehicle_count,
                       (
                           SELECT m.avg_speed_kmh
                           FROM sensor_measurements m
                           WHERE m.intersection_id = base.intersection_id
                             AND m.sensor_type = 'TRAFFIC_FLOW'
                           ORDER BY m.id DESC LIMIT 1
                       ) AS avg_speed_kmh,
                       (
                           SELECT m.pm25
                           FROM sensor_measurements m
                           WHERE m.intersection_id = base.intersection_id
                             AND m.sensor_type = 'POLLUTION'
                           ORDER BY m.id DESC LIMIT 1
                       ) AS pm25,
                       (
                           SELECT m.noise_db
                           FROM sensor_measurements m
                           WHERE m.intersection_id = base.intersection_id
                             AND m.sensor_type = 'NOISE'
                           ORDER BY m.id DESC LIMIT 1
                       ) AS noise_db,
                       (
                           SELECT m.accident_detected
                           FROM sensor_measurements m
                           WHERE m.intersection_id = base.intersection_id
                             AND m.sensor_type = 'ACCIDENT_CAMERA'
                           ORDER BY m.id DESC LIMIT 1
                       ) AS accident_detected
                FROM (
                    SELECT DISTINCT intersection_id
                    FROM sensor_measurements
                ) base
                ORDER BY base.intersection_id
                """;
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("intersectionId", rs.getString("intersection_id"));
                row.put("vehicleCount", rs.getInt("vehicle_count"));
                row.put("averageSpeedKmh", rs.getDouble("avg_speed_kmh"));
                row.put("pm25", rs.getDouble("pm25"));
                row.put("noiseDb", rs.getDouble("noise_db"));
                row.put("accidentDetected", rs.getBoolean("accident_detected"));
                rows.add(row);
            }
        } catch (Exception ex) {
            LOG.warn("loadIntersectionStatus failed: {}", ex.getMessage());
        }
        return rows;
    }

    public List<Map<String, Object>> loadTrafficLights() {
        String sql = """
                SELECT intersection_id, green_duration_seconds, updated_at
                FROM traffic_light_state
                ORDER BY intersection_id
                """;
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("intersectionId", rs.getString("intersection_id"));
                row.put("greenDurationSeconds", rs.getInt("green_duration_seconds"));
                Timestamp updatedAt = rs.getTimestamp("updated_at");
                row.put("updatedAt", updatedAt == null ? 0L : updatedAt.getTime());
                rows.add(row);
            }
        } catch (Exception ex) {
            LOG.warn("loadTrafficLights failed: {}", ex.getMessage());
        }
        return rows;
    }

    public List<SoapNotificationRecord> loadSoapNotifications(int limit) {
        String sql = """
                SELECT id, alert_type, zone_id, message, transport, status, created_at
                FROM soap_notifications
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<SoapNotificationRecord> records = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    SoapNotificationRecord record = new SoapNotificationRecord();
                    record.setId(rs.getLong("id"));
                    record.setAlertType(rs.getString("alert_type"));
                    record.setZoneId(rs.getString("zone_id"));
                    record.setMessage(rs.getString("message"));
                    record.setTransport(rs.getString("transport"));
                    record.setStatus(rs.getString("status"));
                    Timestamp createdAt = rs.getTimestamp("created_at");
                    record.setTimestamp(createdAt == null ? 0L : createdAt.getTime());
                    records.add(record);
                }
            }
        } catch (Exception ex) {
            LOG.warn("loadSoapNotifications failed: {}", ex.getMessage());
        }
        return records;
    }

    public Map<String, Object> loadPipelineStats() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("recentTrafficEvents", countMeasurementsByType("TRAFFIC_FLOW", 10));
        result.put("recentPollutionEvents", countMeasurementsByType("POLLUTION", 10));
        result.put("recentNoiseEvents", countMeasurementsByType("NOISE", 10));
        result.put("recentAccidentEvents", countMeasurementsByType("ACCIDENT_CAMERA", 10));
        result.put("lastMeasurementId", lastMeasurementId());
        return result;
    }

    private long countMeasurementsByType(String sensorType, int minutes) {
        String sql = """
                SELECT COUNT(*)
                FROM sensor_measurements
                WHERE sensor_type = ?
                  AND event_time >= (CURRENT_TIMESTAMP - INTERVAL ? MINUTE)
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sensorType);
            ps.setInt(2, minutes);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception ex) {
            LOG.warn("countMeasurementsByType failed: {}", ex.getMessage());
        }
        return 0L;
    }

    private long lastMeasurementId() {
        String sql = "SELECT MAX(id) FROM sensor_measurements";
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception ex) {
            LOG.warn("lastMeasurementId failed: {}", ex.getMessage());
        }
        return 0L;
    }

    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(AppConfig.mysqlUrl(), AppConfig.mysqlUser(), AppConfig.mysqlPassword());
    }
}
