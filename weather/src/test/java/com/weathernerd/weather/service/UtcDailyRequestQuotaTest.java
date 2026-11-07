package com.weathernerd.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class UtcDailyRequestQuotaTest {
    @Test
    void allowsAtMostOneHundredOutboundRequestsPerUtcDay() {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-07T23:59:59Z"));
        UtcDailyRequestQuota quota = new UtcDailyRequestQuota(clock);

        for (int attempt = 0; attempt < 100; attempt++) {
            quota.reserve();
        }

        assertThatThrownBy(quota::reserve).isInstanceOf(ProviderQuotaExceededException.class);
        clock.setInstant(Instant.parse("2026-11-08T00:00:00Z"));
        quota.reserve();
    }

    @Test
    void concurrentReservationsNeverExceedTheDailyLimit() throws InterruptedException {
        UtcDailyRequestQuota quota = new UtcDailyRequestQuota(
                Clock.fixed(Instant.parse("2026-11-07T12:00:00Z"), ZoneOffset.UTC));
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger reserved = new AtomicInteger();
        Thread[] threads = new Thread[150];

        for (int index = 0; index < threads.length; index++) {
            threads[index] = new Thread(() -> {
                try {
                    start.await();
                    quota.reserve();
                    reserved.incrementAndGet();
                } catch (ProviderQuotaExceededException ignored) {
                    // The quota rejection is the expected result after 100 reservations.
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
            threads[index].start();
        }
        start.countDown();
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(reserved.get()).isEqualTo(100);
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
