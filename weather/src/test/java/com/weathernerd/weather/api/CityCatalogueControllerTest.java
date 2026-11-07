package com.weathernerd.weather.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weathernerd.weather.domain.City;
import com.weathernerd.weather.domain.CityCatalogue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class CityCatalogueControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CityCatalogue catalogue;

    @Test
    void returnsPinnedCatalogue() throws Exception {
        var result = mockMvc.perform(get("/weather/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value(
                        "https://en.wikipedia.org/wiki/List_of_cities_in_the_European_Union_by_population_within_city_limits"))
                .andExpect(jsonPath("$.snapshotRetrievedAt").value("2026-11-07"))
                .andExpect(jsonPath("$.cities.length()").value(95))
                .andExpect(jsonPath("$.cities[0].cityId").value("berlin"))
                .andExpect(jsonPath("$.cities[0].rank").value(1))
                .andExpect(jsonPath("$.cities[0].population").value(3913644));

        for (City city : catalogue.cities()) {
            result.andExpect(jsonPath("$.cities[%d].cityId".formatted(city.rank() - 1))
                            .value(city.cityId()))
                    .andExpect(jsonPath("$.cities[%d].city".formatted(city.rank() - 1))
                            .value(city.city()))
                    .andExpect(jsonPath("$.cities[%d].rainForecastAvailable".formatted(city.rank() - 1))
                            .doesNotExist());
        }
    }
}
