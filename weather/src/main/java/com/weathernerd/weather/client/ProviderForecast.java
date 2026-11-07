package com.weathernerd.weather.client;

import com.weathernerd.weather.domain.DailyForecast;
import java.time.Instant;
import java.util.List;

public record ProviderForecast(Instant fetchedAt, List<DailyForecast> dailyForecast) {
    public ProviderForecast {
        dailyForecast = List.copyOf(dailyForecast);
    }
}
