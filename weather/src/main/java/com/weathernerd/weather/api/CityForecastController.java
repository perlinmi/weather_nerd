package com.weathernerd.weather.api;

import com.weathernerd.weather.domain.WeatherForecastResponse;
import com.weathernerd.weather.service.CityForecastService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CityForecastController {
    private final CityForecastService forecastService;

    public CityForecastController(CityForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping("/weather/forecast/{cityId}")
    public WeatherForecastResponse getForecast(
            @PathVariable String cityId,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return forecastService.getForecast(cityId, userAgent);
    }
}
