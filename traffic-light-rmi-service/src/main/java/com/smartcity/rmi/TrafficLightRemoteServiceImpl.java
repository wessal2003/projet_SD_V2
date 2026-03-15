package com.smartcity.rmi;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.rmi.TrafficLightRemoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TrafficLightRemoteServiceImpl extends UnicastRemoteObject implements TrafficLightRemoteService {
    private static final Logger LOG = LoggerFactory.getLogger(TrafficLightRemoteServiceImpl.class);
    private final ConcurrentHashMap<String, Integer> greenDurations = new ConcurrentHashMap<>();

    protected TrafficLightRemoteServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public String extendGreen(String intersectionId, int additionalSeconds) {
        int newValue = greenDurations.merge(intersectionId, 30 + additionalSeconds, Integer::sum);
        persistState(intersectionId, newValue);
        String result = "Green extended at " + intersectionId + " -> " + newValue + "s";
        LOG.info(result);
        return result;
    }

    @Override
    public Map<String, Integer> getGreenDurations() {
        return Map.copyOf(greenDurations);
    }

    private void persistState(String intersectionId, int greenDurationSeconds) {
        String sql = """
                INSERT INTO traffic_light_state (intersection_id, green_duration_seconds, updated_at)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    green_duration_seconds = VALUES(green_duration_seconds),
                    updated_at = VALUES(updated_at)
                """;

        try (Connection connection = DriverManager.getConnection(AppConfig.mysqlUrl(), AppConfig.mysqlUser(), AppConfig.mysqlPassword());
             PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, intersectionId);
            ps.setInt(2, greenDurationSeconds);
            ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
            ps.executeUpdate();
        } catch (Exception ex) {
            LOG.warn("Failed to persist traffic light state: {}", ex.getMessage());
        }
    }
}
