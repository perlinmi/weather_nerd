package com.weathernerd.weather.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weathernerd.weather.config.WeatherClientProperties;
import com.weathernerd.weather.domain.DailyForecast;
import com.weathernerd.weather.domain.City;
import com.weathernerd.weather.domain.WeatherCodeSummary;
import com.weathernerd.weather.service.ProviderRequestQuota;
import org.springframework.beans.factory.annotation.Qualifier;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class OpenMeteoClient {
    private static final int MAX_ATTEMPTS_PER_HOP = 2;
    private static final int MAX_REDIRECTS = 5;

    private final RestClient restClient;
    private final String baseUrl;
    private final ProviderRequestQuota quota;
    private final Clock clock;
    private final WeatherCodeSummary codeSummary;
    private final ObjectMapper objectMapper;

    public OpenMeteoClient(
            @Qualifier("openMeteoRestClient")
            RestClient openMeteoRestClient,
            WeatherClientProperties properties,
            ProviderRequestQuota quota,
            Clock clock,
            WeatherCodeSummary codeSummary,
            ObjectMapper objectMapper) {
        this.restClient = openMeteoRestClient;
        this.baseUrl = properties.getOpenMeteoBaseUrl();
        this.quota = quota;
        this.clock = clock;
        this.codeSummary = codeSummary;
        this.objectMapper = objectMapper;
    }

    public ProviderForecast fetchForecast(City city) {
        URI requestUri = buildForecastUri(city);
        RawResponse response = dispatchWithRetry(requestUri);
        int redirects = 0;
        while (isRedirect(response.statusCode())) {
            if (++redirects > MAX_REDIRECTS) {
                throw new ProviderResponseException("The forecast provider redirected too many times.");
            }
            requestUri = safeRedirect(requestUri, response.location());
            response = dispatchWithRetry(requestUri);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new ProviderResponseException(
                    "The forecast provider returned HTTP " + response.statusCode() + ".");
        }

        return toForecast(response.body(), city);
    }

    private RawResponse dispatchWithRetry(URI uri) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS_PER_HOP; attempt++) {
            quota.reserve();
            try {
                RawResponse response = restClient.get()
                        .uri(uri)
                        .exchange((request, clientResponse) -> readResponse(clientResponse));
                if (response.statusCode() >= 500 && attempt < MAX_ATTEMPTS_PER_HOP) {
                    continue;
                }
                return response;
            } catch (RestClientException exception) {
                if (attempt == MAX_ATTEMPTS_PER_HOP) {
                    throw new ProviderResponseException(
                            "The forecast provider request failed.", exception);
                }
            }
        }
        throw new IllegalStateException("Provider retry loop ended unexpectedly.");
    }

    private RawResponse readResponse(ClientHttpResponse response) throws IOException {
        return new RawResponse(
                response.getStatusCode().value(),
                response.getHeaders().getFirst(HttpHeaders.LOCATION),
                new String(response.getBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    }

    private URI buildForecastUri(City city) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .path("/v1/dwd-icon")
                .queryParam("latitude", Double.toString(city.latitude()))
                .queryParam("longitude", Double.toString(city.longitude()))
                .queryParam(
                        "daily",
                        "temperature_2m_min,temperature_2m_max,weather_code,"
                                + "precipitation_probability_max,wind_speed_10m_max")
                .queryParam("temperature_unit", "celsius")
                .queryParam("wind_speed_unit", "ms")
                .queryParam("timezone", city.timezone())
                .queryParam("forecast_days", "3")
                .build()
                .encode()
                .toUri();
    }

    private URI safeRedirect(URI currentUri, String location) {
        if (location == null) {
            throw new ProviderResponseException("The forecast provider returned a redirect without a target.");
        }

        URI target = currentUri.resolve(location);
        URI base = URI.create(baseUrl);
        if (!base.getScheme().equalsIgnoreCase(target.getScheme())
                || !base.getHost().equalsIgnoreCase(target.getHost())
                || target.getUserInfo() != null) {
            throw new ProviderResponseException(
                    "The forecast provider redirected outside its configured origin.");
        }
        return target;
    }

    private ProviderForecast toForecast(String body, City city) {
        ProviderPayload payload;
        try {
            payload = objectMapper.readValue(body, ProviderPayload.class);
        } catch (JsonProcessingException exception) {
            throw new ProviderResponseException("The forecast provider returned invalid JSON.", exception);
        }

        ZoneId timezone = ZoneId.of(city.timezone());
        LocalDate firstDate = LocalDate.now(clock.withZone(timezone));
        List<LocalDate> expectedDates = List.of(firstDate, firstDate.plusDays(1), firstDate.plusDays(2));
        if (payload == null
                || !city.timezone().equals(payload.timezone())
                || payload.daily() == null
                || !validUnits(payload.dailyUnits())
                || !validDaily(payload.daily(), expectedDates)) {
            throw new ProviderResponseException(
                    "The forecast provider returned incomplete or unusable daily data.");
        }

        List<DailyForecast> forecasts = new ArrayList<>(3);
        for (int index = 0; index < 3; index++) {
            int code = payload.daily().weatherCode().get(index);
            forecasts.add(new DailyForecast(
                    expectedDates.get(index),
                    payload.daily().minimumTemperatureCelsius().get(index),
                    payload.daily().maximumTemperatureCelsius().get(index),
                    codeSummary.describe(code),
                    code,
                    payload.daily().precipitationProbability().get(index),
                    payload.daily().maximumWindSpeedMetersPerSecond().get(index)));
        }
        return new ProviderForecast(Instant.now(clock), forecasts);
    }

    private boolean validUnits(ProviderDailyUnits units) {
        return units != null
                && "°C".equals(units.minimumTemperatureCelsius())
                && "°C".equals(units.maximumTemperatureCelsius())
                && "wmo code".equalsIgnoreCase(units.weatherCode())
                && "%".equals(units.precipitationProbability())
                && "m/s".equals(units.maximumWindSpeedMetersPerSecond());
    }

    private boolean validDaily(ProviderDaily daily, List<LocalDate> expectedDates) {
        if (daily.time() == null
                || daily.minimumTemperatureCelsius() == null
                || daily.maximumTemperatureCelsius() == null
                || daily.weatherCode() == null
                || daily.precipitationProbability() == null
                || daily.maximumWindSpeedMetersPerSecond() == null
                || daily.time().size() != 3
                || daily.minimumTemperatureCelsius().size() != 3
                || daily.maximumTemperatureCelsius().size() != 3
                || daily.weatherCode().size() != 3
                || daily.precipitationProbability().size() != 3
                || daily.maximumWindSpeedMetersPerSecond().size() != 3) {
            return false;
        }

        for (int index = 0; index < 3; index++) {
            try {
                if (!expectedDates.get(index).equals(LocalDate.parse(daily.time().get(index)))) {
                    return false;
                }
            } catch (RuntimeException exception) {
                return false;
            }

            BigDecimal minimum = daily.minimumTemperatureCelsius().get(index);
            BigDecimal maximum = daily.maximumTemperatureCelsius().get(index);
            Integer precipitation = daily.precipitationProbability().get(index);
            BigDecimal wind = daily.maximumWindSpeedMetersPerSecond().get(index);
            if (minimum == null
                    || maximum == null
                    || minimum.compareTo(maximum) > 0
                    || daily.weatherCode().get(index) == null
                    || precipitation == null
                    || precipitation < 0
                    || precipitation > 100
                    || wind == null
                    || wind.signum() < 0) {
                return false;
            }
        }
        return true;
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301
                || statusCode == 302
                || statusCode == 303
                || statusCode == 307
                || statusCode == 308;
    }

    private record RawResponse(int statusCode, String location, String body) {
    }

    private record ProviderPayload(String timezone, ProviderDailyUnits daily_units, ProviderDaily daily) {
        ProviderDailyUnits dailyUnits() {
            return daily_units;
        }
    }

    private record ProviderDailyUnits(
            String temperature_2m_min,
            String temperature_2m_max,
            String weather_code,
            String precipitation_probability_max,
            String wind_speed_10m_max) {
        String minimumTemperatureCelsius() {
            return temperature_2m_min;
        }

        String maximumTemperatureCelsius() {
            return temperature_2m_max;
        }

        String weatherCode() {
            return weather_code;
        }

        String precipitationProbability() {
            return precipitation_probability_max;
        }

        String maximumWindSpeedMetersPerSecond() {
            return wind_speed_10m_max;
        }
    }

    private record ProviderDaily(
            List<String> time,
            List<BigDecimal> temperature_2m_min,
            List<BigDecimal> temperature_2m_max,
            List<Integer> weather_code,
            List<Integer> precipitation_probability_max,
            List<BigDecimal> wind_speed_10m_max) {
        List<BigDecimal> minimumTemperatureCelsius() {
            return temperature_2m_min;
        }

        List<BigDecimal> maximumTemperatureCelsius() {
            return temperature_2m_max;
        }

        List<Integer> weatherCode() {
            return weather_code;
        }

        List<Integer> precipitationProbability() {
            return precipitation_probability_max;
        }

        List<BigDecimal> maximumWindSpeedMetersPerSecond() {
            return wind_speed_10m_max;
        }
    }
}
