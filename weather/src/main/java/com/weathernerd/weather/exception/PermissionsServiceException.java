package com.weathernerd.weather.exception;

public class PermissionsServiceException extends RuntimeException {
    public PermissionsServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public PermissionsServiceException(String message) {
        super(message);
    }
}
