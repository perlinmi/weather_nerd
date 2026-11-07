package com.weathernerd.weather.config;

import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(WeatherClientProperties.class)
public class WeatherHttpConfiguration {
    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    RestClient openMeteoRestClient(
            RestClient.Builder builder, WeatherClientProperties properties) {
        return configuredClient(builder, properties);
    }

    @Bean
    RestClient permissionsRestClient(
            RestClient.Builder builder, WeatherClientProperties properties) {
        return configuredClient(builder, properties);
    }

    private RestClient configuredClient(
            RestClient.Builder builder, WeatherClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());

        return builder.clone()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, properties.getUserAgent())
                .build();
    }
}
