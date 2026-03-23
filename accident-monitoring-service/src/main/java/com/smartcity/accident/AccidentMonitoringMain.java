package com.smartcity.accident;

import com.smartcity.common.config.AppConfig;
import com.smartcity.common.rmi.CameraRemoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.ExportException;
import java.util.concurrent.CountDownLatch;

public class AccidentMonitoringMain {
    private static final Logger LOG = LoggerFactory.getLogger(AccidentMonitoringMain.class);

    public static void main(String[] args) throws Exception {
        int rmiPort = AppConfig.cameraRmiPort();
        String serviceName = AppConfig.cameraRmiServiceName();
        String rmiUrl = "rmi://" + AppConfig.cameraRmiHost() + ":" + rmiPort + "/" + serviceName;

        try {
            LocateRegistry.createRegistry(rmiPort);
        } catch (ExportException ignored) {
            LocateRegistry.getRegistry(rmiPort);
            LOG.info("Camera RMI registry already running on port {}", rmiPort);
        }

        CameraRemoteService service = new CameraRemoteServiceImpl();
        Naming.rebind(rmiUrl, service);

        LOG.info("ServiceCamera JAVA RMI started at {}", rmiUrl);
        new CountDownLatch(1).await();
    }
}
