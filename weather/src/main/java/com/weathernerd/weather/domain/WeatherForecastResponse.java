package com.weathernerd.weather.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

public record WeatherForecastResponse(
        String city,
        String country,
        String timezone,
        String source,
        Instant fetchedAt,
        boolean stale,
        List<DailyForecast> forecast,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<RainForecastDay> rainForecast) {
    public WeatherForecastResponse {
        forecast = List.copyOf(forecast);
        rainForecast = rainForecast == null ? null : List.copyOf(rainForecast);
    }

    public WeatherForecastResponse withStale(boolean stale) {
        return new WeatherForecastResponse(
                city, country, timezone, source, fetchedAt, stale, forecast, rainForecast);
    }
}
