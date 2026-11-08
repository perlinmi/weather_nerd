package com.weathernerd.weather.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

public record NearbyCityItem(
        String cityId,
        String city,
        String country,
        double distanceKm,
        @JsonInclude(JsonInclude.Include.NON_NULL) WeatherForecastResponse forecast) {
}
