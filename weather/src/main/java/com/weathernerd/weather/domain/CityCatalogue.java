package com.weathernerd.weather.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

@Component
public final class CityCatalogue {
    private static final String RESOURCE = "city-catalogue-eu-2026-11-07.json";
    private static final int EXPECTED_CITY_COUNT = 95;

    private final String source;
    private final LocalDate snapshotRetrievedAt;
    private final List<City> cities;

    public CityCatalogue(ObjectMapper objectMapper) {
        Snapshot snapshot;
        try (var input = new ClassPathResource(RESOURCE).getInputStream()) {
            snapshot = objectMapper.readValue(input, Snapshot.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load the pinned city catalogue.", exception);
        }

        validate(snapshot);
        this.source = snapshot.source();
        this.snapshotRetrievedAt = snapshot.snapshotRetrievedAt();
        this.cities = List.copyOf(snapshot.cities());
    }

    public String source() {
        return source;
    }

    public LocalDate snapshotRetrievedAt() {
        return snapshotRetrievedAt;
    }

    public List<City> cities() {
        return cities;
    }

    public Optional<City> findById(String cityId) {
        if (cityId == null) {
            return Optional.empty();
        }
        return cities.stream().filter(city -> city.cityId().equals(cityId)).findFirst();
    }

    private static void validate(Snapshot snapshot) {
        if (snapshot == null
                || snapshot.source() == null
                || snapshot.snapshotRetrievedAt() == null
                || snapshot.cities() == null
                || snapshot.omissions() == null) {
            throw new IllegalStateException("The city catalogue snapshot is incomplete.");
        }
        URI sourceUri = URI.create(snapshot.source());
        if (!"https".equalsIgnoreCase(sourceUri.getScheme()) || sourceUri.getHost() == null) {
            throw new IllegalStateException("The city catalogue source must be an HTTPS URL.");
        }
        if (snapshot.sourceCityCount() != EXPECTED_CITY_COUNT
                || snapshot.cities().size() != EXPECTED_CITY_COUNT
                || snapshot.sourceCityCount() != snapshot.cities().size() + snapshot.omissions().size()) {
            throw new IllegalStateException("The pinned city catalogue does not match its source snapshot.");
        }

        Set<String> cityIds = new HashSet<>();
        for (int index = 0; index < snapshot.cities().size(); index++) {
            City city = snapshot.cities().get(index);
            if (city.rank() != index + 1) {
                throw new IllegalStateException("City ranks must be unique and in population order.");
            }
            if (!cityIds.add(city.cityId())) {
                throw new IllegalStateException("City IDs must be unique.");
            }
        }
    }

    public record Snapshot(
            String source,
            LocalDate snapshotRetrievedAt,
            int sourceCityCount,
            List<City> cities,
            List<String> omissions) {
    }
}
