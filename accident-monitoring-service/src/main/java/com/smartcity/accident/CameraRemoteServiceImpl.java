package com.smartcity.accident;

import com.smartcity.analysis.common.DetectionResult;
import com.smartcity.analysis.common.SensorEventProcessor;
import com.smartcity.common.model.AlertType;
import com.smartcity.common.model.SensorEvent;
import com.smartcity.common.model.SensorType;
import com.smartcity.common.rmi.CameraRemoteService;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Optional;

public class CameraRemoteServiceImpl extends UnicastRemoteObject implements CameraRemoteService {
    private final SensorEventProcessor processor = new SensorEventProcessor("ServiceCamera");

    protected CameraRemoteServiceImpl() throws RemoteException {
        super();
    }

    @Override
    public String submitAccidentEvent(SensorEvent event) throws RemoteException {
        return processor.process(event, this::detectAccident);
    }

    private Optional<DetectionResult> detectAccident(SensorEvent event) {
        if (event.getSensorType() == SensorType.ACCIDENT_CAMERA && event.isAccidentDetected()) {
            return Optional.of(new DetectionResult(
                    AlertType.ACCIDENT,
                    "Accident detecte par camera a " + event.getIntersectionId(),
                    "Devier le trafic et synchroniser les feux sur l'axe principal."));
        }
        return Optional.empty();
    }
}
