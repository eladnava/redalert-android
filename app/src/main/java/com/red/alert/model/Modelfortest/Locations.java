package com.red.alert.model.Modelfortest;

import android.util.Pair;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.List;

public class Locations {
    public static List<Pair<LatLng, String>> getLocations() {
        List<Pair<LatLng, String>> locations = new ArrayList<>();

        // Основные города Израиля
        locations.add(new Pair<>(new LatLng(32.0852999, 34.7817676), "Tel Aviv"));
        locations.add(new Pair<>(new LatLng(31.768319, 35.21371), "Jerusalem"));
        locations.add(new Pair<>(new LatLng(32.794046, 34.989571), "Haifa"));
        locations.add(new Pair<>(new LatLng(29.557669, 34.951925), "Eilat"));
        locations.add(new Pair<>(new LatLng(32.321458, 34.853196), "Netanya"));

        // Генерация дополнительных точек для тестирования
        for (int i = 1; i <= 1000; i++) {
            double lat = 29.0 + Math.random() * (33.0 - 29.0); // Широта в пределах Израиля
            double lng = 34.0 + Math.random() * (36.0 - 34.0); // Долгота в пределах Израиля
            locations.add(new Pair<>(new LatLng(lat, lng), "Location " + i));
        }

        return locations;
    }
}