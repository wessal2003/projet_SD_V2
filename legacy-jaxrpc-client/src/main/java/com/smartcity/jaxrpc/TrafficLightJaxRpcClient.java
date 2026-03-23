package com.smartcity.jaxrpc;

import com.smartcity.common.config.AppConfig;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import javax.xml.rpc.ParameterMode;
import java.net.URL;

public class TrafficLightJaxRpcClient {
    private static final Logger LOG = LoggerFactory.getLogger(TrafficLightJaxRpcClient.class);
    private static final String TARGET_NAMESPACE = "http://trafficlight.jaxrpc.smartcity.com/";

    public String extendGreen(String intersectionId, int additionalSeconds) throws Exception {
        return invoke("extendGreen", org.apache.axis.encoding.XMLType.XSD_STRING,
                new Object[]{intersectionId, additionalSeconds},
                new Parameter[]{
                        new Parameter("intersectionId", org.apache.axis.encoding.XMLType.XSD_STRING),
                        new Parameter("additionalSeconds", org.apache.axis.encoding.XMLType.XSD_INT)
                });
    }

    public String blockRoad(String intersectionId) throws Exception {
        return invoke("blockRoad", org.apache.axis.encoding.XMLType.XSD_STRING,
                new Object[]{intersectionId},
                new Parameter[]{
                        new Parameter("intersectionId", org.apache.axis.encoding.XMLType.XSD_STRING)
                });
    }

    private String invoke(String operationName, javax.xml.namespace.QName returnType,
                          Object[] arguments, Parameter[] parameters) throws Exception {
        Service service = new Service();
        Call call = (Call) service.createCall();

        call.setTargetEndpointAddress(new URL(AppConfig.trafficLightJaxRpcEndpoint()));
        call.setOperationName(new QName(TARGET_NAMESPACE, operationName));
        call.setUseSOAPAction(false);
        for (Parameter parameter : parameters) {
            call.addParameter(parameter.name, parameter.xmlType, ParameterMode.IN);
        }
        call.setReturnType(returnType);

        Object response = call.invoke(arguments);
        String result = response == null ? "ACK: null" : response.toString();
        LOG.info("JAX-RPC traffic light request sent to {} operation={}",
                AppConfig.trafficLightJaxRpcEndpoint(), operationName);
        return result;
    }

    private record Parameter(String name, QName xmlType) {
    }
}
