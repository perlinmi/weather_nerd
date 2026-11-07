package com.weathernerd.weather.domain;

import java.time.DateTimeException;
import java.time.ZoneId;

public record City(
        String cityId,
        String city,
        String country,
        double latitude,
        double longitude,
        String timezone,
        int rank,
        long population,
        int populationAsOfYear) {

    public City {
        requireText(cityId, "cityId");
        requireText(city, "city");
        requireText(country, "country");
        requireText(timezone, "timezone");
        if (!cityId.matches("[a-z0-9]+(?:-[a-z0-9]+)*")) {
            throw new IllegalArgumentException("cityId must be a lowercase ASCII slug.");
        }
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude must be between -90 and 90.");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude must be between -180 and 180.");
        }
        if (rank < 1 || population < 1 || populationAsOfYear < 1900) {
            throw new IllegalArgumentException("City rank and population metadata must be positive.");
        }
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("timezone must be a valid IANA timezone.", exception);
        }
    }

    public boolean hasRainForecast() {
        return RainForecastEligibility.isEligible(city);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank.");
        }
    }

}
