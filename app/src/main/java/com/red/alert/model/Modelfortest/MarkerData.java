package com.red.alert.model.Modelfortest;

import com.google.android.gms.maps.model.LatLng;

public class MarkerData {
    private final LatLng location;
    private final String localizedName;
    private final int iconResId;
    private final String colorHex;
    private final int width;
    private final int height;

    public MarkerData(LatLng location, String localizedName, int iconResId, String colorHex, int width, int height) {
        this.location = location;
        this.localizedName = localizedName;
        this.iconResId = iconResId;
        this.colorHex = colorHex;
        this.width = width;
        this.height = height;
    }

    public LatLng getLocation() {
        return location;
    }

    public String getLocalizedName() {
        return localizedName;
    }

    public int getIconResId() {
        return iconResId;
    }

    public String getColorHex() {
        return colorHex;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}


