package com.smartcity.dashboard;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Path("/")
public class HomeResource {
    @GET
    @Produces(MediaType.TEXT_HTML)
    public String home() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("web/index.html")) {
            if (in == null) {
                return "<h1>Smart Traffic Dashboard</h1><p>index.html not found</p>";
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
