package com.weathernerd.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.weathernerd.weather.api.BerlinForecastController;
import com.weathernerd.weather.client.OpenMeteoClient;
import com.weathernerd.weather.client.PermissionsClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class WeatherApplicationContextTest {
    @Autowired
    private BerlinForecastController controller;

    @Autowired
    private OpenMeteoClient openMeteoClient;

    @Autowired
    private PermissionsClient permissionsClient;

    @Test
    void startsWithConfiguredForecastAndPermissionsClients() {
        assertThat(controller).isNotNull();
        assertThat(openMeteoClient).isNotNull();
        assertThat(permissionsClient).isNotNull();
    }
}
