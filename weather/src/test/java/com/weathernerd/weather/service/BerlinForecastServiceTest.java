package com.weathernerd.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.weathernerd.weather.client.OpenMeteoClient;
import com.weathernerd.weather.client.PermissionsClient;
import com.weathernerd.weather.client.PermissionsServiceException;
import com.weathernerd.weather.client.ProviderForecast;
import com.weathernerd.weather.client.ProviderResponseException;
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
class BerlinForecastServiceTest {
    @Mock
    private PermissionsClient permissionsClient;

    @Mock
    private OpenMeteoClient providerClient;

    private MutableClock clock;
    private BerlinForecastService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-11-07T10:00:00Z"));
        service = new BerlinForecastService(permissionsClient, providerClient, clock);
    }

    @Test
    void checksPermissionBeforeCallingProviderAndReusesFreshCache() {
        when(permissionsClient.isGranted("Chrome/1.0")).thenReturn(true);
        when(providerClient.fetchForecast()).thenReturn(providerForecast(LocalDate.parse("2026-11-07")));

        WeatherForecastResponse first = service.getForecast("Chrome/1.0");
        WeatherForecastResponse second = service.getForecast("Chrome/1.0");

        assertThat(first.stale()).isFalse();
        assertThat(second.stale()).isFalse();
        verify(permissionsClient, org.mockito.Mockito.times(2)).isGranted("Chrome/1.0");
        verify(providerClient).fetchForecast();
    }

    @Test
    void refreshesCacheAfterOneHour() {
        when(permissionsClient.isGranted("Chrome/1.0")).thenReturn(true);
        when(providerClient.fetchForecast())
                .thenReturn(providerForecast(LocalDate.parse("2026-11-07")))
                .thenReturn(providerForecast(LocalDate.parse("2026-11-07")));
        service.getForecast("Chrome/1.0");
        clock.setInstant(Instant.parse("2026-11-07T11:00:01Z"));

        service.getForecast("Chrome/1.0");

        verify(providerClient, org.mockito.Mockito.times(2)).fetchForecast();
    }

    @Test
    void doesNotReturnCachedForecastWhenPermissionIsDenied() {
        when(permissionsClient.isGranted("Chrome/1.0")).thenReturn(true, false);
        when(providerClient.fetchForecast()).thenReturn(providerForecast(LocalDate.parse("2026-11-07")));
        service.getForecast("Chrome/1.0");

        assertThatThrownBy(() -> service.getForecast("Chrome/1.0"))
                .isInstanceOf(ForecastAccessDeniedException.class);
        verify(providerClient).fetchForecast();
    }

    @Test
    void servesExpiredDateWindowAsStaleAfterRefreshFailure() {
        when(permissionsClient.isGranted("Safari/1.0")).thenReturn(true);
        when(providerClient.fetchForecast())
                .thenReturn(providerForecast(LocalDate.parse("2026-11-07")))
                .thenThrow(new ProviderResponseException("Provider unavailable"));
        WeatherForecastResponse initial = service.getForecast("Safari/1.0");
        clock.setInstant(Instant.parse("2026-11-07T23:00:00Z"));

        WeatherForecastResponse stale = service.getForecast("Safari/1.0");

        assertThat(initial.stale()).isFalse();
        assertThat(stale.stale()).isTrue();
        assertThat(stale.fetchedAt()).isEqualTo(initial.fetchedAt());
        assertThat(stale.forecast().getFirst().date()).isEqualTo(LocalDate.parse("2026-11-07"));
    }

    @Test
    void returns429WhenQuotaIsExhaustedAndNoCacheExists() {
        when(permissionsClient.isGranted(null)).thenReturn(true);
        when(providerClient.fetchForecast()).thenThrow(new ProviderQuotaExceededException());

        assertThatThrownBy(() -> service.getForecast(null))
                .isInstanceOf(ForecastQuotaExceededException.class);
    }

    @Test
    void returns502WhenProviderFailsAndNoCacheExists() {
        when(permissionsClient.isGranted("Chrome/1.0")).thenReturn(true);
        when(providerClient.fetchForecast()).thenThrow(new ProviderResponseException("bad response"));

        assertThatThrownBy(() -> service.getForecast("Chrome/1.0"))
                .isInstanceOf(ForecastUnavailableException.class);
    }

    @Test
    void servesStaleCacheWhenProviderQuotaIsExhausted() {
        when(permissionsClient.isGranted("Chrome/1.0")).thenReturn(true);
        when(providerClient.fetchForecast())
                .thenReturn(providerForecast(LocalDate.parse("2026-11-07")))
                .thenThrow(new ProviderQuotaExceededException());
        WeatherForecastResponse initial = service.getForecast("Chrome/1.0");
        clock.setInstant(Instant.parse("2026-11-07T11:00:01Z"));

        WeatherForecastResponse stale = service.getForecast("Chrome/1.0");

        assertThat(stale.stale()).isTrue();
        assertThat(stale.fetchedAt()).isEqualTo(initial.fetchedAt());
    }

    @Test
    void doesNotCallProviderWhenPermissionsServiceIsUnavailable() {
        when(permissionsClient.isGranted("Chrome/1.0"))
                .thenThrow(new PermissionsServiceException("unavailable"));

        assertThatThrownBy(() -> service.getForecast("Chrome/1.0"))
                .isInstanceOf(PermissionsServiceException.class);
        verify(providerClient, org.mockito.Mockito.never()).fetchForecast();
    }

    @Test
    void coalescesConcurrentRefreshes() throws Exception {
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
                .fetchForecast();

        CompletableFuture<WeatherForecastResponse> first =
                CompletableFuture.supplyAsync(() -> service.getForecast("Chrome/1.0"));
        assertThat(providerStarted.await(5, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<WeatherForecastResponse> second =
                CompletableFuture.supplyAsync(() -> service.getForecast("Chrome/1.0"));
        releaseProvider.countDown();

        assertThat(first.get(5, TimeUnit.SECONDS).forecast()).hasSize(3);
        assertThat(second.get(5, TimeUnit.SECONDS).forecast()).hasSize(3);
        assertThat(providerCalls.get()).isEqualTo(1);
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
