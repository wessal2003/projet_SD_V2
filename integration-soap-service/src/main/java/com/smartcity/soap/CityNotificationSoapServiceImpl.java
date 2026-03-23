package com.smartcity.soap;

import com.smartcity.common.soap.CityNotificationSoapPort;
import jakarta.jws.WebMethod;
import jakarta.jws.WebParam;
import jakarta.jws.WebService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@WebService(
        targetNamespace = "http://soap.common.smartcity.com/",
        serviceName = "CityNotificationSoapService",
        portName = "CityNotificationSoapPort")
public class CityNotificationSoapServiceImpl implements CityNotificationSoapPort {
    private static final Logger LOG = LoggerFactory.getLogger(CityNotificationSoapServiceImpl.class);
    private final SoapNotificationRepository repository = new SoapNotificationRepository();

    @WebMethod
    @Override
    public String notifyAlert(
            @WebParam(name = "alertType") String alertType,
            @WebParam(name = "zoneId") String zoneId,
            @WebParam(name = "message") String message) {
        String response = "SOAP/JAX-WS notification received: [" + alertType + "] zone=" + zoneId + " message=" + message;
        repository.insertNotification(alertType, zoneId, message, "JAX-WS", "RECEIVED");
        LOG.info(response);
        return "ACK: " + response;
    }
}
