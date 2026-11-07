package com.weathernerd.weather.api;

import com.weathernerd.weather.domain.CityCatalogue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CityCatalogueController {
    private final CityCatalogue catalogue;

    public CityCatalogueController(CityCatalogue catalogue) {
        this.catalogue = catalogue;
    }

    @GetMapping("/weather/cities")
    public CityCatalogueResponse getCities() {
        return new CityCatalogueResponse(
                catalogue.source(),
                catalogue.snapshotRetrievedAt(),
                catalogue.cities().stream().map(CityCatalogueItem::from).toList());
    }
}
