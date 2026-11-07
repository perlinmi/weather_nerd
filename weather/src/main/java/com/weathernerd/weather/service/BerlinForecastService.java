package com.weathernerd.weather.service;

import com.weathernerd.weather.client.OpenMeteoClient;
import com.weathernerd.weather.client.PermissionsClient;
import com.weathernerd.weather.client.ProviderForecast;
import com.weathernerd.weather.client.ProviderResponseException;
import com.weathernerd.weather.domain.WeatherForecastResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BerlinForecastService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BerlinForecastService.class);
    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    private final PermissionsClient permissionsClient;
    private final OpenMeteoClient providerClient;
    private final Clock clock;
    private final Object refreshLock = new Object();

    private volatile CacheEntry cache;
    private CompletableFuture<CacheEntry> inFlightRefresh;

    public BerlinForecastService(
            PermissionsClient permissionsClient, OpenMeteoClient providerClient, Clock clock) {
        this.permissionsClient = permissionsClient;
        this.providerClient = providerClient;
        this.clock = clock;
    }

    public WeatherForecastResponse getForecast(String originalUserAgent) {
        boolean granted = permissionsClient.isGranted(originalUserAgent);
        if (!granted) {
            throw new ForecastAccessDeniedException();
        }

        CacheEntry currentCache = cache;
        if (isFresh(currentCache)) {
            return currentCache.response().withStale(false);
        }

        return refreshOrJoin(currentCache);
    }

    private WeatherForecastResponse refreshOrJoin(CacheEntry staleCandidate) {
        CompletableFuture<CacheEntry> refresh;
        boolean owner = false;
        synchronized (refreshLock) {
            if (isFresh(cache)) {
                return cache.response().withStale(false);
            }
            if (inFlightRefresh == null) {
                inFlightRefresh = new CompletableFuture<>();
                owner = true;
            }
            refresh = inFlightRefresh;
            if (staleCandidate == null) {
                staleCandidate = cache;
            }
        }

        if (owner) {
            performRefresh(refresh);
        }

        try {
            return refresh.join().response().withStale(false);
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause();
            if (staleCandidate != null) {
                LOGGER.warn("Serving stale Berlin forecast after refresh failure", cause);
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

    private void performRefresh(CompletableFuture<CacheEntry> refresh) {
        try {
            ProviderForecast providerForecast = providerClient.fetchForecast();
            Instant now = Instant.now(clock);
            WeatherForecastResponse response = new WeatherForecastResponse(
                    "Berlin",
                    "Germany",
                    BERLIN.getId(),
                    "Open-Meteo DWD API",
                    providerForecast.fetchedAt(),
                    false,
                    providerForecast.dailyForecast());
            CacheEntry newCache = new CacheEntry(
                    response,
                    now.plus(CACHE_TTL),
                    providerForecast.dailyForecast().stream()
                            .map(dailyForecast -> dailyForecast.date())
                            .toList());
            cache = newCache;
            refresh.complete(newCache);
        } catch (ProviderQuotaExceededException | ProviderResponseException exception) {
            refresh.completeExceptionally(exception);
        } catch (RuntimeException exception) {
            refresh.completeExceptionally(exception);
        } finally {
            synchronized (refreshLock) {
                if (inFlightRefresh == refresh) {
                    inFlightRefresh = null;
                }
            }
        }
    }

    private boolean isFresh(CacheEntry candidate) {
        return candidate != null
                && Instant.now(clock).isBefore(candidate.expiresAt())
                && candidate.forecastDates().equals(datesForToday());
    }

    private List<LocalDate> datesForToday() {
        LocalDate today = LocalDate.now(clock.withZone(BERLIN));
        return List.of(today, today.plusDays(1), today.plusDays(2));
    }

    private record CacheEntry(
            WeatherForecastResponse response, Instant expiresAt, List<LocalDate> forecastDates) {
    }
}
