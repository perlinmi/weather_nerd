package com.weathernerd.weather.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.weathernerd.weather.config.WeatherClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class PermissionsClientTest {
    @Test
    void forwardsOriginalUserAgentAndGrantsOnlyOn200() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://permissions.test/permissions/is_granted"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.USER_AGENT, "Original Browser/1.0"))
                .andRespond(withSuccess());
        WeatherClientProperties properties = new WeatherClientProperties();
        properties.setPermissionsBaseUrl("http://permissions.test");
        PermissionsClient client = new PermissionsClient(builder.build(), properties);

        assertThat(client.isGranted("Original Browser/1.0")).isTrue();
        server.verify();
    }

    @Test
    void forwardsAbsentUserAgentAsAnEmptyHeaderAndTreats403AsDenied() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://permissions.test/permissions/is_granted"))
                .andExpect(header(HttpHeaders.USER_AGENT, ""))
                .andRespond(withStatus(HttpStatus.FORBIDDEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"Permission denied\"}"));
        WeatherClientProperties properties = new WeatherClientProperties();
        properties.setPermissionsBaseUrl("http://permissions.test");
        PermissionsClient client = new PermissionsClient(builder.build(), properties);

        assertThat(client.isGranted(null)).isFalse();
        server.verify();
    }

    @Test
    void treatsUnexpectedStatusAsPermissionsOutage() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://permissions.test/permissions/is_granted"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        WeatherClientProperties properties = new WeatherClientProperties();
        properties.setPermissionsBaseUrl("http://permissions.test");
        PermissionsClient client = new PermissionsClient(builder.build(), properties);

        assertThatThrownBy(() -> client.isGranted("Browser/1.0"))
                .isInstanceOf(PermissionsServiceException.class);
        server.verify();
    }
}
