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

public class Utils {

    public static BitmapDescriptor bitmapDescriptorFromVector(
            Context context,
            Object icon,
            String colorString,
            int width,
            int height
    ) {
        if (icon instanceof Integer) {
            int iconId = (Integer) icon;
            Drawable vectorDrawable = ContextCompat.getDrawable(context, iconId);
            if (vectorDrawable != null) {
                Drawable drawable = DrawableCompat.wrap(vectorDrawable).mutate();

                // Convert color from string to ARGB
                int color = fromHex(colorString);

                DrawableCompat.setTint(drawable, color);
                drawable.setBounds(0, 0, width, height); // Set the specified size
                Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                Canvas canvas = new Canvas(bitmap);
                drawable.draw(canvas);
                return BitmapDescriptorFactory.fromBitmap(bitmap);
            }
        } else if (icon instanceof BitmapDrawable) {
            BitmapDrawable bitmapDrawable = (BitmapDrawable) icon;
            Bitmap bitmap = Bitmap.createScaledBitmap(bitmapDrawable.getBitmap(), width, height, false);
            return BitmapDescriptorFactory.fromBitmap(bitmap);
        } else {
            // Handle other types of sources if needed
        }

        // Return a default marker in case of error
        return BitmapDescriptorFactory.defaultMarker();
    }


    public static int fromHex(String colorString) {
        // Remove # if present
        String colorWithoutHash = colorString.startsWith("#") ? colorString.substring(1) : colorString;

        // Check the length of the string
        if (colorWithoutHash.length() != 6 && colorWithoutHash.length() != 8) {
            throw new IllegalArgumentException("Invalid hex color string: " + colorString);
        }

        // Add # if it was not present
        colorWithoutHash = colorWithoutHash.length() == 6 ? "FF" + colorWithoutHash : colorWithoutHash;
        String colorWithHash = "#" + colorWithoutHash;

        // Convert string to color
        return Color.parseColor(colorWithHash);
    }
}
