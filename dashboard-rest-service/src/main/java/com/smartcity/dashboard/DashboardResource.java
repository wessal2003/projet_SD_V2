package com.smartcity.dashboard;

import com.smartcity.common.config.Thresholds;
import com.smartcity.common.model.AlertRecord;
import com.smartcity.common.model.DashboardOverview;
import com.smartcity.common.model.ExecutedActionRecord;
import com.smartcity.common.model.RecommendationRecord;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;

@Path("/api/dashboard")
@Produces(MediaType.APPLICATION_JSON)
public class DashboardResource {
    private final DashboardRepository repository = new DashboardRepository();

    @GET
    @Path("/overview")
    public DashboardOverview overview() {
        return repository.loadOverview();
    }

    @GET
    @Path("/alerts")
    public List<AlertRecord> alerts(@DefaultValue("20") @QueryParam("limit") int limit) {
        return repository.loadAlerts(limit);
    }

    @GET
    @Path("/recommendations")
    public List<RecommendationRecord> recommendations(@DefaultValue("20") @QueryParam("limit") int limit) {
        return repository.loadRecommendations(limit);
    }

    @GET
    @Path("/traffic-lights")
    public List<Map<String, Object>> trafficLights() {
        return repository.loadTrafficLights();
    }

    @GET
    @Path("/live-metrics")
    public Map<String, Object> liveMetrics() {
        return repository.loadLiveMetrics();
    }

    @GET
    @Path("/intersection-snapshots")
    public List<Map<String, Object>> intersectionSnapshots() {
        return repository.loadIntersectionSnapshots();
    }

    @GET
    @Path("/congested-routes")
    public List<Map<String, Object>> congestedRoutes() {
        return repository.loadCongestedRoutes();
    }

    @GET
    @Path("/zone-stats")
    public Map<String, Object> zoneStats() {
        return repository.loadZoneStats();
    }

    @GET
    @Path("/actions")
    public List<ExecutedActionRecord> actions(@DefaultValue("10") @QueryParam("limit") int limit) {
        return repository.loadExecutedActions(limit);
    }

    @GET
    @Path("/map-data")
    public Map<String, Object> mapData() {
        return repository.loadMapData();
    }

    @GET
    @Path("/thresholds")
    public Map<String, Object> thresholds() {
        return Map.of(
                "congestionVehicles", Thresholds.CONGESTION_VEHICLE_THRESHOLD,
                "congestionSpeed", Thresholds.CONGESTION_SPEED_THRESHOLD,
                "pm25", Thresholds.POLLUTION_PM25_THRESHOLD,
                "noiseDb", Thresholds.NOISE_DB_THRESHOLD);
    }

    @GET
    @Path("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
