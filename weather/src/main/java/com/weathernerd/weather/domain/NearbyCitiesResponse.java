package com.weathernerd.weather.domain;

import java.util.List;

public record NearbyCitiesResponse(String originCityId, List<NearbyCityItem> nearbyCities) {
    public NearbyCitiesResponse {
        nearbyCities = List.copyOf(nearbyCities);
    }
}
