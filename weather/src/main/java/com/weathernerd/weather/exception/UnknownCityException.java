package com.weathernerd.weather.exception;

public class UnknownCityException extends RuntimeException {
    public UnknownCityException() {
        super("The requested city is not in the catalogue.");
    }
}
