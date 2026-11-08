package com.weathernerd.weather.service;

import com.weathernerd.weather.client.OpenMeteoClient;
import com.weathernerd.weather.client.PermissionsClient;
import com.weathernerd.weather.client.ProviderForecast;
import com.weathernerd.weather.domain.City;
import com.weathernerd.weather.domain.CityCatalogue;
import com.weathernerd.weather.domain.WeatherForecastResponse;
import com.weathernerd.weather.exception.ForecastAccessDeniedException;
import com.weathernerd.weather.exception.ForecastQuotaExceededException;
import com.weathernerd.weather.exception.ForecastUnavailableException;
import com.weathernerd.weather.exception.ProviderQuotaExceededException;
import com.weathernerd.weather.exception.ProviderResponseException;
import com.weathernerd.weather.exception.UnknownCityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CityForecastService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CityForecastService.class);
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final CityCatalogue catalogue;
    private final PermissionsClient permissionsClient;
    private final OpenMeteoClient providerClient;
    private final Clock clock;
    private final Object refreshLock = new Object();
    private final Map<String, CacheEntry> cacheByCity = new HashMap<>();
    private final Map<String, CompletableFuture<CacheEntry>> inFlightByCity = new HashMap<>();

    public CityForecastService(
            CityCatalogue catalogue,
            PermissionsClient permissionsClient,
            OpenMeteoClient providerClient,
            Clock clock) {
        this.catalogue = catalogue;
        this.permissionsClient = permissionsClient;
        this.providerClient = providerClient;
        this.clock = clock;
    }

    public WeatherForecastResponse getForecast(String cityId, String originalUserAgent) {
        City city = catalogue.findById(cityId).orElseThrow(UnknownCityException::new);
        if (!permissionsClient.isGranted(originalUserAgent)) {
            throw new ForecastAccessDeniedException();
        }

        CacheEntry currentCache;
        synchronized (refreshLock) {
            currentCache = cacheByCity.get(city.cityId());
            if (isFresh(currentCache, city)) {
                return currentCache.response().withStale(false);
            }
        }
        return refreshOrJoin(city, currentCache);
    }

    private WeatherForecastResponse refreshOrJoin(City city, CacheEntry staleCandidate) {
        CompletableFuture<CacheEntry> refresh;
        boolean owner = false;
        synchronized (refreshLock) {
            CacheEntry current = cacheByCity.get(city.cityId());
            if (isFresh(current, city)) {
                return current.response().withStale(false);
            }
            if (staleCandidate == null) {
                staleCandidate = current;
            }
            refresh = inFlightByCity.get(city.cityId());
            if (refresh == null) {
                refresh = new CompletableFuture<>();
                inFlightByCity.put(city.cityId(), refresh);
                owner = true;
            }
        }

        if (owner) {
            performRefresh(city, refresh);
        }

        try {
            return refresh.join().response().withStale(false);
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (staleCandidate != null) {
                LOGGER.warn("Serving stale forecast for city {}", city.cityId(), cause);
                return staleCandidate.response().withStale(true);
            }
            if (cause instanceof ProviderQuotaExceededException) {
                throw new ForecastQuotaExceededException();
            }
            if (cause instanceof ProviderResponseException) {
                throw new ForecastUnavailableException(cause);
            }
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new ForecastUnavailableException(cause);
        }
    }

    private void performRefresh(City city, CompletableFuture<CacheEntry> refresh) {
        try {
            ProviderForecast providerForecast = providerClient.fetchForecast(city);
            Instant now = Instant.now(clock);
            WeatherForecastResponse response = new WeatherForecastResponse(
                    city.city(),
                    city.country(),
                    city.timezone(),
                    "Open-Meteo DWD API",
                    providerForecast.fetchedAt(),
                    false,
                    providerForecast.dailyForecast(),
                    city.hasRainForecast() ? providerForecast.rainForecast() : null);
            CacheEntry newCache = new CacheEntry(
                    response,
                    now.plus(CACHE_TTL),
                    providerForecast.dailyForecast().stream()
                            .map(dailyForecast -> dailyForecast.date())
                            .toList());
            synchronized (refreshLock) {
                cacheByCity.put(city.cityId(), newCache);
            }
            refresh.complete(newCache);
        } catch (RuntimeException exception) {
            refresh.completeExceptionally(exception);
        } finally {
            synchronized (refreshLock) {
                if (inFlightByCity.get(city.cityId()) == refresh) {
                    inFlightByCity.remove(city.cityId());
                }
            }
        }
    }

    private boolean isFresh(CacheEntry candidate, City city) {
        return candidate != null
                && Instant.now(clock).isBefore(candidate.expiresAt())
                && candidate.forecastDates().equals(datesForToday(city));
    }

    private List<LocalDate> datesForToday(City city) {
        ZoneId timezone = ZoneId.of(city.timezone());
        LocalDate today = LocalDate.now(clock.withZone(timezone));
        return List.of(today, today.plusDays(1), today.plusDays(2));
    }

    private record CacheEntry(
            WeatherForecastResponse response, Instant expiresAt, List<LocalDate> forecastDates) {
    }
}
