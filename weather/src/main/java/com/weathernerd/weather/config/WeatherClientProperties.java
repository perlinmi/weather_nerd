package com.weathernerd.weather.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weather")
public class WeatherClientProperties {
    private String openMeteoBaseUrl = "https://api.open-meteo.com";
    private String permissionsBaseUrl = "http://localhost:8081";
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(4);
    private String userAgent = "WeatherNerd/0.1.0";

    public String getOpenMeteoBaseUrl() {
        return openMeteoBaseUrl;
    }

    public void setOpenMeteoBaseUrl(String openMeteoBaseUrl) {
        this.openMeteoBaseUrl = openMeteoBaseUrl;
    }

    public String getPermissionsBaseUrl() {
        return permissionsBaseUrl;
    }

    public void setPermissionsBaseUrl(String permissionsBaseUrl) {
        this.permissionsBaseUrl = permissionsBaseUrl;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }
}
