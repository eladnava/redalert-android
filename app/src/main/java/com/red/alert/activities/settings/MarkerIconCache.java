package com.red.alert.activities.settings;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

public class MarkerIconCache {
    private static final int CACHE_SIZE = 100; // Настройте размер кэша при необходимости

    private static final LruCache<String, BitmapDescriptor> iconCache =
            new LruCache<>(CACHE_SIZE);

    public static BitmapDescriptor getMarkerIcon(Context context, int resourceId, int color, int width, int height) {
        String key = resourceId + "_" + color + "_" + width + "_" + height;
        BitmapDescriptor icon = iconCache.get(key);
        if (icon == null) {
            try {
                icon = Utils.bitmapDescriptorFromVector(context, resourceId, color, width, height);
                if (icon != null) {
                    iconCache.put(key, icon);
                } else {
                    // В случае ошибки создаем стандартный маркер
                    icon = BitmapDescriptorFactory.defaultMarker();
                }
            } catch (Exception e) {
                // Логируем ошибку и создаем стандартный маркер
                Log.e("MarkerIconCache", "Ошибка при создании BitmapDescriptor", e);
                icon = BitmapDescriptorFactory.defaultMarker();
            }
        }
        return icon;
    }
}

