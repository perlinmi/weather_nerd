package com.weathernerd.weather.api;

import com.weathernerd.weather.domain.City;

public record CityCatalogueItem(
        String cityId,
        String city,
        String country,
        double latitude,
        double longitude,
        String timezone,
        int rank,
        long population,
        int populationAsOfYear) {

    public static CityCatalogueItem from(City city) {
        return new CityCatalogueItem(
                city.cityId(),
                city.city(),
                city.country(),
                city.latitude(),
                city.longitude(),
                city.timezone(),
                city.rank(),
                city.population(),
                city.populationAsOfYear());
    }
}
