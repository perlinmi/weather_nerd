package com.weathernerd.weather.api;

import java.time.LocalDate;
import java.util.List;

public record CityCatalogueResponse(
        String source,
        LocalDate snapshotRetrievedAt,
        List<CityCatalogueItem> cities) {
    public CityCatalogueResponse {
        cities = List.copyOf(cities);
    }
}
