package com.weathernerd.weather.api;

import com.weathernerd.weather.domain.NearbyCitiesResponse;
import com.weathernerd.weather.service.NearbyCityService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NearbyCityController {
    private final NearbyCityService nearbyCityService;

    public NearbyCityController(NearbyCityService nearbyCityService) {
        this.nearbyCityService = nearbyCityService;
    }

    @GetMapping("/weather/nearby/{cityId}")
    public NearbyCitiesResponse getNearbyCities(
            @PathVariable String cityId,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return nearbyCityService.getNearbyCities(cityId, userAgent);
    }
}
