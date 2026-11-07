package com.weathernerd.weather.service;

public class ProviderQuotaExceededException extends RuntimeException {
    public ProviderQuotaExceededException() {
        super("The daily forecast provider request quota has been exhausted.");
    }
}
