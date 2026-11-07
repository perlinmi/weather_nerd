package com.weathernerd.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weathernerd.weather.client.OpenMeteoClient;
import com.weathernerd.weather.client.PermissionsClient;
import com.weathernerd.weather.client.PermissionsServiceException;
import com.weathernerd.weather.client.ProviderForecast;
import com.weathernerd.weather.client.ProviderResponseException;
import com.weathernerd.weather.domain.City;
import com.weathernerd.weather.domain.CityCatalogue;
import com.weathernerd.weather.domain.DailyForecast;
import com.weathernerd.weather.domain.WeatherForecastResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CityForecastServiceTest {
    @Mock
    private PermissionsClient permissionsClient;

    @Mock
    private OpenMeteoClient providerClient;

    private MutableClock clock;
    private CityCatalogue catalogue;
    private CityForecastService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-07T10:00:00Z"));
        catalogue = new CityCatalogue(new ObjectMapper().findAndRegisterModules());
        service = new CityForecastService(catalogue, permissionsClient, providerClient, clock);
    }

    @Test
    void cachesEachCityIndependentlyAndChecksPermissionBeforeEveryResponse() {
        City berlin = city("berlin");
        City madrid = city("madrid");
        when(permissionsClient.isGranted("Chrome/1.0")).thenReturn(true);
        when(providerClient.fetchForecast(berlin))
                .thenReturn(providerForecast(LocalDate.parse("2026-11-07")));
        when(providerClient.fetchForecast(madrid))
                .thenReturn(providerForecast(LocalDate.parse("2026-11-07")));

        service.getForecast("berlin", "Chrome/1.0");
        WeatherForecastResponse cachedBerlin = service.getForecast("berlin", "Chrome/1.0");
        service.getForecast("madrid", "Chrome/1.0");

        assertThat(cachedBerlin.city()).isEqualTo("Berlin");
        assertThat(cachedBerlin.stale()).isFalse();
        verify(permissionsClient, org.mockito.Mockito.times(3)).isGranted("Chrome/1.0");
        verify(providerClient).fetchForecast(berlin);
        verify(providerClient).fetchForecast(madrid);
    }

    @Test
    void rejectsUnknownCityBeforeCheckingPermissionOrCallingProvider() {
        assertThatThrownBy(() -> service.getForecast("not-a-city", "Chrome/1.0"))
                .isInstanceOf(UnknownCityException.class);

        verify(permissionsClient, org.mockito.Mockito.never()).isGranted("Chrome/1.0");
        verify(providerClient, org.mockito.Mockito.never()).fetchForecast(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void neverReturnsCachedDataWhenPermissionIsDenied() {
        City berlin = city("berlin");
        when(permissionsClient.isGranted("Chrome/1.0")).thenReturn(true, false);
        when(providerClient.fetchForecast(berlin))
                .thenReturn(providerForecast(LocalDate.parse("2026-11-07")));
        service.getForecast("berlin", "Chrome/1.0");

        assertThatThrownBy(() -> service.getForecast("berlin", "Chrome/1.0"))
                .isInstanceOf(ForecastAccessDeniedException.class);

        verify(providerClient).fetchForecast(berlin);
    }

    @Test
    void refreshesCacheAfterOneHour() {
        City berlin = city("berlin");
        when(permissionsClient.isGranted("Chrome/1.0")).thenReturn(true);
        when(providerClient.fetchForecast(berlin))
                .thenReturn(providerForecast(LocalDate.parse("2026-11-07")))
                .thenReturn(providerForecast(LocalDate.parse("2026-11-07")));
        service.getForecast("berlin", "Chrome/1.0");
        clock.setInstant(Instant.parse("2026-11-07T11:00:01Z"));

        service.getForecast("berlin", "Chrome/1.0");

        verify(providerClient, org.mockito.Mockito.times(2)).fetchForecast(berlin);
    }

    @Test
    void failsClosedWhenPermissionServiceIsUnavailable() {
        when(permissionsClient.isGranted("Chrome/1.0"))
                .thenThrow(new PermissionsServiceException("unavailable"));

        assertThatThrownBy(() -> service.getForecast("berlin", "Chrome/1.0"))
                .isInstanceOf(PermissionsServiceException.class);

        verify(providerClient, org.mockito.Mockito.never()).fetchForecast(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void refreshesWhenTheCityLocalDateWindowChanges() {
        City berlin = city("berlin");
        clock.setInstant(Instant.parse("2026-11-07T23:30:00Z"));
        when(permissionsClient.isGranted("Safari/1.0")).thenReturn(true);
        when(providerClient.fetchForecast(berlin))
                .thenReturn(providerForecast(LocalDate.parse("2026-11-08")))
                .thenThrow(new ProviderResponseException("Provider unavailable"));
        service.getForecast("berlin", "Safari/1.0");
        clock.setInstant(Instant.parse("2026-11-08T23:30:00Z"));

        WeatherForecastResponse stale = service.getForecast("berlin", "Safari/1.0");

        assertThat(stale.stale()).isTrue();
        assertThat(stale.forecast().get(0).date()).isEqualTo(LocalDate.parse("2026-11-08"));
    }

    @Test
    void coalescesConcurrentRefreshesForTheSameCity() throws Exception {
        City berlin = city("berlin");
        when(permissionsClient.isGranted("Chrome/1.0")).thenReturn(true);
        CountDownLatch providerStarted = new CountDownLatch(1);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        AtomicInteger providerCalls = new AtomicInteger();
        doAnswer(invocation -> {
                    providerCalls.incrementAndGet();
                    providerStarted.countDown();
                    if (!releaseProvider.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to release provider mock.");
                    }
                    return providerForecast(LocalDate.parse("2026-11-07"));
                })
                .when(providerClient)
                .fetchForecast(berlin);

        CompletableFuture<WeatherForecastResponse> first =
                CompletableFuture.supplyAsync(() -> service.getForecast("berlin", "Chrome/1.0"));
        assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<WeatherForecastResponse> second =
                CompletableFuture.supplyAsync(() -> service.getForecast("berlin", "Chrome/1.0"));
        releaseProvider.countDown();

        assertThat(first.get(5, TimeUnit.SECONDS).forecast()).hasSize(3);
        assertThat(second.get(5, TimeUnit.SECONDS).forecast()).hasSize(3);
        assertThat(providerCalls.get()).isEqualTo(1);
    }

    @Test
    void allowsDifferentCitiesToRefreshIndependently() throws Exception {
        City berlin = city("berlin");
        City madrid = city("madrid");
        when(permissionsClient.isGranted("Chrome/1.0")).thenReturn(true);
        CountDownLatch bothStarted = new CountDownLatch(2);
        doAnswer(invocation -> {
                    bothStarted.countDown();
                    return providerForecast(LocalDate.parse("2026-11-07"));
                })
                .when(providerClient)
                .fetchForecast(org.mockito.ArgumentMatchers.any(City.class));

        CompletableFuture<WeatherForecastResponse> berlinResult =
                CompletableFuture.supplyAsync(() -> service.getForecast("berlin", "Chrome/1.0"));
        CompletableFuture<WeatherForecastResponse> madridResult =
                CompletableFuture.supplyAsync(() -> service.getForecast("madrid", "Chrome/1.0"));

        assertThat(bothStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(berlinResult.get(5, TimeUnit.SECONDS).city()).isEqualTo("Berlin");
        assertThat(madridResult.get(5, TimeUnit.SECONDS).city()).isEqualTo("Madrid");
        verify(providerClient).fetchForecast(berlin);
        verify(providerClient).fetchForecast(madrid);
    }

    private City city(String cityId) {
        return catalogue.findById(cityId).orElseThrow();
    }

    private static ProviderForecast providerForecast(LocalDate firstDate) {
        return new ProviderForecast(
                Instant.parse("2026-11-07T10:00:00Z"),
                List.of(
                        daily(firstDate),
                        daily(firstDate.plusDays(1)),
                        daily(firstDate.plusDays(2))));
    }

    private static DailyForecast daily(LocalDate date) {
        return new DailyForecast(
                date,
                new BigDecimal("4.2"),
                new BigDecimal("10.1"),
                "Overcast",
                3,
                20,
                new BigDecimal("4.5"));
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
