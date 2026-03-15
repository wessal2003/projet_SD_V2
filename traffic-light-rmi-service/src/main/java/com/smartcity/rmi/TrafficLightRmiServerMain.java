package com.smartcity.rmi;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.rmi.TrafficLightRemoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;

public class TrafficLightRmiServerMain {
    private static final Logger LOG = LoggerFactory.getLogger(TrafficLightRmiServerMain.class);

    public static void main(String[] args) throws Exception {
        int rmiPort = AppConfig.rmiPort();
        String serviceName = AppConfig.rmiServiceName();
        String rmiUrl = "rmi://" + AppConfig.rmiHost() + ":" + rmiPort + "/" + serviceName;

        LocateRegistry.createRegistry(rmiPort);
        TrafficLightRemoteService service = new TrafficLightRemoteServiceImpl();
        Naming.rebind(rmiUrl, service);

        LOG.info("TrafficLight RMI service started at {}", rmiUrl);
    }
}
