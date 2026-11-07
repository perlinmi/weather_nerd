package com.weathernerd.weather.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import com.weathernerd.weather.domain.WeatherCodeSummary;
import com.weathernerd.weather.config.WeatherClientProperties;
import com.weathernerd.weather.domain.City;
import com.weathernerd.weather.service.ProviderQuotaExceededException;
import com.weathernerd.weather.service.UtcDailyRequestQuota;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class OpenMeteoClientTest {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-11-07T10:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest
    @MethodSource("com.weathernerd.weather.ForecastTestCities#stream")
    void requestsCityDailyFieldsAndMapsThreeDays(City city) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/dwd-icon")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("latitude", Double.toString(city.latitude())))
                .andExpect(queryParam("longitude", Double.toString(city.longitude())))
                .andExpect(queryParam("timezone", city.timezone()))
                .andExpect(queryParam("temperature_unit", "celsius"))
                .andExpect(queryParam("wind_speed_unit", "ms"))
                .andExpect(queryParam(
                        "daily",
                        "temperature_2m_min,temperature_2m_max,weather_code,precipitation_probability_max,wind_speed_10m_max"))
                .andExpect(queryParam("forecast_days", "3"))
                .andRespond(withSuccess(
                        validPayload(city.timezone(), LocalDate.parse("2026-11-07")),
                        MediaType.APPLICATION_JSON));
        UtcDailyRequestQuota quota = new UtcDailyRequestQuota(CLOCK);
        OpenMeteoClient client = client(builder, quota);

        ProviderForecast forecast = client.fetchForecast(city);

        assertThat(forecast.fetchedAt()).isEqualTo(Instant.now(CLOCK));
        assertThat(forecast.dailyForecast()).hasSize(3);
        assertThat(forecast.dailyForecast().get(0).date()).isEqualTo(LocalDate.parse("2026-11-07"));
        assertThat(forecast.dailyForecast().get(0).summary()).isEqualTo("Overcast");
        assertThat(forecast.dailyForecast().get(0).precipitationProbability()).isEqualTo(20);
        assertThat(quota.reservedToday()).isEqualTo(1);
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("com.weathernerd.weather.ForecastTestCities#stream")
    void rejectsPayloadThatDoesNotContainThreeExpectedDates(City city) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/dwd-icon")))
                .andRespond(withSuccess(
                        validPayload(city.timezone(), LocalDate.parse("2026-11-07")).replace(
                                "\"2026-11-09\"",
                                "\"2026-11-10\""),
                        MediaType.APPLICATION_JSON));
        OpenMeteoClient client = client(builder, new UtcDailyRequestQuota(CLOCK));

        assertThatThrownBy(() -> client.fetchForecast(city))
                .isInstanceOf(ProviderResponseException.class);
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("com.weathernerd.weather.ForecastTestCities#stream")
    void rejectsPayloadWithMissingOrMisalignedDailyValues(City city) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/dwd-icon")))
                .andRespond(withSuccess(
                        validPayload(city.timezone(), LocalDate.parse("2026-11-07")).replace(
                                "\"wind_speed_10m_max\": [4.5, 5.1, 3.7]",
                                "\"wind_speed_10m_max\": [4.5, 5.1]"),
                        MediaType.APPLICATION_JSON));
        OpenMeteoClient client = client(builder, new UtcDailyRequestQuota(CLOCK));

        assertThatThrownBy(() -> client.fetchForecast(city))
                .isInstanceOf(ProviderResponseException.class);
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("com.weathernerd.weather.ForecastTestCities#stream")
    void retriesServerFailureAndCountsEachOutboundAttempt(City city) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/dwd-icon")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_GATEWAY));
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/dwd-icon")))
                .andRespond(withSuccess(
                        validPayload(city.timezone(), LocalDate.parse("2026-11-07")),
                        MediaType.APPLICATION_JSON));
        UtcDailyRequestQuota quota = new UtcDailyRequestQuota(CLOCK);
        OpenMeteoClient client = client(builder, quota);

        ProviderForecast forecast = client.fetchForecast(city);

        assertThat(forecast.dailyForecast()).hasSize(3);
        assertThat(quota.reservedToday()).isEqualTo(2);
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("com.weathernerd.weather.ForecastTestCities#stream")
    void countsRedirectHopAsASeparateOutboundRequest(City city) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/dwd-icon")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TEMPORARY_REDIRECT)
                        .location(java.net.URI.create("http://open-meteo.test/v1/dwd-icon-redirect")));
        server.expect(requestTo("http://open-meteo.test/v1/dwd-icon-redirect"))
                .andRespond(withSuccess(
                        validPayload(city.timezone(), LocalDate.parse("2026-11-07")),
                        MediaType.APPLICATION_JSON));
        UtcDailyRequestQuota quota = new UtcDailyRequestQuota(CLOCK);
        OpenMeteoClient client = client(builder, quota);

        ProviderForecast forecast = client.fetchForecast(city);

        assertThat(forecast.dailyForecast()).hasSize(3);
        assertThat(quota.reservedToday()).isEqualTo(2);
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("com.weathernerd.weather.ForecastTestCities#stream")
    void doesNotDispatchRedirectWhenQuotaHasNoRemainingSlots(City city) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/dwd-icon")))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TEMPORARY_REDIRECT)
                        .location(java.net.URI.create("http://open-meteo.test/v1/dwd-icon-redirect")));
        UtcDailyRequestQuota quota = new UtcDailyRequestQuota(CLOCK);
        for (int attempt = 0; attempt < 499; attempt++) {
            quota.reserve();
        }
        OpenMeteoClient client = client(builder, quota);

        assertThatThrownBy(() -> client.fetchForecast(city))
                .isInstanceOf(ProviderQuotaExceededException.class);
        assertThat(quota.reservedToday()).isEqualTo(500);
        server.verify();
    }

    @ParameterizedTest
    @MethodSource("com.weathernerd.weather.ForecastTestCities#stream")
    void requestsCoordinatesAndDatesForTheSelectedCityTimezone(City city) {
        Clock lateUtcClock = Clock.fixed(
                Instant.parse("2026-11-07T23:30:00Z"), ZoneOffset.UTC);
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/dwd-icon")))
                .andExpect(queryParam("latitude", Double.toString(city.latitude())))
                .andExpect(queryParam("longitude", Double.toString(city.longitude())))
                .andExpect(queryParam("timezone", city.timezone()))
                .andRespond(withSuccess(
                        validPayload(city.timezone(), LocalDate.parse("2026-11-08")),
                        MediaType.APPLICATION_JSON));
        OpenMeteoClient client = client(
                builder, new UtcDailyRequestQuota(lateUtcClock), lateUtcClock);

        ProviderForecast forecast = client.fetchForecast(city);

        assertThat(forecast.dailyForecast().get(0).date()).isEqualTo(LocalDate.parse("2026-11-08"));
        server.verify();
    }

    private static OpenMeteoClient client(
            RestClient.Builder builder, UtcDailyRequestQuota quota) {
        return client(builder, quota, CLOCK);
    }

    private static OpenMeteoClient client(
            RestClient.Builder builder, UtcDailyRequestQuota quota, Clock clock) {
        return new OpenMeteoClient(
                builder.build(),
                properties(),
                quota,
                clock,
                new WeatherCodeSummary(),
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .findAndRegisterModules());
    }

    private static WeatherClientProperties properties() {
        WeatherClientProperties properties = new WeatherClientProperties();
        properties.setOpenMeteoBaseUrl("http://open-meteo.test");
        return properties;
    }

    private static String validPayload(String timezone, LocalDate firstDate) {
        return """
                {
                  "timezone": "%s",
                  "daily_units": {
                    "temperature_2m_min": "°C",
                    "temperature_2m_max": "°C",
                    "weather_code": "wmo code",
                    "precipitation_probability_max": "%%",
                    "wind_speed_10m_max": "m/s"
                  },
                  "daily": {
                    "time": ["%s", "%s", "%s"],
                    "temperature_2m_min": [4.2, 3.8, 2.9],
                    "temperature_2m_max": [10.1, 9.6, 8.7],
                    "weather_code": [3, 61, 2],
                    "precipitation_probability_max": [20, 60, 10],
                    "wind_speed_10m_max": [4.5, 5.1, 3.7]
                  }
                }
                """.formatted(
                timezone,
                firstDate,
                firstDate.plusDays(1),
                firstDate.plusDays(2));
    }
}
