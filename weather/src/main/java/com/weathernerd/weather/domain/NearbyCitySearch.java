package com.weathernerd.weather.domain;

import java.util.Comparator;
import java.util.List;

public final class NearbyCitySearch {
    private static final double MAXIMUM_DISTANCE_KM = 200.0;
    private static final double EARTH_MEAN_RADIUS_KM = 6_371.0088;
    private static final Comparator<NearbyCity> ORDER = Comparator
            .comparingDouble(NearbyCity::distanceKm)
            .thenComparing(nearbyCity -> nearbyCity.city().cityId());

    private NearbyCitySearch() {}

    public static List<NearbyCity> within200Km(City origin, List<City> cities) {
        return cities.stream()
                .filter(city -> !city.cityId().equals(origin.cityId()))
                .map(city -> new NearbyCity(city, distanceKm(origin, city)))
                .filter(nearbyCity -> nearbyCity.distanceKm() <= MAXIMUM_DISTANCE_KM)
                .sorted(ORDER)
                .toList();
    }

    private static double distanceKm(City first, City second) {
        double firstLatitude = Math.toRadians(first.latitude());
        double secondLatitude = Math.toRadians(second.latitude());
        double latitudeDifference = secondLatitude - firstLatitude;
        double longitudeDifference = Math.toRadians(second.longitude() - first.longitude());
        double sinLatitudeDifference = Math.sin(latitudeDifference / 2.0);
        double sinLongitudeDifference = Math.sin(longitudeDifference / 2.0);
        double haversine = sinLatitudeDifference * sinLatitudeDifference
                + Math.cos(firstLatitude)
                        * Math.cos(secondLatitude)
                        * sinLongitudeDifference
                        * sinLongitudeDifference;
        double boundedHaversine = Math.min(1.0, Math.max(0.0, haversine));
        return 2.0 * EARTH_MEAN_RADIUS_KM * Math.asin(Math.sqrt(boundedHaversine));
    }

    public record NearbyCity(City city, double distanceKm) {}
}
