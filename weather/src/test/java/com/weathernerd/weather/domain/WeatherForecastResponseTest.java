package com.weathernerd.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.weathernerd.weather.ForecastTestCities;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.boot.test.json.JacksonTester;

@JsonTest
class WeatherForecastResponseTest {
    @Autowired
    private JacksonTester<WeatherForecastResponse> json;

    @ParameterizedTest
    @MethodSource("com.weathernerd.weather.ForecastTestCities#stream")
    void serializesTheContractFieldsAndThreeDailyEntries(City city) throws Exception {
        WeatherForecastResponse response = new WeatherForecastResponse(
                city.city(),
                city.country(),
                city.timezone(),
                "Open-Meteo DWD API",
                Instant.parse("2026-11-07T10:00:00Z"),
                false,
                List.of(
                        daily("2026-11-07"),
                        daily("2026-11-08"),
                        daily("2026-11-09")),
                city.hasRainForecast()
                        ? List.of(
                                new RainForecastDay(LocalDate.parse("2026-11-07"), 20),
                                new RainForecastDay(LocalDate.parse("2026-11-08"), 60),
                                new RainForecastDay(LocalDate.parse("2026-11-09"), 10))
                        : null);

        assertThat(json.write(response))
                .extractingJsonPathStringValue("@.city").isEqualTo(city.city());
        assertThat(json.write(response))
                .extractingJsonPathStringValue("@.country").isEqualTo(city.country());
        assertThat(json.write(response))
                .extractingJsonPathStringValue("@.timezone").isEqualTo(city.timezone());
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
        assertThat(json.write(response).getJson()).doesNotContain("precipitationProbability");
        if (city.hasRainForecast()) {
            assertThat(json.write(response))
                    .extractingJsonPathNumberValue("@.rainForecast[0].probability")
                    .isEqualTo(20);
            assertThat(json.write(response))
                    .extractingJsonPathStringValue("@.rainForecast[0].date")
                    .isEqualTo("2026-11-07");
            assertThat(json.write(response))
                    .extractingJsonPathArrayValue("@.rainForecast")
                    .hasSize(3);
        } else {
            assertThat(json.write(response).getJson()).doesNotContain("\"rainForecast\"");
        }
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
                new BigDecimal("4.5"));
    }
}
