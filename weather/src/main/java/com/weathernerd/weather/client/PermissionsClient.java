package com.weathernerd.weather.client;

import com.weathernerd.weather.config.WeatherClientProperties;
import com.weathernerd.weather.exception.PermissionsServiceException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PermissionsClient {
    private final RestClient restClient;
    private final WeatherClientProperties properties;

    public PermissionsClient(
            @Qualifier("permissionsRestClient") RestClient permissionsRestClient,
            WeatherClientProperties properties) {
        this.restClient = permissionsRestClient;
        this.properties = properties;
    }

    public boolean isGranted(String originalUserAgent, String endpoint) {
        try {
            Response response = restClient.post()
                    .uri(properties.getPermissionsCheckUrl())
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.set(
                            HttpHeaders.USER_AGENT,
                            originalUserAgent == null ? "" : originalUserAgent))
                    .body(new PermissionCheckRequest(endpoint))
                    .exchange((request, clientResponse) -> new Response(
                            clientResponse.getStatusCode().value()));

            if (response.statusCode() == 200) {
                return true;
            }
            if (response.statusCode() == 403) {
                return false;
            }
            throw new PermissionsServiceException(
                    "The permissions service returned HTTP " + response.statusCode() + ".");
        } catch (RestClientException exception) {
            throw new PermissionsServiceException("The permissions service request failed.", exception);
        }
    }

    private record Response(int statusCode) {
    }

    private record PermissionCheckRequest(String endpoint) {
    }
}
