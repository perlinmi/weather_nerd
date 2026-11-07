package com.weathernerd.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weathernerd.weather.ForecastTestCities;
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
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
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
    void cachesEachRepresentativeCityIndependentlyAndChecksPermissionBeforeEveryResponse() {
        when(permissionsClient.isGranted("Chrome/1.0")).thenReturn(true);
        ForecastTestCities.ALL.forEach(city -> when(providerClient.fetchForecast(city))
                .thenReturn(providerForecast(LocalDate.parse("2026-11-07"))));

        ForecastTestCities.ALL.forEach(city -> {
            service.getForecast(city.cityId(), "Chrome/1.0");
            WeatherForecastResponse cached = service.getForecast(city.cityId(), "Chrome/1.0");

            assertThat(cached.city()).isEqualTo(city.city());
            assertThat(cached.stale()).isFalse();
        });

        verify(permissionsClient, org.mockito.Mockito.times(ForecastTestCities.ALL.size() * 2))
                .isGranted("Chrome/1.0");
        ForecastTestCities.ALL.forEach(city -> verify(providerClient).fetchForecast(city));
    }

    @Test
    void rejectsUnknownCityBeforeCheckingPermissionOrCallingProvider() {
        assertThatThrownBy(() -> service.getForecast("not-a-city", "Chrome/1.0"))
                .isInstanceOf(UnknownCityException.class);

        verify(permissionsClient, org.mockito.Mockito.never()).isGranted("Chrome/1.0");
        verify(providerClient, org.mockito.Mockito.never()).fetchForecast(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @MethodSource("com.weathernerd.weather.ForecastTestCities#stream")
    void neverReturnsCachedDataWhenPermissionIsDenied(City city) {
        when(permissionsClient.isGranted("Chrome/1.0")).thenReturn(true, false);
        when(providerClient.fetchForecast(city))
                .thenReturn(providerForecast(LocalDate.parse("2026-11-07")));
        service.getForecast(city.cityId(), "Chrome/1.0");

        assertThatThrownBy(() -> service.getForecast(city.cityId(), "Chrome/1.0"))
                .isInstanceOf(ForecastAccessDeniedException.class);

        verify(providerClient).fetchForecast(city);
    }

    @ParameterizedTest
    @MethodSource("com.weathernerd.weather.ForecastTestCities#stream")
    void refreshesCacheAfterOneHour(City city) {
        when(permissionsClient.isGranted("Chrome/1.0")).thenReturn(true);
        when(providerClient.fetchForecast(city))
                .thenReturn(providerForecast(LocalDate.parse("2026-11-07")))
                .thenReturn(providerForecast(LocalDate.parse("2026-11-07")));
        service.getForecast(city.cityId(), "Chrome/1.0");
        clock.setInstant(Instant.parse("2026-11-07T11:00:01Z"));

        service.getForecast(city.cityId(), "Chrome/1.0");

        verify(providerClient, org.mockito.Mockito.times(2)).fetchForecast(city);
    }

    @ParameterizedTest
    @MethodSource("com.weathernerd.weather.ForecastTestCities#stream")
    void failsClosedWhenPermissionServiceIsUnavailable(City city) {
        when(permissionsClient.isGranted("Chrome/1.0"))
                .thenThrow(new PermissionsServiceException("unavailable"));

        assertThatThrownBy(() -> service.getForecast(city.cityId(), "Chrome/1.0"))
                .isInstanceOf(PermissionsServiceException.class);

        verify(providerClient, org.mockito.Mockito.never()).fetchForecast(org.mockito.ArgumentMatchers.any());
    }

    @ParameterizedTest
    @MethodSource("com.weathernerd.weather.ForecastTestCities#stream")
    void refreshesWhenTheCityLocalDateWindowChanges(City city) {
        clock.setInstant(Instant.parse("2026-11-07T23:30:00Z"));
        when(permissionsClient.isGranted("Safari/1.0")).thenReturn(true);
        when(providerClient.fetchForecast(city))
                .thenReturn(providerForecast(LocalDate.parse("2026-11-08")))
                .thenThrow(new ProviderResponseException("Provider unavailable"));
        service.getForecast(city.cityId(), "Safari/1.0");
        clock.setInstant(Instant.parse("2026-11-08T23:30:00Z"));

        WeatherForecastResponse stale = service.getForecast(city.cityId(), "Safari/1.0");

        assertThat(stale.stale()).isTrue();
        assertThat(stale.forecast().get(0).date()).isEqualTo(LocalDate.parse("2026-11-08"));
    }

    @ParameterizedTest
    @MethodSource("com.weathernerd.weather.ForecastTestCities#stream")
    void coalescesConcurrentRefreshesForTheSameCity(City city) throws Exception {
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
                .fetchForecast(city);

        CompletableFuture<WeatherForecastResponse> first =
                CompletableFuture.supplyAsync(() -> service.getForecast(city.cityId(), "Chrome/1.0"));
        assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<WeatherForecastResponse> second =
                CompletableFuture.supplyAsync(() -> service.getForecast(city.cityId(), "Chrome/1.0"));
        releaseProvider.countDown();

        assertThat(first.get(5, TimeUnit.SECONDS).forecast()).hasSize(3);
        assertThat(second.get(5, TimeUnit.SECONDS).forecast()).hasSize(3);
        assertThat(providerCalls.get()).isEqualTo(1);
    }

    @Test
    void allowsAllRepresentativeCitiesToRefreshIndependently() throws Exception {
        when(permissionsClient.isGranted("Chrome/1.0")).thenReturn(true);
        CountDownLatch allStarted = new CountDownLatch(ForecastTestCities.ALL.size());
        doAnswer(invocation -> {
                    allStarted.countDown();
                    return providerForecast(LocalDate.parse("2026-11-07"));
                })
                .when(providerClient)
                .fetchForecast(org.mockito.ArgumentMatchers.any(City.class));

        Map<City, CompletableFuture<WeatherForecastResponse>> results =
                ForecastTestCities.ALL.stream().collect(Collectors.toMap(
                        city -> city,
                        city -> CompletableFuture.supplyAsync(
                                () -> service.getForecast(city.cityId(), "Chrome/1.0"))));

        assertThat(allStarted.await(5, TimeUnit.SECONDS)).isTrue();
        for (Map.Entry<City, CompletableFuture<WeatherForecastResponse>> entry : results.entrySet()) {
                City city = entry.getKey();
                CompletableFuture<WeatherForecastResponse> result = entry.getValue();
                assertThat(result.get(5, TimeUnit.SECONDS).city()).isEqualTo(city.city());
        }
        ForecastTestCities.ALL.forEach(city -> verify(providerClient).fetchForecast(city));
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
