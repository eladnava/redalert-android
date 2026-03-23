package com.red.alert.model;

import java.util.List;

import me.pushy.sdk.lib.jackson.annotation.JsonIgnore;
import me.pushy.sdk.lib.jackson.annotation.JsonProperty;

public class Alert {
    @JsonProperty("id")
    public int id;

    @JsonProperty("date")
    public long date;

    @JsonProperty("area")
    public String city;

    @JsonProperty("threat")
    public String threat;

    public String localizedTitle;
    public String localizedCity;
    public String localizedZone;
    public String localizedThreat;

    public String desc;
    public String dateString;

    @JsonIgnore
    public List<Alert> groupedAlerts;

    @JsonIgnore
    public List<String> groupedDescriptions;

    @JsonIgnore
    public List<String> groupedCities;

    public boolean isExpanded;

    public long firstGroupedAlertTimestamp;

    @Override
    public String toString() {
        return city;
    }
}
