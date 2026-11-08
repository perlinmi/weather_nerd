package com.weathernerd.permissions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
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
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class PermissionsApiEndToEndTest {
    private static final String CHROME =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36";
    private static final String SAFARI =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/605.1.15 Version/17.0 Safari/605.1.15";
    private static final String OTHER =
            "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/125.0.0.0 Safari/537.36 Edg/125.0.0.0";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate http;

    @Test
    void appliesBrowserPolicyForEachWeatherEndpoint() {
        assertThat(post(CHROME, "forecast").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post(SAFARI, "forecast").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertDenied(post(OTHER, "forecast"));

        assertThat(post(CHROME, "nearby").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertDenied(post(SAFARI, "nearby"));
        assertDenied(post(OTHER, "nearby"));
    }

    @Test
    void deniesMissingOrUnknownEndpointKeysAndMissingUserAgents() {
        assertDenied(post(CHROME, null));
        assertDenied(post(CHROME, "unknown"));
        assertDenied(post(null, "forecast"));
    }

    private ResponseEntity<String> post(String userAgent, String endpoint) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (userAgent != null) {
            headers.set(HttpHeaders.USER_AGENT, userAgent);
        }
        Object body = endpoint == null ? null : Map.of("endpoint", endpoint);
        return http.exchange(
                "http://localhost:" + port + "/permissions/is_granted",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);
    }

    private void assertDenied(ResponseEntity<String> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isEqualTo("{\"error\":\"Permission denied\"}");
    }
}
