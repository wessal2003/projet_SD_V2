package com.smartcity.dashboard;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.model.AlertRecord;
import com.smartcity.common.model.AlertType;
import com.smartcity.common.model.DashboardOverview;
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
import java.util.List;
import java.util.Map;

public class DashboardRepository {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardRepository.class);

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
        String sql = """
                SELECT intersection_id, green_duration_seconds, updated_at
                FROM traffic_light_state
                ORDER BY updated_at DESC
                """;
        List<Map<String, Object>> rows = new ArrayList<>();
        try (Connection connection = getConnection();
             Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new HashMap<>();
                row.put("intersectionId", rs.getString("intersection_id"));
                row.put("greenDurationSeconds", rs.getInt("green_duration_seconds"));
                Timestamp updated = rs.getTimestamp("updated_at");
                row.put("updatedAt", updated == null ? 0L : updated.getTime());
                rows.add(row);
            }
        } catch (Exception ex) {
            LOG.warn("loadTrafficLights failed (table may be empty): {}", ex.getMessage());
        }
        return rows;
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

    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(AppConfig.mysqlUrl(), AppConfig.mysqlUser(), AppConfig.mysqlPassword());
    }
}
