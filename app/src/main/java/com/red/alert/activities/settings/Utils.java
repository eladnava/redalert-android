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

                    // Применяем цвет
                    DrawableCompat.setTint(drawable, color);
                    drawable.setBounds(0, 0, width, height); // Устанавливаем размеры
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

        // Возвращаем маркер по умолчанию в случае ошибки
        return BitmapDescriptorFactory.defaultMarker();
    }
}


