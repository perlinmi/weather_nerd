package com.weathernerd.weather.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    void returnsPinnedCatalogueWithoutPerCitySourceCitations() throws Exception {
        mockMvc.perform(get("/weather/cities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value(
                        "https://en.wikipedia.org/wiki/List_of_cities_in_the_European_Union_by_population_within_city_limits"))
                .andExpect(jsonPath("$.snapshotRetrievedAt").value("2026-11-07"))
                .andExpect(jsonPath("$.cities.length()").value(95))
                .andExpect(jsonPath("$.cities[0].cityId").value("berlin"))
                .andExpect(jsonPath("$.cities[0].rank").value(1))
                .andExpect(jsonPath("$.cities[0].population").value(3913644))
                .andExpect(jsonPath("$.cities[0].populationSource").doesNotExist())
                .andExpect(jsonPath("$.cities[0].coordinatesSource").doesNotExist())
                .andExpect(jsonPath("$.cities[0].timezoneSource").doesNotExist());
    }
}
