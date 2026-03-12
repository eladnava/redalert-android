package com.red.alert.model;

import android.text.Spanned;

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
    public String localizedZone;
    public String localizedThreat;

    public Spanned localizedCityHtml;

    public String desc;
    public String dateString;

    @JsonIgnore
    public List<Alert> groupedAlerts;

    @JsonIgnore
    public List<String> groupedDescriptions;

    @JsonIgnore
    public List<String> groupedLocalizedCities;

    public boolean isExpanded;
    public boolean isExpandableAlert;

    @Override
    public String toString() {
        return city;
    }
}
