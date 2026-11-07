package com.weathernerd.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WeatherCodeSummaryTest {
    private final WeatherCodeSummary summaries = new WeatherCodeSummary();

    @Test
    void mapsRepresentativeWmoCodesToEnglishSummaries() {
        assertThat(summaries.describe(0)).isEqualTo("Clear sky");
        assertThat(summaries.describe(2)).isEqualTo("Partly cloudy");
        assertThat(summaries.describe(45)).isEqualTo("Fog");
        assertThat(summaries.describe(51)).isEqualTo("Light drizzle");
        assertThat(summaries.describe(61)).isEqualTo("Slight rain");
        assertThat(summaries.describe(71)).isEqualTo("Slight snowfall");
        assertThat(summaries.describe(80)).isEqualTo("Slight rain showers");
        assertThat(summaries.describe(95)).isEqualTo("Thunderstorm");
    }

    @Test
    void mapsUnrecognizedCodesToUnknown() {
        assertThat(summaries.describe(-1)).isEqualTo("Unknown");
        assertThat(summaries.describe(999)).isEqualTo("Unknown");
    }
}
