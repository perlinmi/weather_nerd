package com.weathernerd.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.Test;

class NearbyCitySearchTest {
    private static final double EARTH_MEAN_RADIUS_KM = 6_371.0088;

    @Test
    void includesThe200KmBoundaryAndExcludesTheOriginAndMoreDistantCities() {
        City origin = city("origin", 0.0, 0.0, 1);
        City boundary = city("boundary", latitudeAtDistance(200.0), 0.0, 2);
        City beyond = city("beyond", latitudeAtDistance(200.1), 0.0, 3);

        List<NearbyCitySearch.NearbyCity> results =
                NearbyCitySearch.within200Km(origin, List.of(origin, boundary, beyond));

        assertThat(results).extracting(result -> result.city().cityId()).containsExactly("boundary");
        assertThat(results.getFirst().distanceKm()).isCloseTo(200.0, within(1.0e-9));
    }

    @Test
    void ordersByDistanceThenCityId() {
        City origin = city("origin", 0.0, 0.0, 1);
        City farther = city("farther", 1.5, 0.0, 2);
        City tieZ = city("z-city", 1.0, 0.0, 3);
        City tieA = city("a-city", -1.0, 0.0, 4);

        List<NearbyCitySearch.NearbyCity> results =
                NearbyCitySearch.within200Km(origin, List.of(origin, farther, tieZ, tieA));

        assertThat(results).extracting(result -> result.city().cityId())
                .containsExactly("a-city", "z-city", "farther");
    }

    private static double latitudeAtDistance(double distanceKm) {
        return Math.toDegrees(distanceKm / EARTH_MEAN_RADIUS_KM);
    }

    private static City city(String cityId, double latitude, double longitude, int rank) {
        return new City(
                cityId,
                cityId,
                "Test country",
                latitude,
                longitude,
                "UTC",
                rank,
                1_000,
                2025);
    }
}
