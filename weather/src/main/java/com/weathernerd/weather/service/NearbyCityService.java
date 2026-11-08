package com.weathernerd.weather.service;

import com.weathernerd.weather.client.PermissionsClient;
import com.weathernerd.weather.domain.City;
import com.weathernerd.weather.domain.CityCatalogue;
import com.weathernerd.weather.domain.NearbyCityItem;
import com.weathernerd.weather.domain.NearbyCitiesResponse;
import com.weathernerd.weather.domain.NearbyCitySearch;
import com.weathernerd.weather.domain.WeatherForecastResponse;
import com.weathernerd.weather.exception.ForecastAccessDeniedException;
import com.weathernerd.weather.exception.ForecastQuotaExceededException;
import com.weathernerd.weather.exception.ForecastUnavailableException;
import com.weathernerd.weather.exception.UnknownCityException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NearbyCityService {
    private static final Logger LOGGER = LoggerFactory.getLogger(NearbyCityService.class);

    private final CityCatalogue catalogue;
    private final PermissionsClient permissionsClient;
    private final CityForecastService forecastService;

    public NearbyCityService(
            CityCatalogue catalogue,
            PermissionsClient permissionsClient,
            CityForecastService forecastService) {
        this.catalogue = catalogue;
        this.permissionsClient = permissionsClient;
        this.forecastService = forecastService;
    }

    public NearbyCitiesResponse getNearbyCities(String cityId, String originalUserAgent) {
        City origin = catalogue.findByIdIgnoreCase(cityId).orElseThrow(UnknownCityException::new);
        if (!permissionsClient.isGranted(originalUserAgent, "nearby")) {
            throw new ForecastAccessDeniedException();
        }

        List<NearbyCityItem> nearbyCities = NearbyCitySearch.within200Km(origin, catalogue.cities())
                .stream()
                .map(this::toResponse)
                .toList();
        return new NearbyCitiesResponse(origin.cityId(), nearbyCities);
    }

    private NearbyCityItem toResponse(NearbyCitySearch.NearbyCity nearbyCity) {
        City city = nearbyCity.city();
        WeatherForecastResponse forecast = forecastFor(city);
        return new NearbyCityItem(
                city.cityId(),
                city.city(),
                city.country(),
                Math.round(nearbyCity.distanceKm() * 10.0) / 10.0,
                forecast);
    }

    private WeatherForecastResponse forecastFor(City city) {
        try {
            return forecastService.getForecastAfterAuthorization(city);
        } catch (ForecastUnavailableException | ForecastQuotaExceededException exception) {
            LOGGER.warn("No forecast is available for nearby city {}", city.cityId(), exception);
            return null;
        }
    }
}
