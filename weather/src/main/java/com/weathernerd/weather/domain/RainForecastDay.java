package com.weathernerd.weather.domain;

import java.time.LocalDate;

public record RainForecastDay(LocalDate date, int probability) {
    public RainForecastDay {
        if (date == null) {
            throw new IllegalArgumentException("Rain forecast date must not be null.");
        }
        if (probability < 0 || probability > 100) {
            throw new IllegalArgumentException("Rain probability must be between 0 and 100.");
        }
    }
}
