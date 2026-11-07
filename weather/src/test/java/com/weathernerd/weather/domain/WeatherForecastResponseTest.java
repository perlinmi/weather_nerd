package com.weathernerd.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

@JsonTest
class WeatherForecastResponseTest {
    @Autowired
    private JacksonTester<WeatherForecastResponse> json;

    @Test
    void serializesTheContractFieldsAndThreeDailyEntries() throws Exception {
        WeatherForecastResponse response = new WeatherForecastResponse(
                "Berlin",
                "Germany",
                "Europe/Berlin",
                "Open-Meteo DWD API",
                Instant.parse("2026-11-07T10:00:00Z"),
                false,
                List.of(
                        daily("2026-11-07"),
                        daily("2026-11-08"),
                        daily("2026-11-09")));

        assertThat(json.write(response))
                .extractingJsonPathStringValue("@.city").isEqualTo("Berlin");
        assertThat(json.write(response))
                .extractingJsonPathStringValue("@.country").isEqualTo("Germany");
        assertThat(json.write(response))
                .extractingJsonPathStringValue("@.timezone").isEqualTo("Europe/Berlin");
        assertThat(json.write(response))
                .extractingJsonPathStringValue("@.source").isEqualTo("Open-Meteo DWD API");
        assertThat(json.write(response))
                .extractingJsonPathStringValue("@.fetchedAt").isEqualTo("2026-11-07T10:00:00Z");
        assertThat(json.write(response)).extractingJsonPathBooleanValue("@.stale").isFalse();
        assertThat(json.write(response)).extractingJsonPathArrayValue("@.forecast").hasSize(3);
        assertThat(json.write(response))
                .extractingJsonPathNumberValue("@.forecast[0].minimumTemperatureCelsius")
                .isEqualTo(4.2);
        assertThat(json.write(response))
                .extractingJsonPathNumberValue("@.forecast[0].maximumTemperatureCelsius")
                .isEqualTo(10.1);
        assertThat(json.write(response))
                .extractingJsonPathStringValue("@.forecast[0].summary").isEqualTo("Overcast");
        assertThat(json.write(response))
                .extractingJsonPathNumberValue("@.forecast[0].weatherCode").isEqualTo(3);
        assertThat(json.write(response))
                .extractingJsonPathNumberValue("@.forecast[0].precipitationProbability")
                .isEqualTo(20);
        assertThat(json.write(response))
                .extractingJsonPathNumberValue("@.forecast[0].windSpeedMetersPerSecond")
                .isEqualTo(4.5);
    }

    private static DailyForecast daily(String date) {
        return new DailyForecast(
                LocalDate.parse(date),
                new BigDecimal("4.2"),
                new BigDecimal("10.1"),
                "Overcast",
                3,
                20,
                new BigDecimal("4.5"));
    }
}
