package com.weathernerd.weather.api;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.weathernerd.weather.domain.DailyForecast;
import com.weathernerd.weather.domain.WeatherForecastResponse;
import com.weathernerd.weather.service.BerlinForecastService;
import com.weathernerd.weather.service.ForecastAccessDeniedException;
import com.weathernerd.weather.service.ForecastQuotaExceededException;
import com.weathernerd.weather.service.ForecastUnavailableException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BerlinForecastController.class)
class BerlinForecastControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BerlinForecastService forecastService;

    @Test
    void returnsThreeDayForecastAndForwardsCallerUserAgent() throws Exception {
        when(forecastService.getForecast("Browser/1.0")).thenReturn(response(false));

        mockMvc.perform(get("/weather/forecast/berlin")
                        .header(HttpHeaders.USER_AGENT, "Browser/1.0"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.city").value("Berlin"))
                .andExpect(jsonPath("$.country").value("Germany"))
                .andExpect(jsonPath("$.timezone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.source").value("Open-Meteo DWD API"))
                .andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.forecast.length()").value(3))
                .andExpect(jsonPath("$.forecast[0].minimumTemperatureCelsius").value(4.2))
                .andExpect(jsonPath("$.forecast[0].windSpeedMetersPerSecond").value(4.5));

        verify(forecastService).getForecast("Browser/1.0");
    }

    @Test
    void mapsPermissionDenialTo403ProblemDetails() throws Exception {
        when(forecastService.getForecast("Other/1.0")).thenThrow(new ForecastAccessDeniedException());

        mockMvc.perform(get("/weather/forecast/berlin")
                        .header(HttpHeaders.USER_AGENT, "Other/1.0"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void mapsPermissionsOutageTo503ProblemDetails() throws Exception {
        when(forecastService.getForecast("Browser/1.0"))
                .thenThrow(new com.weathernerd.weather.client.PermissionsServiceException("unavailable"));

        mockMvc.perform(get("/weather/forecast/berlin")
                        .header(HttpHeaders.USER_AGENT, "Browser/1.0"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void mapsQuotaExhaustionTo429AndProviderFailureTo502() throws Exception {
        when(forecastService.getForecast("Browser/1.0"))
                .thenThrow(new ForecastQuotaExceededException())
                .thenThrow(new ForecastUnavailableException());

        mockMvc.perform(get("/weather/forecast/berlin")
                        .header(HttpHeaders.USER_AGENT, "Browser/1.0"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        mockMvc.perform(get("/weather/forecast/berlin")
                        .header(HttpHeaders.USER_AGENT, "Browser/1.0"))
                .andExpect(status().isBadGateway())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    private static WeatherForecastResponse response(boolean stale) {
        return new WeatherForecastResponse(
                "Berlin",
                "Germany",
                "Europe/Berlin",
                "Open-Meteo DWD API",
                Instant.parse("2026-11-07T10:00:00Z"),
                stale,
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
