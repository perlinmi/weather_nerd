package com.weathernerd.weather.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyForecast(
        LocalDate date,
        BigDecimal minimumTemperatureCelsius,
        BigDecimal maximumTemperatureCelsius,
        String summary,
        int weatherCode,
        BigDecimal windSpeedMetersPerSecond) {
}
