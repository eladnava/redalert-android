package com.red.alert.model.metadata;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;

import java.util.List;

public class City {
    @JsonProperty("id")
    public int id;

    @JsonProperty("lat")
    public double latitude;

    @JsonProperty("lng")
    public double longitude;

    @JsonProperty("name")
    public String name;

    @JsonProperty("name_en")
    public String nameEnglish;

    @JsonProperty("name_ru")
    public String nameRussian;

    @JsonProperty("name_ar")
    public String nameArabic;

    @JsonProperty("zone")
    public String zone;

    @JsonProperty("zone_en")
    public String zoneEnglish;

    @JsonProperty("zone_ru")
    public String zoneRussian;

    @JsonProperty("zone_ar")
    public String zoneArabic;

    @JsonProperty("value")
    public String value;

    @JsonProperty("shelters")
    public int shelters;

    @JsonProperty("countdown")
    public int countdown;

    @Override
    public String toString() {
        return value;
    }
}
