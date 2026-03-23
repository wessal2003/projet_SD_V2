package com.smartcity.common.rmi;

import com.smartcity.common.model.SensorEvent;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface CameraRemoteService extends Remote {
    String submitAccidentEvent(SensorEvent event) throws RemoteException;
}
