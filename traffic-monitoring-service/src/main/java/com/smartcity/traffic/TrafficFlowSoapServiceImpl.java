package com.smartcity.traffic;

import com.smartcity.analysis.common.DetectionResult;
import com.smartcity.analysis.common.SensorEventProcessor;
import com.smartcity.common.config.Thresholds;
import com.smartcity.common.model.AlertType;
import com.smartcity.common.model.SensorEvent;
import com.smartcity.common.model.SensorType;
import com.smartcity.common.soap.ServiceFluxVehicules;
import jakarta.jws.WebService;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;

@WebService(
        endpointInterface = "com.smartcity.common.soap.ServiceFluxVehicules",
        serviceName = "ServiceFluxVehiculesService",
        portName = "ServiceFluxVehiculesPort",
        targetNamespace = "http://traffic.soap.smartcity.com/")
public class TrafficFlowSoapServiceImpl implements ServiceFluxVehicules {
    private final SensorEventProcessor processor = new SensorEventProcessor("ServiceFluxVehicules");

    @Override
    public String submitTrafficEvent(SensorEvent event) {
        return processor.process(event, this::detectTraffic);
    }

    @Override
    public String health() {
        return "UP";
    }

    private Optional<DetectionResult> detectTraffic(SensorEvent event) {
        if (event.getSensorType() == SensorType.TRAFFIC_FLOW
                && event.getVehicleCount() > Thresholds.CONGESTION_VEHICLE_THRESHOLD
                && event.getAverageSpeedKmh() < Thresholds.CONGESTION_SPEED_THRESHOLD) {
            String recommendation = event.getVehicleCount() > 120
                    ? "Ouvrir une voie supplementaire, allonger le feu vert de 15 secondes et proposer un itineraire alternatif."
                    : "Allonger le feu vert de 15 secondes et proposer un itineraire alternatif.";
            return Optional.of(new DetectionResult(
                    AlertType.CONGESTION,
                    "Congestion detectee a " + event.getIntersectionId()
                            + " (vehicules=" + event.getVehicleCount()
                            + ", vitesse=" + event.getAverageSpeedKmh() + " km/h)",
                    recommendation));
        }
        if (event.getSensorType() == SensorType.TRAFFIC_FLOW
                && isPeakHour(event)
                && event.getVehicleCount() > 75) {
            return Optional.of(new DetectionResult(
                    AlertType.PEAK_HOUR,
                    "Heure de pointe detectee a " + event.getIntersectionId()
                            + " (vehicules=" + event.getVehicleCount() + ")",
                    "Synchronisation des feux sur l'axe principal et priorisation des transports publics."));
        }
        return Optional.empty();
    }

    private boolean isPeakHour(SensorEvent event) {
        LocalTime time = Instant.ofEpochMilli(event.getTimestamp())
                .atZone(ZoneId.systemDefault())
                .toLocalTime();
        return (time.getHour() >= 7 && time.getHour() <= 9)
                || (time.getHour() >= 17 && time.getHour() <= 19);
    }
}
