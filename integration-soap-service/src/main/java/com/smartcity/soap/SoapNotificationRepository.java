package com.smartcity.soap;

import com.smartcity.common.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;

public class SoapNotificationRepository {
    private static final Logger LOG = LoggerFactory.getLogger(SoapNotificationRepository.class);
    private static volatile boolean schemaReady;

    public void insertNotification(String alertType, String zoneId, String message, String transport, String status) {
        String sql = """
                INSERT INTO soap_notifications (alert_type, zone_id, message, transport, status, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = DriverManager.getConnection(
                AppConfig.mysqlUrl(), AppConfig.mysqlUser(), AppConfig.mysqlPassword());
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ensureSchema(connection);
            ps.setString(1, alertType);
            ps.setString(2, zoneId);
            ps.setString(3, message);
            ps.setString(4, transport);
            ps.setString(5, status);
            ps.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (Exception ex) {
            LOG.warn("Failed to persist SOAP notification: {}", ex.getMessage());
        }
    }

    private void ensureSchema(Connection connection) {
        if (schemaReady) {
            return;
        }

        String ddl = """
                CREATE TABLE IF NOT EXISTS soap_notifications (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    alert_type VARCHAR(32) NOT NULL,
                    zone_id VARCHAR(64) NOT NULL,
                    message VARCHAR(500) NOT NULL,
                    transport VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    INDEX idx_soap_created (created_at),
                    INDEX idx_soap_alert_type (alert_type)
                )
                """;
        try (PreparedStatement ps = connection.prepareStatement(ddl)) {
            ps.execute();
            schemaReady = true;
        } catch (Exception ex) {
            LOG.warn("Failed to ensure soap_notifications table: {}", ex.getMessage());
        }
    }
}
