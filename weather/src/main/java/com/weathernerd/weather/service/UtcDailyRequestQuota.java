package com.weathernerd.weather.service;

import com.weathernerd.weather.exception.ProviderQuotaExceededException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

@Component
public class UtcDailyRequestQuota implements ProviderRequestQuota {
    private static final int DAILY_LIMIT = 500;

    private final Clock clock;
    private LocalDate currentUtcDate;
    private int reservedToday;

    public UtcDailyRequestQuota(Clock clock) {
        this.clock = clock;
        this.currentUtcDate = LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }

    @Override
    public synchronized void reserve() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!today.equals(currentUtcDate)) {
            currentUtcDate = today;
            reservedToday = 0;
        }
        if (reservedToday >= DAILY_LIMIT) {
            throw new ProviderQuotaExceededException();
        }
        reservedToday++;
    }

    public synchronized int reservedToday() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!today.equals(currentUtcDate)) {
            currentUtcDate = today;
            reservedToday = 0;
        }
        return reservedToday;
    }
}
