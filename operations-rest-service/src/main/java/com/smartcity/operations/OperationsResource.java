package com.smartcity.operations;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.model.AlertRecord;
import com.smartcity.common.model.RecommendationRecord;
import com.smartcity.common.model.SoapNotificationRecord;
import com.smartcity.common.rmi.TrafficLightRemoteService;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.rmi.Naming;
import java.util.List;
import java.util.Map;

@Path("/api/operations")
@Produces(MediaType.APPLICATION_JSON)
public class OperationsResource {
    private final OperationsRepository repository = new OperationsRepository();

    @GET
    @Path("/alerts/active")
    public List<AlertRecord> activeAlerts(@DefaultValue("20") @QueryParam("limit") int limit,
                                          @DefaultValue("15") @QueryParam("minutes") int minutes) {
        return repository.loadActiveAlerts(limit, minutes);
    }

    @GET
    @Path("/recommendations/latest")
    public List<RecommendationRecord> latestRecommendations(@DefaultValue("20") @QueryParam("limit") int limit) {
        return repository.loadLatestRecommendations(limit);
    }

    @GET
    @Path("/intersections/status")
    public List<Map<String, Object>> intersectionStatus() {
        return repository.loadIntersectionStatus();
    }

    @GET
    @Path("/traffic-lights")
    public List<Map<String, Object>> trafficLights() {
        return repository.loadTrafficLights();
    }

    @POST
    @Path("/traffic-lights/{intersectionId}/extend")
    public Map<String, Object> extendGreen(@PathParam("intersectionId") String intersectionId,
                                           @DefaultValue("15") @QueryParam("seconds") int seconds) throws Exception {
        String url = "rmi://" + AppConfig.rmiHost() + ":" + AppConfig.rmiPort() + "/" + AppConfig.rmiServiceName();
        TrafficLightRemoteService service = (TrafficLightRemoteService) Naming.lookup(url);
        String result = service.extendGreen(intersectionId, seconds);
        return Map.of(
                "intersectionId", intersectionId,
                "seconds", seconds,
                "result", result,
                "transport", "RMI");
    }

    @GET
    @Path("/integrations/soap-notifications")
    public List<SoapNotificationRecord> soapNotifications(@DefaultValue("20") @QueryParam("limit") int limit) {
        return repository.loadSoapNotifications(limit);
    }

    @GET
    @Path("/pipeline/stats")
    public Map<String, Object> pipelineStats() {
        return repository.loadPipelineStats();
    }

    @GET
    @Path("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP", "service", "operations-rest-service");
    }
}
