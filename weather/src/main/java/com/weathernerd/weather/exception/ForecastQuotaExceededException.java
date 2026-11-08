package com.weathernerd.weather.exception;

public class ForecastQuotaExceededException extends RuntimeException {
    public ForecastQuotaExceededException() {
        super("The daily forecast provider request quota has been exhausted.");
    }
}
