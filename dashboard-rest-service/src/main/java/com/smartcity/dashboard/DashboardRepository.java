package com.smartcity.dashboard;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.model.AlertRecord;
import com.smartcity.common.model.AlertType;
import com.smartcity.common.model.DashboardOverview;
import com.smartcity.common.model.ExecutedActionRecord;
import com.smartcity.common.model.RecommendationRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class DashboardRepository {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardRepository.class);
    private static final Map<String, String> ROUTE_NAMES = Map.of(
            "I-101", "Avenue Mohammed V",
            "I-102", "Boulevard Hassan II",
            "I-103", "Avenue Allal Ben Abdellah",
            "I-104", "Avenue Moulay Hassan");
    private static final Map<String, double[]> COORDINATES = Map.of(
            "I-101", new double[]{34.0189, -6.8368},
            "I-102", new double[]{34.0216, -6.8424},
            "I-103", new double[]{34.0234, -6.8462},
            "I-104", new double[]{34.0197, -6.8498});
    private static volatile boolean trafficLightSchemaChecked;

    public DashboardOverview loadOverview() {
        DashboardOverview overview = new DashboardOverview();
        overview.setTotalEvents(count("sensor_measurements"));
        overview.setTotalAlerts(count("alerts"));
        overview.setTotalRecommendations(count("recommendations"));
        overview.setTotalAccidents(countByType(AlertType.ACCIDENT.name()));
        return overview;
    }

    public List<AlertRecord> loadAlerts(int limit) {
        String sql = """
                SELECT id, alert_type, zone_id, intersection_id, message, created_at
                FROM alerts
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<AlertRecord> records = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AlertRecord record = new AlertRecord();
                    record.setId(rs.getLong("id"));
                    record.setAlertType(AlertType.valueOf(rs.getString("alert_type")));
                    record.setZoneId(rs.getString("zone_id"));
                    record.setIntersectionId(rs.getString("intersection_id"));
                    record.setMessage(rs.getString("message"));
                    Timestamp created = rs.getTimestamp("created_at");
                    record.setTimestamp(created == null ? 0L : created.getTime());
                    records.add(record);
                }
            }
        } catch (Exception ex) {
            LOG.error("loadAlerts failed: {}", ex.getMessage());
        }
        return records;
    }

    public List<RecommendationRecord> loadRecommendations(int limit) {
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
                    Timestamp created = rs.getTimestamp("created_at");
                    record.setTimestamp(created == null ? 0L : created.getTime());
                    records.add(record);
                }
            }
        } catch (Exception ex) {
            LOG.error("loadRecommendations failed: {}", ex.getMessage());
        }
        return records;
    }

    public List<Map<String, Object>> loadTrafficLights() {
        ensureTrafficLightSchema();
        String sql = """
                SELECT intersection_id, green_duration_seconds, road_blocked, status_label, updated_at
                FROM traffic_light_state
                ORDER BY updated_at DESC
                """;
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = getConnection();
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                String intersectionId = rs.getString("intersection_id");
                row.put("intersectionId", intersectionId);
                row.put("routeName", routeName(intersectionId));
                row.put("greenDurationSeconds", rs.getInt("green_duration_seconds"));
                row.put("roadBlocked", rs.getBoolean("road_blocked"));
                row.put("statusLabel", rs.getString("status_label"));
                Timestamp updated = rs.getTimestamp("updated_at");
                row.put("updatedAt", updated == null ? 0L : updated.getTime());
                double[] coordinates = coordinates(intersectionId);
                row.put("lat", coordinates[0]);
                row.put("lng", coordinates[1]);
                rows.add(row);
            }
        } catch (Exception ex) {
            LOG.warn("loadTrafficLights failed (table may be empty): {}", ex.getMessage());
        }
        return rows;
    }

    public Map<String, Object> loadLiveMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        metrics.put("flux", averageRecent("TRAFFIC_FLOW", "vehicle_count", 8));
        metrics.put("pollution", averageRecent("POLLUTION", "pm25", 8));
        metrics.put("noise", averageRecent("NOISE", "noise_db", 8));
        metrics.put("activeAlerts", countRecentAlerts(10));
        metrics.put("lastMeasurementId", lastMeasurementId());
        return metrics;
    }

    public List<Map<String, Object>> loadIntersectionSnapshots() {
        ensureTrafficLightSchema();
        String sql = """
                SELECT base.intersection_id,
                       (
                           SELECT m.vehicle_count
                           FROM sensor_measurements m
                           WHERE m.intersection_id = base.intersection_id
                             AND m.sensor_type = 'TRAFFIC_FLOW'
                           ORDER BY m.id DESC
                           LIMIT 1
                       ) AS vehicle_count,
                       (
                           SELECT m.pm25
                           FROM sensor_measurements m
                           WHERE m.intersection_id = base.intersection_id
                             AND m.sensor_type = 'POLLUTION'
                           ORDER BY m.id DESC
                           LIMIT 1
                       ) AS pm25,
                       (
                           SELECT m.noise_db
                           FROM sensor_measurements m
                           WHERE m.intersection_id = base.intersection_id
                             AND m.sensor_type = 'NOISE'
                           ORDER BY m.id DESC
                           LIMIT 1
                       ) AS noise_db,
                       (
                           SELECT m.accident_detected
                           FROM sensor_measurements m
                           WHERE m.intersection_id = base.intersection_id
                             AND m.sensor_type = 'ACCIDENT_CAMERA'
                           ORDER BY m.id DESC
                           LIMIT 1
                       ) AS accident_detected,
                       (
                           SELECT tls.green_duration_seconds
                           FROM traffic_light_state tls
                           WHERE tls.intersection_id = base.intersection_id
                           ORDER BY tls.updated_at DESC
                           LIMIT 1
                       ) AS green_duration_seconds,
                       (
                           SELECT tls.road_blocked
                           FROM traffic_light_state tls
                           WHERE tls.intersection_id = base.intersection_id
                           ORDER BY tls.updated_at DESC
                           LIMIT 1
                       ) AS road_blocked,
                       (
                           SELECT tls.status_label
                           FROM traffic_light_state tls
                           WHERE tls.intersection_id = base.intersection_id
                           ORDER BY tls.updated_at DESC
                           LIMIT 1
                       ) AS status_label,
                       (
                           SELECT COUNT(*)
                           FROM alerts a
                           WHERE a.intersection_id = base.intersection_id
                             AND a.created_at >= (CURRENT_TIMESTAMP - INTERVAL 10 MINUTE)
                       ) AS recent_alerts,
                       (
                           SELECT COUNT(*)
                           FROM recommendations r
                           WHERE r.intersection_id = base.intersection_id
                             AND r.created_at >= (CURRENT_TIMESTAMP - INTERVAL 10 MINUTE)
                       ) AS recent_recommendations
                FROM (
                    SELECT DISTINCT intersection_id
                    FROM sensor_measurements
                    ORDER BY intersection_id
                    LIMIT 8
                ) base
                ORDER BY base.intersection_id
                """;

        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = getConnection();
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                String intersectionId = rs.getString("intersection_id");
                double[] coordinates = coordinates(intersectionId);
                row.put("intersectionId", intersectionId);
                row.put("routeName", routeName(intersectionId));
                row.put("lat", coordinates[0]);
                row.put("lng", coordinates[1]);
                row.put("vehicleCount", rs.getInt("vehicle_count"));
                row.put("pm25", rs.getDouble("pm25"));
                row.put("noiseDb", rs.getDouble("noise_db"));
                row.put("accidentDetected", rs.getBoolean("accident_detected"));
                row.put("greenDurationSeconds", rs.getInt("green_duration_seconds"));
                row.put("roadBlocked", rs.getBoolean("road_blocked"));
                row.put("statusLabel", rs.getString("status_label"));
                row.put("recentAlerts", rs.getInt("recent_alerts"));
                row.put("recentRecommendations", rs.getInt("recent_recommendations"));
                rows.add(row);
            }
        } catch (Exception ex) {
            LOG.warn("loadIntersectionSnapshots failed: {}", ex.getMessage());
        }
        return rows;
    }

    public List<Map<String, Object>> loadCongestedRoutes() {
        String sql = """
                SELECT a.intersection_id, MAX(a.created_at) AS last_alert,
                       MAX(a.message) AS message,
                       (
                           SELECT m.vehicle_count FROM sensor_measurements m
                           WHERE m.intersection_id = a.intersection_id
                             AND m.sensor_type = 'TRAFFIC_FLOW'
                           ORDER BY m.id DESC LIMIT 1
                       ) AS vehicle_count
                FROM alerts a
                WHERE a.alert_type IN ('CONGESTION', 'PEAK_HOUR')
                  AND a.created_at >= (CURRENT_TIMESTAMP - INTERVAL 20 MINUTE)
                GROUP BY a.intersection_id
                ORDER BY last_alert DESC
                """;
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = getConnection();
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                String intersectionId = rs.getString("intersection_id");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("intersectionId", intersectionId);
                row.put("routeName", routeName(intersectionId));
                row.put("vehicleCount", rs.getInt("vehicle_count"));
                row.put("message", rs.getString("message"));
                Timestamp lastAlert = rs.getTimestamp("last_alert");
                row.put("timestamp", lastAlert == null ? 0L : lastAlert.getTime());
                rows.add(row);
            }
        } catch (Exception ex) {
            LOG.warn("loadCongestedRoutes failed: {}", ex.getMessage());
        }
        return rows;
    }

    public Map<String, Object> loadZoneStats() {
        ensureTrafficLightSchema();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("zoneId", "zone-centre-ville");
        stats.put("avgFlux", averageRecent("TRAFFIC_FLOW", "vehicle_count", 20));
        stats.put("avgPollution", averageRecent("POLLUTION", "pm25", 20));
        stats.put("avgNoise", averageRecent("NOISE", "noise_db", 20));
        stats.put("activeAlerts", countRecentAlerts(15));
        stats.put("blockedRoutes", countBlockedRoutes());
        stats.put("congestedRoutes", loadCongestedRoutes().size());
        stats.put("executedActions", count("executed_actions"));
        return stats;
    }

    public List<ExecutedActionRecord> loadExecutedActions(int limit) {
        String sql = """
                SELECT id, action_name, zone_id, intersection_id, transport, status, details, created_at
                FROM executed_actions
                ORDER BY created_at DESC
                LIMIT ?
                """;
        List<ExecutedActionRecord> records = new ArrayList<>();
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ExecutedActionRecord record = new ExecutedActionRecord();
                    record.setId(rs.getLong("id"));
                    record.setActionName(rs.getString("action_name"));
                    record.setZoneId(rs.getString("zone_id"));
                    record.setIntersectionId(rs.getString("intersection_id"));
                    record.setTransport(rs.getString("transport"));
                    record.setStatus(rs.getString("status"));
                    record.setDetails(rs.getString("details"));
                    Timestamp created = rs.getTimestamp("created_at");
                    record.setTimestamp(created == null ? 0L : created.getTime());
                    records.add(record);
                }
            }
        } catch (Exception ex) {
            LOG.warn("loadExecutedActions failed: {}", ex.getMessage());
        }
        return records;
    }

    public Map<String, Object> loadMapData() {
        Map<String, Object> mapData = new LinkedHashMap<>();
        List<Map<String, Object>> vehicles = new ArrayList<>();
        List<Map<String, Object>> trafficLights = new ArrayList<>(loadTrafficLights());
        List<Map<String, Object>> accidents = new ArrayList<>();

        for (Map<String, Object> snapshot : loadIntersectionSnapshots()) {
            String intersectionId = String.valueOf(snapshot.get("intersectionId"));
            double baseLat = ((Number) snapshot.get("lat")).doubleValue();
            double baseLng = ((Number) snapshot.get("lng")).doubleValue();
            int vehiclesCount = (int) Math.max(1, Math.min(5, Math.round(((Number) snapshot.get("vehicleCount")).intValue() / 30.0)));
            for (int i = 0; i < vehiclesCount; i++) {
                Map<String, Object> vehicle = new LinkedHashMap<>();
                vehicle.put("id", intersectionId + "-veh-" + i);
                vehicle.put("intersectionId", intersectionId);
                vehicle.put("routeName", routeName(intersectionId));
                vehicle.put("lat", baseLat + (i * 0.00025));
                vehicle.put("lng", baseLng - (i * 0.00018));
                vehicle.put("vehicleCount", snapshot.get("vehicleCount"));
                vehicles.add(vehicle);
            }
            if (Boolean.TRUE.equals(snapshot.get("accidentDetected")) || Boolean.TRUE.equals(snapshot.get("roadBlocked"))) {
                Map<String, Object> accident = new LinkedHashMap<>();
                accident.put("intersectionId", intersectionId);
                accident.put("routeName", routeName(intersectionId));
                accident.put("lat", baseLat);
                accident.put("lng", baseLng);
                accident.put("blocked", snapshot.get("roadBlocked"));
                accidents.add(accident);
            }
        }

        mapData.put("vehicles", vehicles);
        mapData.put("trafficLights", trafficLights);
        mapData.put("accidents", accidents);
        return mapData;
    }

    private long count(String tableName) {
        String sql = "SELECT COUNT(*) FROM " + tableName;
        try (Connection connection = getConnection();
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception ex) {
            LOG.error("count failed on {}: {}", tableName, ex.getMessage());
        }
        return 0L;
    }

    private long countByType(String alertType) {
        String sql = "SELECT COUNT(*) FROM alerts WHERE alert_type = ?";
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, alertType);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception ex) {
            LOG.error("countByType failed: {}", ex.getMessage());
        }
        return 0L;
    }

    private double averageRecent(String sensorType, String column, int limit) {
        String sql = """
                SELECT AVG(metric_value)
                FROM (
                    SELECT %s AS metric_value
                    FROM sensor_measurements
                    WHERE sensor_type = ?
                    ORDER BY id DESC
                    LIMIT ?
                ) recent_values
                """.formatted(column);
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sensorType);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble(1);
                }
            }
        } catch (Exception ex) {
            LOG.warn("averageRecent failed for {}: {}", sensorType, ex.getMessage());
        }
        return 0.0;
    }

    private long countRecentAlerts(int minutes) {
        String sql = """
                SELECT COUNT(*)
                FROM alerts
                WHERE created_at >= (CURRENT_TIMESTAMP - INTERVAL ? MINUTE)
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, minutes);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong(1);
                }
            }
        } catch (Exception ex) {
            LOG.warn("countRecentAlerts failed: {}", ex.getMessage());
        }
        return 0L;
    }

    private long lastMeasurementId() {
        String sql = "SELECT MAX(id) FROM sensor_measurements";
        try (Connection connection = getConnection();
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception ex) {
            LOG.warn("lastMeasurementId failed: {}", ex.getMessage());
        }
        return 0L;
    }

    private long countBlockedRoutes() {
        ensureTrafficLightSchema();
        String sql = "SELECT COUNT(*) FROM traffic_light_state WHERE road_blocked = TRUE";
        try (Connection connection = getConnection();
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (Exception ex) {
            LOG.warn("countBlockedRoutes failed: {}", ex.getMessage());
        }
        return 0L;
    }

    private String routeName(String intersectionId) {
        return ROUTE_NAMES.getOrDefault(intersectionId, intersectionId);
    }

    private double[] coordinates(String intersectionId) {
        return COORDINATES.getOrDefault(intersectionId, new double[]{34.020882, -6.841650});
    }

    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(AppConfig.mysqlUrl(), AppConfig.mysqlUser(), AppConfig.mysqlPassword());
    }

    private void ensureTrafficLightSchema() {
        if (trafficLightSchemaChecked) {
            return;
        }
        synchronized (DashboardRepository.class) {
            if (trafficLightSchemaChecked) {
                return;
            }
            try (Connection connection = getConnection();
                 Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE IF NOT EXISTS traffic_light_state (
                            intersection_id VARCHAR(64) PRIMARY KEY,
                            green_duration_seconds INT NOT NULL,
                            road_blocked BOOLEAN NOT NULL DEFAULT FALSE,
                            status_label VARCHAR(32) NOT NULL DEFAULT 'GREEN',
                            updated_at TIMESTAMP NOT NULL,
                            INDEX idx_light_updated (updated_at)
                        )
                        """);
                ensureColumn(connection, "road_blocked",
                        "ALTER TABLE traffic_light_state ADD COLUMN road_blocked BOOLEAN NOT NULL DEFAULT FALSE");
                ensureColumn(connection, "status_label",
                        "ALTER TABLE traffic_light_state ADD COLUMN status_label VARCHAR(32) NOT NULL DEFAULT 'GREEN'");
                trafficLightSchemaChecked = true;
            } catch (Exception ex) {
                LOG.warn("ensureTrafficLightSchema failed: {}", ex.getMessage());
            }
        }
    }

    private void ensureColumn(Connection connection, String columnName, String ddl) throws Exception {
        try (ResultSet rs = connection.getMetaData()
                .getColumns(connection.getCatalog(), null, "traffic_light_state", columnName)) {
            if (rs.next()) {
                return;
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }
}
