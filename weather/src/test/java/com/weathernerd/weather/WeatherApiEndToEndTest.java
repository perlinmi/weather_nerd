package com.weathernerd.weather;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.weathernerd.weather.domain.City;
import com.weathernerd.weather.domain.CityCatalogue;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class WeatherApiEndToEndTest {
    private static final WireMockServer WEATHER_PROVIDER =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());
    private static final WireMockServer PERMISSIONS_SERVICE =
            new WireMockServer(WireMockConfiguration.options().dynamicPort());

    static {
        WEATHER_PROVIDER.start();
        PERMISSIONS_SERVICE.start();
    }

    @DynamicPropertySource
    static void configureExternalServices(DynamicPropertyRegistry registry) {
        registry.add(
                "weather.open-meteo-forecast-url",
                () -> WEATHER_PROVIDER.baseUrl() + "/v1/dwd-icon");
        registry.add(
                "weather.permissions-check-url",
                () -> PERMISSIONS_SERVICE.baseUrl() + "/permissions/is_granted");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private CityCatalogue cityCatalogue;

    @BeforeEach
    void resetExternalServices() {
        WEATHER_PROVIDER.resetAll();
        PERMISSIONS_SERVICE.resetAll();
    }

    @AfterAll
    static void stopExternalServices() {
        WEATHER_PROVIDER.stop();
        PERMISSIONS_SERVICE.stop();
    }

    @Test
    void listsCitiesThroughThePublicApiWithoutCallingExternalServices() throws Exception {
        ResponseEntity<String> response = http.getForEntity(url("/weather/cities"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode cities = jsonMapper.readTree(response.getBody()).path("cities");
        assertThat(cities).hasSize(95);
        for (int index = 0; index < cities.size(); index++) {
            JsonNode city = cities.get(index);
            assertThat(city.path("rank").asInt()).isEqualTo(index + 1);
            assertThat(city.path("cityId").asText()).isNotBlank();
            assertThat(city.path("city").asText()).isNotBlank();
            assertThat(city.path("country").asText()).isNotBlank();
            assertThat(city.path("latitude").asDouble()).isBetween(-90.0, 90.0);
            assertThat(city.path("longitude").asDouble()).isBetween(-180.0, 180.0);
            assertThat(city.path("timezone").asText()).isNotBlank();
            assertThat(city.path("population").asLong()).isPositive();
            assertThat(city.path("populationAsOfYear").asInt()).isPositive();
            assertThat(city.has("rainForecastAvailable")).isFalse();
        }
        WEATHER_PROVIDER.verify(0, anyRequestedFor(anyUrl()));
        PERMISSIONS_SERVICE.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void returnsEligibleCityForecastAndRainDataThroughThePublicApi() throws Exception {
        City city = city("berlin");
        String userAgent = "Chrome/125.0";
        grantPermission(userAgent);
        stubWeather(city);

        ResponseEntity<String> response = forecast(city, userAgent);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = jsonMapper.readTree(response.getBody());
        assertThat(body.path("forecast")).hasSize(3);
        assertThat(body.path("forecast").get(0).has("precipitationProbability")).isFalse();
        assertThat(body.path("rainForecast")).hasSize(3);
        for (int index = 0; index < 3; index++) {
            assertThat(body.path("rainForecast").get(index).path("date").asText())
                    .isEqualTo(body.path("forecast").get(index).path("date").asText());
        }
        assertThat(body.path("rainForecast").get(0).path("probability").asInt()).isEqualTo(20);

        PERMISSIONS_SERVICE.verify(1, postRequestedFor(urlPathEqualTo("/permissions/is_granted"))
                .withHeader(HttpHeaders.USER_AGENT, equalTo(userAgent))
                .withRequestBody(matchingJsonPath("$.endpoint", equalTo("forecast"))));
        WEATHER_PROVIDER.verify(1, getRequestedFor(urlPathEqualTo("/v1/dwd-icon"))
                .withQueryParam("latitude", equalTo(Double.toString(city.latitude())))
                .withQueryParam("longitude", equalTo(Double.toString(city.longitude())))
                .withQueryParam("timezone", equalTo(city.timezone())));
    }

    @Test
    void omitsRainForecastForNamesWithSevenOrMoreLetters() throws Exception {
        City city = city("ljubljana");
        grantPermission("Safari/17.0");
        stubWeather(city);

        ResponseEntity<String> response = forecast(city, "Safari/17.0");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = jsonMapper.readTree(response.getBody());
        assertThat(body.path("forecast")).hasSize(3);
        assertThat(body.has("rainForecast")).isFalse();
    }

    @Test
    void deniedPermissionPreventsAProviderRequest() throws Exception {
        String userAgent = "Firefox/125.0";
        PERMISSIONS_SERVICE.stubFor(post(urlPathEqualTo("/permissions/is_granted"))
                .withHeader(HttpHeaders.USER_AGENT, equalTo(userAgent))
                .withRequestBody(matchingJsonPath("$.endpoint", equalTo("forecast")))
                .willReturn(aResponse().withStatus(HttpStatus.FORBIDDEN.value())));

        ResponseEntity<String> response = forecast(city("madrid"), userAgent);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(jsonMapper.readTree(response.getBody()).path("status").asInt()).isEqualTo(403);
        WEATHER_PROVIDER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void unknownCityIsRejectedBeforeCallingExternalServices() {
        ResponseEntity<String> response = forecast("not-a-city", "Chrome/125.0");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        WEATHER_PROVIDER.verify(0, anyRequestedFor(anyUrl()));
        PERMISSIONS_SERVICE.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void providerFailureBecomesAnApiError() throws Exception {
        City city = city("malmo");
        grantPermission("Chrome/125.0");
        WEATHER_PROVIDER.stubFor(get(urlPathEqualTo("/v1/dwd-icon"))
                .willReturn(aResponse().withStatus(HttpStatus.BAD_GATEWAY.value())));

        ResponseEntity<String> response = forecast(city, "Chrome/125.0");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(jsonMapper.readTree(response.getBody()).path("status").asInt()).isEqualTo(502);
    }

    @Test
    void returnsNearbyCitiesNearestFirstAndReusesTheirCachedForecasts() throws Exception {
        String userAgent = "Chrome/125.0";
        grantNearbyPermission(userAgent);
        List<City> nearbyCities = List.of(
                catalogueCity("szczecin"),
                catalogueCity("leipzig"),
                catalogueCity("dresden"));
        nearbyCities.forEach(this::stubWeather);

        ResponseEntity<String> firstResponse = nearby("BERLIN", userAgent);
        ResponseEntity<String> cachedResponse = nearby("berlin", userAgent);

        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = jsonMapper.readTree(firstResponse.getBody());
        assertThat(body.path("originCityId").asText()).isEqualTo("berlin");
        List<JsonNode> results = StreamSupport.stream(body.path("nearbyCities").spliterator(), false)
                .toList();
        assertThat(results).extracting(city -> city.path("cityId").asText())
                .containsExactly("szczecin", "leipzig", "dresden");
        assertThat(results).extracting(city -> city.path("distanceKm").asDouble())
                .containsExactly(127.9, 147.9, 165.0);
        assertThat(results).allSatisfy(city -> {
            assertThat(city.has("forecast")).isTrue();
            assertThat(city.path("forecast").path("forecast")).hasSize(3);
        });

        assertThat(cachedResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        PERMISSIONS_SERVICE.verify(2, postRequestedFor(urlPathEqualTo("/permissions/is_granted"))
                .withHeader(HttpHeaders.USER_AGENT, equalTo(userAgent))
                .withRequestBody(matchingJsonPath("$.endpoint", equalTo("nearby"))));
        nearbyCities.forEach(city -> WEATHER_PROVIDER.verify(
                1,
                getRequestedFor(urlPathEqualTo("/v1/dwd-icon"))
                        .withQueryParam("latitude", equalTo(Double.toString(city.latitude())))
                        .withQueryParam("longitude", equalTo(Double.toString(city.longitude())))));
    }

    @Test
    void returnsNearbyCitiesWithoutForecastWhenProviderFails() throws Exception {
        String userAgent = "Chrome/125.0";
        grantNearbyPermission(userAgent);
        List<City> nearbyCities =
                List.of(catalogueCity("bratislava"), catalogueCity("brno"), catalogueCity("graz"));
        City unavailable = nearbyCities.getFirst();
        WEATHER_PROVIDER.stubFor(get(urlPathEqualTo("/v1/dwd-icon"))
                .withQueryParam("latitude", equalTo(Double.toString(unavailable.latitude())))
                .withQueryParam("longitude", equalTo(Double.toString(unavailable.longitude())))
                .withQueryParam("timezone", equalTo(unavailable.timezone()))
                .willReturn(aResponse().withStatus(HttpStatus.BAD_GATEWAY.value())));
        nearbyCities.stream().skip(1).forEach(this::stubWeather);

        ResponseEntity<String> response = nearby("vienna", userAgent);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode results = jsonMapper.readTree(response.getBody()).path("nearbyCities");
        List<JsonNode> nearbyResults =
                StreamSupport.stream(results.spliterator(), false).toList();
        assertThat(nearbyResults).extracting(city -> city.path("cityId").asText())
                .containsExactly("bratislava", "brno", "graz");
        assertThat(nearbyResults.get(0).has("forecast")).isFalse();
        assertThat(nearbyResults.get(1).has("forecast")).isTrue();
        assertThat(nearbyResults.get(2).has("forecast")).isTrue();
    }

    @Test
    void deniesNonChromeNearbyRequestsBeforeCallingProvider() throws Exception {
        String userAgent = "Safari/17.0";
        denyNearbyPermission(userAgent);

        ResponseEntity<String> response = nearby("hamburg", userAgent);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        WEATHER_PROVIDER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void permissionsServiceFailureReturns503BeforeForecastRequests() {
        String userAgent = "Chrome/125.0";
        PERMISSIONS_SERVICE.stubFor(post(urlPathEqualTo("/permissions/is_granted"))
                .withHeader(HttpHeaders.USER_AGENT, equalTo(userAgent))
                .withRequestBody(matchingJsonPath("$.endpoint", equalTo("nearby")))
                .willReturn(aResponse().withStatus(HttpStatus.INTERNAL_SERVER_ERROR.value())));

        ResponseEntity<String> response = nearby("helsinki", userAgent);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        WEATHER_PROVIDER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void returnsEmptyNearbyListWhenNoCatalogueCitiesAreWithinRange() throws Exception {
        grantNearbyPermission("Chrome/125.0");

        ResponseEntity<String> response = nearby("riga", "Chrome/125.0");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(jsonMapper.readTree(response.getBody()).path("nearbyCities")).isEmpty();
        WEATHER_PROVIDER.verify(0, anyRequestedFor(anyUrl()));
    }

    @Test
    void rejectsUnknownNearbyCityBeforePermissionCheck() {
        ResponseEntity<String> response = nearby("not-a-city", "Chrome/125.0");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        PERMISSIONS_SERVICE.verify(0, anyRequestedFor(anyUrl()));
        WEATHER_PROVIDER.verify(0, anyRequestedFor(anyUrl()));
    }

    private City city(String cityId) {
        return ForecastTestCities.ALL.stream()
                .filter(city -> city.cityId().equals(cityId))
                .findFirst()
                .orElseThrow();
    }

    private City catalogueCity(String cityId) {
        return cityCatalogue.findById(cityId).orElseThrow();
    }

    private ResponseEntity<String> forecast(City city, String userAgent) {
        return forecast(city.cityId(), userAgent);
    }

    private ResponseEntity<String> forecast(String cityId, String userAgent) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        return http.exchange(
                url("/weather/forecast/" + cityId),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }

    private ResponseEntity<String> nearby(String cityId, String userAgent) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.USER_AGENT, userAgent);
        return http.exchange(
                url("/weather/nearby/" + cityId),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private void grantPermission(String userAgent) {
        PERMISSIONS_SERVICE.stubFor(post(urlPathEqualTo("/permissions/is_granted"))
                .withHeader(HttpHeaders.USER_AGENT, equalTo(userAgent))
                .withRequestBody(matchingJsonPath("$.endpoint", equalTo("forecast")))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())));
    }

    private void grantNearbyPermission(String userAgent) {
        PERMISSIONS_SERVICE.stubFor(post(urlPathEqualTo("/permissions/is_granted"))
                .withHeader(HttpHeaders.USER_AGENT, equalTo(userAgent))
                .withRequestBody(matchingJsonPath("$.endpoint", equalTo("nearby")))
                .willReturn(aResponse().withStatus(HttpStatus.OK.value())));
    }

    private void denyNearbyPermission(String userAgent) {
        PERMISSIONS_SERVICE.stubFor(post(urlPathEqualTo("/permissions/is_granted"))
                .withHeader(HttpHeaders.USER_AGENT, equalTo(userAgent))
                .withRequestBody(matchingJsonPath("$.endpoint", equalTo("nearby")))
                .willReturn(aResponse().withStatus(HttpStatus.FORBIDDEN.value())));
    }

    private void stubWeather(City city) {
        LocalDate today = LocalDate.now(ZoneId.of(city.timezone()));
        String body = """
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
                """.formatted(city.timezone(), today, today.plusDays(1), today.plusDays(2));
        WEATHER_PROVIDER.stubFor(get(urlPathEqualTo("/v1/dwd-icon"))
                .withQueryParam("latitude", equalTo(Double.toString(city.latitude())))
                .withQueryParam("longitude", equalTo(Double.toString(city.longitude())))
                .withQueryParam("timezone", equalTo(city.timezone()))
                .willReturn(aResponse()
                        .withStatus(HttpStatus.OK.value())
                        .withHeader(HttpHeaders.CONTENT_TYPE, "application/json")
                        .withBody(body)));
    }
}
