package com.red.alert.activities.settings;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

/**
 * Utility class for caching and generating BitmapDescriptor objects for map markers.
 *
 * The purpose of this class is to optimize the creation of custom marker icons by
 * storing them in an in-memory cache, reducing redundant processing and improving performance.
 *
 * Class: MarkerIconCache
 *
 * - Constants:
 *   - `CACHE_SIZE`: The maximum number of marker icons that can be stored in the cache.
 *
 * - Fields:
 *   - `iconCache`: An instance of LruCache that maps unique keys to BitmapDescriptor objects.
 *     It is used to store previously created marker icons for reuse.
 *
 * - Method: getMarkerIcon
 *   - Parameters:
 *     - `Context context`: Application context, used for creating marker icons.
 *     - `int resourceId`: The resource ID of the marker icon (e.g., R.drawable.icon).
 *     - `int color`: The color to tint the icon.
 *     - `int width`: The desired width of the icon.
 *     - `int height`: The desired height of the icon.
 *   - Logic:
 *     1. Constructs a unique key based on the `resourceId`, `color`, `width`, and `height`.
 *     2. Checks if the icon for the given key already exists in the cache:
 *        - If found, retrieves and returns the cached icon.
 *        - If not found, creates a new BitmapDescriptor using the `Utils.bitmapDescriptorFromVector` method.
 *     3. Adds the newly created icon to the cache for future reuse.
 *     4. Handles errors gracefully by returning a default marker if any exception occurs.
 *
 * - Returns:
 *   - A `BitmapDescriptor` object, either retrieved from the cache or newly created.
 *
 * Notes:
 * - This class is particularly useful for applications that display a large number of markers
 *   with custom icons, as it minimizes redundant computations and memory usage.
 * - By caching marker icons, it ensures faster rendering and smoother user experience in map-based applications.
 */

public class MarkerIconCache {
    private static final int CACHE_SIZE = 100;

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

                Log.e("MarkerIconCache", "Error while creating BitmapDescriptor", e);
                icon = BitmapDescriptorFactory.defaultMarker();
            }
        }
        return icon;
    }
}

