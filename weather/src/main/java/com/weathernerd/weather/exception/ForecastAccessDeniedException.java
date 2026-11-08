package com.weathernerd.weather.exception;

public class ForecastAccessDeniedException extends RuntimeException {
    public ForecastAccessDeniedException() {
        super("The caller is not permitted to access the forecast.");
    }
}
