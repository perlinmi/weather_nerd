package com.weathernerd.weather.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weather")
public class WeatherClientProperties {
    private String openMeteoForecastUrl;
    private String permissionsCheckUrl;
    private Duration connectTimeout = Duration.ofSeconds(2);
    private Duration readTimeout = Duration.ofSeconds(4);
    private String userAgent = "WeatherNerd/0.1.0";

    public String getOpenMeteoForecastUrl() {
        return openMeteoForecastUrl;
    }

    public void setOpenMeteoForecastUrl(String openMeteoForecastUrl) {
        this.openMeteoForecastUrl = openMeteoForecastUrl;
    }

    public String getPermissionsCheckUrl() {
        return permissionsCheckUrl;
    }

    public void setPermissionsCheckUrl(String permissionsCheckUrl) {
        this.permissionsCheckUrl = permissionsCheckUrl;
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
