package com.weathernerd.weather.domain;

public final class RainForecastEligibility {
    private static final long MAXIMUM_LETTERS = 7;

    private RainForecastEligibility() {}

    public static boolean isEligible(String cityName) {
        return cityName.codePoints()
                        .filter(Character::isLetter)
                        .count()
                < MAXIMUM_LETTERS;
    }
}
