package com.smartcity.soap;

import com.smartcity.common.config.AppConfig;
import jakarta.xml.ws.Endpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SoapServerMain {
    private static final Logger LOG = LoggerFactory.getLogger(SoapServerMain.class);

    public static void main(String[] args) {
        String endpointUrl = AppConfig.soapEndpoint();
        Endpoint.publish(endpointUrl, new CityNotificationSoapServiceImpl());
        LOG.info("SOAP service running at {}", endpointUrl);
        LOG.info("WSDL available at {}?wsdl", endpointUrl);
    }
}
