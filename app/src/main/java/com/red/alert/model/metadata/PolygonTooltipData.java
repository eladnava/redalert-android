package com.red.alert.model.metadata;

import com.google.android.gms.maps.model.LatLng;

public class PolygonTooltipData {
    public LatLng location;
    public String localizedName;
    public String tooltip;

    public PolygonTooltipData(LatLng location, String localizedName, String tooltip) {
        this.tooltip = tooltip;
        this.location = location;
        this.localizedName = localizedName;
    }
}
