package com.red.alert.activities.settings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

/**
 * Utility class for working with icons and creating BitmapDescriptor objects
 * for custom map markers (Google Maps API).
 *
 * The main purpose of this method is to generate a BitmapDescriptor from a
 * vector resource (VectorDrawable) or BitmapDrawable, with the ability to resize
 * and tint it with a specific color.
 *
 * Method: bitmapDescriptorFromVector
 *
 * - Parameters:
 *   - `Context context`: the application context, used to access resources.
 *   - `Object icon`: the icon resource, which can be either a resource ID (R.drawable)
 *     or a BitmapDrawable.
 *   - `int color`: the color to apply to the vector resource.
 *   - `int width`: the desired width of the icon.
 *   - `int height`: the desired height of the icon.
 *
 * - Logic:
 *   1. Validates the input parameters (`context` and `icon`) to ensure they are not null.
 *      If either is null, it returns a default marker.
 *   2. If `icon` is an integer (resource ID):
 *      - Loads the vector resource as a Drawable.
 *      - Wraps and tints the vector Drawable with the specified color.
 *      - Sets the bounds and creates a Bitmap to draw the Drawable.
 *      - Converts the Bitmap to a BitmapDescriptor.
 *   3. If `icon` is a BitmapDrawable:
 *      - Scales the BitmapDrawable to the specified width and height.
 *      - Converts it to a BitmapDescriptor.
 *   4. In case of an error (OutOfMemoryError or general Exception), it prints the
 *      stack trace and returns a default marker.
 *
 * - Returns:
 *   - A `BitmapDescriptor` object, which can be used to set custom markers on a map.
 *
 * Notes:
 * - This method is useful for handling custom marker icons, especially when the icon
 *   is initially provided as a vector resource.
 * - It enables customization of marker styles (color and size) to align with the app's design.
 */

public class Utils {

    public static BitmapDescriptor bitmapDescriptorFromVector(
            Context context,
            Object icon,
            int color,
            int width,
            int height
    ) {
        if (context == null || icon == null) {
            // Проверяем входные данные
            return BitmapDescriptorFactory.defaultMarker();
        }

        try {
            if (icon instanceof Integer) {
                int iconId = (Integer) icon;
                Drawable vectorDrawable = ContextCompat.getDrawable(context, iconId);

                if (vectorDrawable != null) {
                    Drawable drawable = DrawableCompat.wrap(vectorDrawable).mutate();


                    DrawableCompat.setTint(drawable, color);
                    drawable.setBounds(0, 0, width, height);
                    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bitmap);
                    drawable.draw(canvas);
                    return BitmapDescriptorFactory.fromBitmap(bitmap);
                }
            } else if (icon instanceof BitmapDrawable) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
                Bitmap bitmap = Bitmap.createScaledBitmap(bitmapDrawable.getBitmap(), width, height, true);
                return BitmapDescriptorFactory.fromBitmap(bitmap);
            }
        } catch (OutOfMemoryError e) {

            e.printStackTrace();
        } catch (Exception e) {

            e.printStackTrace();
        }


        return BitmapDescriptorFactory.defaultMarker();
    }
}


