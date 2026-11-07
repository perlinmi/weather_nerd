package com.weathernerd.weather.api;

import com.weathernerd.weather.domain.WeatherForecastResponse;
import com.weathernerd.weather.service.BerlinForecastService;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BerlinForecastController {
    private final BerlinForecastService forecastService;

    public BerlinForecastController(BerlinForecastService forecastService) {
        this.forecastService = forecastService;
    }

    @GetMapping("/weather/forecast/berlin")
    public WeatherForecastResponse getBerlinForecast(
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        return forecastService.getForecast(userAgent);
    }
}
