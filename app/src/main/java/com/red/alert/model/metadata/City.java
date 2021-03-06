package com.red.alert.model.metadata;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;

import java.util.List;

public class City {
    @JsonProperty("lat")
    public double latitude;

    @JsonProperty("lng")
    public double longitude;

    @JsonProperty("name")
    public String name;

    @JsonProperty("name_en")
    public String nameEnglish;

    @JsonProperty("zone")
    public String zone;

    @JsonProperty("zone_en")
    public String zoneEnglish;

    @JsonProperty("value")
    public String value;

    @JsonProperty("time")
    public String time;

    @JsonProperty("time_en")
    public String timeEnglish;

    @JsonProperty("shelters")
    public int shelters;

    @JsonProperty("countdown")
    public int countdown;

    @Override
    public String toString() {
        return value;
    }
}
