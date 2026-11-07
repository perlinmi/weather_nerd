package com.weathernerd.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.weathernerd.weather.ForecastTestCities;
import org.junit.jupiter.api.Test;

class RainForecastEligibilityTest {
    @Test
    void countsUnicodeLettersAndExcludesNamesWithSevenOrMoreLetters() {
        assertThat(RainForecastEligibility.isEligible("Berlin")).isTrue();
        assertThat(RainForecastEligibility.isEligible("Malmö")).isTrue();
        assertThat(RainForecastEligibility.isEligible("Gdańsk")).isTrue();
        assertThat(RainForecastEligibility.isEligible("Malmo\u0308")).isTrue();
        assertThat(RainForecastEligibility.isEligible("abc-def")).isTrue();
        assertThat(RainForecastEligibility.isEligible("abc def")).isTrue();
        assertThat(RainForecastEligibility.isEligible("abc123")).isTrue();
        assertThat(RainForecastEligibility.isEligible("abcdefg")).isFalse();
        assertThat(RainForecastEligibility.isEligible("Ljubljana")).isFalse();
    }

    @Test
    void sharedForecastCitiesIncludeBothEligibleAndIneligibleExamples() {
        assertThat(ForecastTestCities.ALL.stream()
                        .filter(City::hasRainForecast)
                        .map(City::cityId)
                        .toList())
                .containsExactlyInAnyOrder("berlin", "madrid", "malmo", "gdansk");
    }
}
