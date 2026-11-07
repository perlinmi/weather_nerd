package com.weathernerd.weather.service;

public class ForecastUnavailableException extends RuntimeException {
    public ForecastUnavailableException() {
        super("A current forecast is unavailable.");
    }

    public ForecastUnavailableException(Throwable cause) {
        super("A current forecast is unavailable.", cause);
    }
}
