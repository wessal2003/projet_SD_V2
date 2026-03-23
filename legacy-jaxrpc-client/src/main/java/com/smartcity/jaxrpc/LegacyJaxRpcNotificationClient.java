package com.smartcity.jaxrpc;

import com.smartcity.common.config.AppConfig;
import org.apache.axis.client.Call;
import org.apache.axis.client.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import javax.xml.rpc.ParameterMode;
import java.net.URL;

public class LegacyJaxRpcNotificationClient {
    private static final Logger LOG = LoggerFactory.getLogger(LegacyJaxRpcNotificationClient.class);
    private static final String TARGET_NAMESPACE = "http://soap.common.smartcity.com/";
    private static final String OPERATION_NAME = "notifyAlert";

    public String notifyAlert(String alertType, String zoneId, String message) throws Exception {
        Service service = new Service();
        Call call = (Call) service.createCall();
        String enrichedMessage = "[via JAX-RPC] " + message;

        call.setTargetEndpointAddress(new URL(AppConfig.soapEndpoint()));
        call.setOperationName(new QName(TARGET_NAMESPACE, OPERATION_NAME));
        call.setUseSOAPAction(false);
        call.addParameter("alertType", org.apache.axis.encoding.XMLType.XSD_STRING, ParameterMode.IN);
        call.addParameter("zoneId", org.apache.axis.encoding.XMLType.XSD_STRING, ParameterMode.IN);
        call.addParameter("message", org.apache.axis.encoding.XMLType.XSD_STRING, ParameterMode.IN);
        call.setReturnType(org.apache.axis.encoding.XMLType.XSD_STRING);

        Object response = call.invoke(new Object[]{alertType, zoneId, enrichedMessage});
        String result = response == null ? "ACK: null" : response.toString();
        LOG.info("JAX-RPC notification sent to {} for zone={} alertType={}", AppConfig.soapEndpoint(), zoneId, alertType);
        LOG.info("JAX-RPC response: {}", result);
        return result;
    }
}
