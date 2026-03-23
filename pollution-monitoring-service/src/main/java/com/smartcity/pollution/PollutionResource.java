package com.smartcity.pollution;

import com.smartcity.analysis.common.DetectionResult;
import com.smartcity.analysis.common.SensorEventProcessor;
import com.smartcity.common.config.Thresholds;
import com.smartcity.common.model.AlertType;
import com.smartcity.common.model.SensorEvent;
import com.smartcity.common.model.SensorType;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Map;
import java.util.Optional;

@Path("/api/pollution")
@Produces(MediaType.APPLICATION_JSON)
public class PollutionResource {
    private final SensorEventProcessor processor = new SensorEventProcessor("ServicePollution");

    @POST
    @Path("/events")
    @Consumes(MediaType.APPLICATION_JSON)
    public Map<String, String> submitEvent(SensorEvent event) {
        String result = processor.process(event, this::detectPollution);
        return Map.of("status", result);
    }

    @GET
    @Path("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "ServicePollution");
    }

    private Optional<DetectionResult> detectPollution(SensorEvent event) {
        if (event.getSensorType() == SensorType.POLLUTION
                && event.getPm25() > Thresholds.POLLUTION_PM25_THRESHOLD) {
            return Optional.of(new DetectionResult(
                    AlertType.HIGH_POLLUTION,
                    "Pollution elevee (PM2.5=" + event.getPm25() + ") a " + event.getIntersectionId(),
                    "Reduire le trafic sur la zone et prioriser les transports publics."));
        }
        return Optional.empty();
    }
}
