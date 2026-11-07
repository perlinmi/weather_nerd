package com.weathernerd.weather.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weathernerd.weather.ForecastTestCities;
import com.weathernerd.weather.domain.City;
import com.weathernerd.weather.domain.DailyForecast;
import com.weathernerd.weather.domain.WeatherForecastResponse;
import com.weathernerd.weather.service.CityForecastService;
import com.weathernerd.weather.service.UnknownCityException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CityForecastController.class)
class CityForecastControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CityForecastService forecastService;

    @ParameterizedTest
    @MethodSource("com.weathernerd.weather.ForecastTestCities#stream")
    void routesByCityIdAndPreservesTheForecastResponseShape(City city) throws Exception {
        when(forecastService.getForecast(city.cityId(), "Browser/1.0"))
                .thenReturn(response(city));

        mockMvc.perform(get("/weather/forecast/" + city.cityId())
                        .header(HttpHeaders.USER_AGENT, "Browser/1.0"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.city").value(city.city()))
                .andExpect(jsonPath("$.country").value(city.country()))
                .andExpect(jsonPath("$.timezone").value(city.timezone()))
                .andExpect(jsonPath("$.source").value("Open-Meteo DWD API"))
                .andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.forecast.length()").value(3))
                .andExpect(jsonPath("$.forecast[0].date").value("2026-11-07"))
                .andExpect(jsonPath("$.forecast[0].minimumTemperatureCelsius").value(4.2))
                .andExpect(jsonPath("$.forecast[0].windSpeedMetersPerSecond").value(4.5));

        verify(forecastService).getForecast(city.cityId(), "Browser/1.0");
    }

    @Test
    void mapsUnknownCityTo404ProblemDetails() throws Exception {
        when(forecastService.getForecast("unknown-city", null)).thenThrow(new UnknownCityException());

        mockMvc.perform(get("/weather/forecast/unknown-city"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404));
    }

    private static WeatherForecastResponse response(City city) {
        return new WeatherForecastResponse(
                city.city(),
                city.country(),
                city.timezone(),
                "Open-Meteo DWD API",
                Instant.parse("2026-11-07T10:00:00Z"),
                false,
                List.of(
                        daily("2026-11-07"),
                        daily("2026-11-08"),
                        daily("2026-11-09")));
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
