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

    public String localizedCity;
    public String localizedThreat;

    public String desc;
    public String dateString;

    @JsonIgnore
    public List<Alert> groupedAlerts;

    @Override
    public String toString() {
        return city;
    }
}
