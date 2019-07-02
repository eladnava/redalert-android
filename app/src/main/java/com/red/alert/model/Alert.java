package com.red.alert.model;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;

public class Alert {
    @JsonProperty("date")
    public long date;

    @JsonProperty("area")
    public String city;
    public String localizedCity;

    public String desc;
    public String dateString;

    @Override
    public String toString() {
        return city;
    }
}
