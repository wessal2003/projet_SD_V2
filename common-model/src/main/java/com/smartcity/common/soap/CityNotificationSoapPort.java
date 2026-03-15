package com.smartcity.common.soap;

public interface CityNotificationSoapPort {
    String notifyAlert(String alertType, String zoneId, String message);
}
