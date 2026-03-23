package com.smartcity.traffic;

import com.smartcity.common.config.AppConfig;
import jakarta.xml.ws.Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrafficMonitoringMain {
    private static final Logger LOG = LoggerFactory.getLogger(TrafficMonitoringMain.class);

    public static void main(String[] args) throws Exception {
        String endpointUrl = AppConfig.trafficWsEndpoint();
        Endpoint endpoint = Endpoint.publish(endpointUrl, new TrafficFlowSoapServiceImpl());
        LOG.info("ServiceFluxVehicules JAX-WS started at {}", endpointUrl);
        Thread.currentThread().join();
        endpoint.stop();
    }
}
