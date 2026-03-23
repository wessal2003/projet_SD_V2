package com.smartcity.analysis.common;

import com.smartcity.common.model.AlertType;
import com.smartcity.common.model.SensorEvent;
import com.smartcity.jaxrpc.LegacyJaxRpcNotificationClient;
import com.smartcity.jaxrpc.TrafficLightJaxRpcClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistributedActuator {
    private static final Logger LOG = LoggerFactory.getLogger(DistributedActuator.class);

    private final TrafficLightJaxRpcClient trafficLightClient = new TrafficLightJaxRpcClient();
    private final LegacyJaxRpcNotificationClient notificationClient = new LegacyJaxRpcNotificationClient();
    private final ActionAuditRepository auditRepository = new ActionAuditRepository();

    public void apply(AlertType alertType, SensorEvent event, String recommendation, String message) {
        if (alertType == AlertType.CONGESTION || alertType == AlertType.PEAK_HOUR) {
            executeExtendGreen(event, alertType == AlertType.PEAK_HOUR ? 20 : 15);
        }

        if (alertType == AlertType.ACCIDENT) {
            executeBlockRoad(event);
            executeSoapNotification(event, alertType, message, recommendation);
        }

        if (alertType == AlertType.HIGH_POLLUTION) {
            executeSoapNotification(event, alertType, message, recommendation);
        }
    }

    private void executeExtendGreen(SensorEvent event, int seconds) {
        try {
            String result = trafficLightClient.extendGreen(event.getIntersectionId(), seconds);
            LOG.info("ACTION EXECUTED: extendGreen intersection={} seconds={}", event.getIntersectionId(), seconds);
            auditRepository.insertAction("extendGreen", event.getZoneId(), event.getIntersectionId(),
                    "JAX-RPC", "SUCCESS", result);
        } catch (Exception ex) {
            LOG.warn("JAX-RPC extendGreen failed: {}", ex.getMessage());
            auditRepository.insertAction("extendGreen", event.getZoneId(), event.getIntersectionId(),
                    "JAX-RPC", "FAILED", ex.getMessage());
        }
    }

    private void executeBlockRoad(SensorEvent event) {
        try {
            String result = trafficLightClient.blockRoad(event.getIntersectionId());
            LOG.info("ACTION EXECUTED: blockRoad intersection={}", event.getIntersectionId());
            auditRepository.insertAction("blockRoad", event.getZoneId(), event.getIntersectionId(),
                    "JAX-RPC", "SUCCESS", result);
        } catch (Exception ex) {
            LOG.warn("JAX-RPC blockRoad failed: {}", ex.getMessage());
            auditRepository.insertAction("blockRoad", event.getZoneId(), event.getIntersectionId(),
                    "JAX-RPC", "FAILED", ex.getMessage());
        }
    }

    private void executeSoapNotification(SensorEvent event, AlertType alertType, String message, String recommendation) {
        try {
            String response = notificationClient.notifyAlert(alertType.name(), event.getZoneId(),
                    message + " -> " + recommendation);
            LOG.info("ACTION EXECUTED: notifySOAP alertType={} intersection={}", alertType, event.getIntersectionId());
            auditRepository.insertAction("notifySOAP", event.getZoneId(), event.getIntersectionId(),
                    "JAX-WS/JAX-RPC", "SUCCESS", response);
        } catch (Exception ex) {
            LOG.warn("SOAP notification failed: {}", ex.getMessage());
            auditRepository.insertAction("notifySOAP", event.getZoneId(), event.getIntersectionId(),
                    "JAX-WS/JAX-RPC", "FAILED", ex.getMessage());
        }
    }
}
