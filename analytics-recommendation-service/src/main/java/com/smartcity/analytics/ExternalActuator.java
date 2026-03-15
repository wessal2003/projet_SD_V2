package com.smartcity.analytics;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.model.AlertType;
import com.smartcity.common.model.SensorEvent;
import com.smartcity.common.rmi.TrafficLightRemoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.rmi.Naming;

public class ExternalActuator {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalActuator.class);

    private TrafficLightRemoteService rmiClient;
    private final HttpClient httpClient = HttpClient.newHttpClient();

    public void apply(AlertType alertType, SensorEvent event, String recommendation, String message) {
        try {
            if (alertType == AlertType.CONGESTION || alertType == AlertType.ACCIDENT) {
                TrafficLightRemoteService client = rmiClient();
                String result = client.extendGreen(event.getIntersectionId(), 15);
                LOG.info("RMI response: {}", result);
            }
        } catch (Exception ex) {
            LOG.warn("RMI call failed: {}", ex.getMessage());
        }

        try {
            if (alertType == AlertType.ACCIDENT || alertType == AlertType.HIGH_POLLUTION) {
                String soapResponse = notifySoap(alertType.name(), event.getZoneId(), message + " -> " + recommendation);
                LOG.info("SOAP response: {}", soapResponse);
            }
        } catch (Exception ex) {
            LOG.warn("SOAP call failed: {}", ex.getMessage());
        }
    }

    private synchronized TrafficLightRemoteService rmiClient() throws Exception {
        if (rmiClient == null) {
            String url = "rmi://" + AppConfig.rmiHost() + ":" + AppConfig.rmiPort() + "/" + AppConfig.rmiServiceName();
            rmiClient = (TrafficLightRemoteService) Naming.lookup(url);
        }
        return rmiClient;
    }

    private String notifySoap(String alertType, String zoneId, String message) throws Exception {
        String envelope = """
                <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:sm="http://soap.common.smartcity.com/">
                  <soapenv:Header/>
                  <soapenv:Body>
                    <sm:notifyAlert>
                      <alertType>%s</alertType>
                      <zoneId>%s</zoneId>
                      <message>%s</message>
                    </sm:notifyAlert>
                  </soapenv:Body>
                </soapenv:Envelope>
                """.formatted(escapeXml(alertType), escapeXml(zoneId), escapeXml(message));

        HttpRequest request = HttpRequest.newBuilder(URI.create(AppConfig.soapEndpoint()))
                .header("Content-Type", "text/xml; charset=utf-8")
                .header("SOAPAction", "\"\"")
                .POST(HttpRequest.BodyPublishers.ofString(envelope))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return "HTTP " + response.statusCode();
    }

    private String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
