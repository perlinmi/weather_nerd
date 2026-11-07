package com.weathernerd.weather.domain;

import java.time.Instant;
import java.util.List;

public record WeatherForecastResponse(
        String city,
        String country,
        String timezone,
        String source,
        Instant fetchedAt,
        boolean stale,
        List<DailyForecast> forecast) {
    public WeatherForecastResponse {
        forecast = List.copyOf(forecast);
    }

    public WeatherForecastResponse withStale(boolean stale) {
        return new WeatherForecastResponse(city, country, timezone, source, fetchedAt, stale, forecast);
    }
}
