package com.smartcity.common.soap;

import com.smartcity.common.model.SensorEvent;
import jakarta.jws.WebMethod;
import jakarta.jws.WebService;

@WebService(targetNamespace = "http://traffic.soap.smartcity.com/", name = "ServiceFluxVehicules")
public interface ServiceFluxVehicules {
    @WebMethod
    String submitTrafficEvent(SensorEvent event);

    @WebMethod
    String health();
}
