package com.smartcity.dashboard;

import com.smartcity.common.config.AppConfig;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class DashboardServerMain {
    private static final Logger LOG = LoggerFactory.getLogger(DashboardServerMain.class);

    public static void main(String[] args) throws Exception {
        URI baseUri = URI.create(AppConfig.restBaseUrl());

        ResourceConfig config = new ResourceConfig();
        config.register(DashboardResource.class);
        config.register(HomeResource.class);
        config.packages("org.glassfish.jersey.jackson");

        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, config);
        LOG.info("Dashboard REST started at {}", baseUri);
        LOG.info("Dashboard UI: {}", baseUri);
        Thread.currentThread().join();
        server.shutdownNow();
    }
}
