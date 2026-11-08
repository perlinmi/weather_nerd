package com.weathernerd.permissions;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
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
    void grantsChromeAndSafariAndDeniesOtherAndMissingUserAgents() {
        assertThat(post(CHROME).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(post(SAFARI).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> other = post(OTHER);
        assertThat(other.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(other.getBody()).isEqualTo("{\"error\":\"Permission denied\"}");

        ResponseEntity<String> missing = post(null);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(missing.getBody()).isEqualTo("{\"error\":\"Permission denied\"}");
    }

    private ResponseEntity<String> post(String userAgent) {
        HttpHeaders headers = new HttpHeaders();
        if (userAgent != null) {
            headers.set(HttpHeaders.USER_AGENT, userAgent);
        }
        return http.exchange(
                "http://localhost:" + port + "/permissions/is_granted",
                HttpMethod.POST,
                new HttpEntity<>(headers),
                String.class);
    }
}
