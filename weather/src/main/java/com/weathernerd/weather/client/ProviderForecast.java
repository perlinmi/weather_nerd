package com.weathernerd.weather.client;

import com.weathernerd.weather.domain.DailyForecast;
import com.weathernerd.weather.domain.RainForecastDay;
import java.time.Instant;
import java.util.List;

public record ProviderForecast(
        Instant fetchedAt, List<DailyForecast> dailyForecast, List<RainForecastDay> rainForecast) {
    public ProviderForecast {
        dailyForecast = List.copyOf(dailyForecast);
        rainForecast = List.copyOf(rainForecast);
    }
}
