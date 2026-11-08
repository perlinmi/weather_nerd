package com.weathernerd.weather.exception;

public class ProviderQuotaExceededException extends RuntimeException {
    public ProviderQuotaExceededException() {
        super("The daily forecast provider request quota has been exhausted.");
    }
}
