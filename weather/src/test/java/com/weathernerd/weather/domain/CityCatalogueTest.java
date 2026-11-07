package com.weathernerd.weather.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class CityCatalogueTest {
    private static final Pattern CITY_ID_PATTERN =
            Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");

    @Autowired
    private CityCatalogue catalogue;

    @Test
    void loadsAllNinetyFiveCitiesFromPinnedSnapshotInPopulationRankOrder() {
        assertThat(catalogue.cities()).hasSize(95);
        assertThat(catalogue.snapshotRetrievedAt()).isEqualTo(LocalDate.parse("2026-11-07"));
        assertThat(catalogue.source())
                .isEqualTo("https://en.wikipedia.org/wiki/List_of_cities_in_the_European_Union_by_population_within_city_limits");

        List<Integer> expectedRanks = new ArrayList<>();
        for (int rank = 1; rank <= 95; rank++) {
            expectedRanks.add(rank);
        }
        assertThat(catalogue.cities()).extracting(City::rank).containsExactlyElementsOf(expectedRanks);
    }

    @Test
    void cityRecordsHaveUniqueStableIdsValidLocationsAndCompleteProvenance() {
        List<City> cities = catalogue.cities();

        assertThat(cities).extracting(City::cityId).doesNotHaveDuplicates();
        assertThat(cities).allSatisfy(city -> {
            assertThat(CITY_ID_PATTERN.matcher(city.cityId()).matches()).isTrue();
            assertThat(city.city()).isNotBlank();
            assertThat(city.country()).isNotBlank();
            assertThat(city.latitude()).isBetween(-90.0, 90.0);
            assertThat(city.longitude()).isBetween(-180.0, 180.0);
            assertThat(ZoneId.getAvailableZoneIds()).contains(city.timezone());
            assertThat(city.population()).isPositive();
            assertThat(city.populationAsOfYear()).isBetween(1900, 2026);
            assertThat(city.populationSource()).matches("https?://.+");
            assertThat(city.coordinatesSource()).matches("https?://.+");
            assertThat(city.timezoneSource()).matches("https?://.+");
        });
    }
}
