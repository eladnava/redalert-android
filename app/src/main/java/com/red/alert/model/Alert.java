package com.red.alert.model;

import java.util.List;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;

public class Alert {
    @JsonProperty("date")
    public long date;

    @JsonProperty("area")
    public String city;

    @JsonProperty("threat")
    public String threat;

    public String localizedCity;
    public String localizedThreat;

    public String desc;
    public String dateString;

    public List<String> groupedCities;

    @Override
    public String toString() {
        return city;
    }
}
