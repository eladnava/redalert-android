package com.red.alert.model;

import java.util.List;

import me.pushy.sdk.lib.jackson.annotation.JsonIgnore;
import me.pushy.sdk.lib.jackson.annotation.JsonProperty;

public class Alert {
    @JsonProperty("date")
    public long date;

    @JsonProperty("area")
    public String city;

    @JsonProperty("threat")
    public String threat;

    // These fields are computed client-side but need to be serialized
    // when passing alerts between fragments
    @JsonProperty("localizedCity")
    public String localizedCity;
    
    @JsonProperty("localizedZone")
    public String localizedZone;
    
    @JsonProperty("localizedThreat")
    public String localizedThreat;

    @JsonProperty("desc")
    public String desc;
    
    @JsonProperty("dateString")
    public String dateString;

    @JsonIgnore
    public List<Alert> groupedAlerts;

    @JsonIgnore
    public List<String> groupedDescriptions;

    @JsonIgnore
    public List<String> groupedLocalizedCities;

    @Override
    public String toString() {
        return city;
    }
}
