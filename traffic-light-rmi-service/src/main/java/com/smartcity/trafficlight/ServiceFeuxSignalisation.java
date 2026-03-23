package com.smartcity.trafficlight;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartcity.common.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceFeuxSignalisation {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceFeuxSignalisation.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ConcurrentHashMap<String, Integer> greenDurations = new ConcurrentHashMap<>();

    public ServiceFeuxSignalisation() {
        ensureSchema();
    }

    public String extendGreen(String intersectionId, int additionalSeconds) {
        int newValue = greenDurations.merge(intersectionId, 30 + additionalSeconds, Integer::sum);
        persistState(intersectionId, newValue, false, "GREEN");
        String result = "Feu vert allonge a " + intersectionId + " -> " + newValue + "s";
        LOG.info(result);
        return result;
    }

    public String blockRoad(String intersectionId) {
        int currentDuration = greenDurations.getOrDefault(intersectionId, 30);
        persistState(intersectionId, currentDuration, true, "BLOCKED");
        String result = "Route bloquee a " + intersectionId;
        LOG.info(result);
        return result;
    }

    public String getGreenDurations() {
        try {
            return MAPPER.writeValueAsString(Map.copyOf(greenDurations));
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private void persistState(String intersectionId, int greenDurationSeconds, boolean roadBlocked, String statusLabel) {
        String sql = """
                INSERT INTO traffic_light_state (intersection_id, green_duration_seconds, road_blocked, status_label, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    green_duration_seconds = VALUES(green_duration_seconds),
                    road_blocked = VALUES(road_blocked),
                    status_label = VALUES(status_label),
                    updated_at = VALUES(updated_at)
                """;

        try (Connection connection = DriverManager.getConnection(AppConfig.mysqlUrl(), AppConfig.mysqlUser(), AppConfig.mysqlPassword());
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, intersectionId);
            ps.setInt(2, greenDurationSeconds);
            ps.setBoolean(3, roadBlocked);
            ps.setString(4, statusLabel);
            ps.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (Exception ex) {
            LOG.warn("Failed to persist traffic light state: {}", ex.getMessage());
        }
    }

    private void ensureSchema() {
        try (Connection connection = DriverManager.getConnection(AppConfig.mysqlUrl(), AppConfig.mysqlUser(), AppConfig.mysqlPassword());
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
        } catch (Exception ex) {
            LOG.warn("Failed to ensure traffic_light_state schema: {}", ex.getMessage());
        }
    }

    private void ensureColumn(Connection connection, String columnName, String ddl) throws Exception {
        if (columnExists(connection, columnName)) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            statement.execute(ddl);
        }
    }

    private boolean columnExists(Connection connection, String columnName) throws Exception {
        try (ResultSet rs = connection.getMetaData()
                .getColumns(connection.getCatalog(), null, "traffic_light_state", columnName)) {
            return rs.next();
        }
    }
}
