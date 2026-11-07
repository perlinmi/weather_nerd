package com.weathernerd.weather.client;

public class PermissionsServiceException extends RuntimeException {
    public PermissionsServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public PermissionsServiceException(String message) {
        super(message);
    }
}
