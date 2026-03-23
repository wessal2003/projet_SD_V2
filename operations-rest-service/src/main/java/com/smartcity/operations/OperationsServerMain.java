package com.smartcity.operations;

import com.smartcity.common.config.AppConfig;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class OperationsServerMain {
    private static final Logger LOG = LoggerFactory.getLogger(OperationsServerMain.class);

    public static void main(String[] args) throws Exception {
        URI baseUri = URI.create(AppConfig.operationsBaseUrl());
        ResourceConfig config = new ResourceConfig();
        config.register(OperationsResource.class);
        config.packages("org.glassfish.jersey.jackson");

        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, config);
        LOG.info("Operations REST started at {}", baseUri);
        Thread.currentThread().join();
        server.shutdownNow();
    }
}
