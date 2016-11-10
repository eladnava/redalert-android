package com.red.alert.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Alert {
    @JsonProperty("date")
    public long date;

    @JsonProperty("area")
    public String zone;
    public String localizedZone;

    public String cities;
    public String dateString;

    @Override
    public String toString() {
        return zone;
    }
}
