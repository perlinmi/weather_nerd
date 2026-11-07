package com.weathernerd.weather.service;

public class ForecastAccessDeniedException extends RuntimeException {
    public ForecastAccessDeniedException() {
        super("The caller is not permitted to access the forecast.");
    }
}
