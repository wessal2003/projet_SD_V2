package com.smartcity.analysis.common;

import com.smartcity.common.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;

public class ActionAuditRepository {
    private static final Logger LOG = LoggerFactory.getLogger(ActionAuditRepository.class);

    public ActionAuditRepository() {
        ensureTable();
    }

    public void insertAction(String actionName, String zoneId, String intersectionId,
                             String transport, String status, String details) {
        String sql = """
                INSERT INTO executed_actions
                (action_name, zone_id, intersection_id, transport, status, details, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = getConnection();
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, actionName);
            ps.setString(2, zoneId);
            ps.setString(3, intersectionId);
            ps.setString(4, transport);
            ps.setString(5, status);
            ps.setString(6, details);
            ps.setTimestamp(7, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (Exception ex) {
            LOG.warn("insertAction failed: {}", ex.getMessage());
        }
    }

    private void ensureTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS executed_actions (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    action_name VARCHAR(64) NOT NULL,
                    zone_id VARCHAR(64) NOT NULL,
                    intersection_id VARCHAR(64) NOT NULL,
                    transport VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    details VARCHAR(500) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    INDEX idx_action_created (created_at),
                    INDEX idx_action_name (action_name)
                )
                """;
        try (Connection connection = getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (Exception ex) {
            LOG.warn("Failed to ensure executed_actions table: {}", ex.getMessage());
        }
    }

    private Connection getConnection() throws Exception {
        return DriverManager.getConnection(AppConfig.mysqlUrl(), AppConfig.mysqlUser(), AppConfig.mysqlPassword());
    }
}
