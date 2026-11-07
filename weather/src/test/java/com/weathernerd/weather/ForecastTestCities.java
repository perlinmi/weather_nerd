package com.weathernerd.weather;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weathernerd.weather.domain.City;
import com.weathernerd.weather.domain.CityCatalogue;
import java.util.List;
import java.util.stream.Stream;

public final class ForecastTestCities {
    public static final List<City> ALL = loadCities();

    private ForecastTestCities() {}

    public static Stream<City> stream() {
        return ALL.stream();
    }

    private static List<City> loadCities() {
        CityCatalogue catalogue = new CityCatalogue(new ObjectMapper().findAndRegisterModules());
        return List.of("berlin", "madrid", "ljubljana", "malmo", "gdansk").stream()
                .map(cityId -> catalogue.findById(cityId).orElseThrow())
                .toList();
    }
}
