package com.weathernerd.weather;

import com.weathernerd.weather.domain.City;
import com.weathernerd.weather.domain.CityCatalogue;
import java.util.List;
import java.util.stream.Stream;
import tools.jackson.databind.json.JsonMapper;

public final class ForecastTestCities {
    public static final List<City> ALL = loadCities();

    private ForecastTestCities() {}

    public static Stream<City> stream() {
        return ALL.stream();
    }

    private static List<City> loadCities() {
        CityCatalogue catalogue = new CityCatalogue(JsonMapper.builder().findAndAddModules().build());
        return List.of("berlin", "madrid", "ljubljana", "malmo", "gdansk").stream()
                .map(cityId -> catalogue.findById(cityId).orElseThrow())
                .toList();
    }
}
