package com.smartcity.common.rmi;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

public interface TrafficLightRemoteService extends Remote {
    String extendGreen(String intersectionId, int additionalSeconds) throws RemoteException;

    Map<String, Integer> getGreenDurations() throws RemoteException;
}
