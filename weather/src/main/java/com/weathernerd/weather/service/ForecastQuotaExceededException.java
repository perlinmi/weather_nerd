package com.weathernerd.weather.service;

public class ForecastQuotaExceededException extends RuntimeException {
    public ForecastQuotaExceededException() {
        super("The daily forecast provider request quota has been exhausted.");
    }
}
