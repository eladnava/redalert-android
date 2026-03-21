package com.red.alert.utils.ui;

import android.text.Layout;
import android.widget.TextView;

public class TextViewUtil {
    public static boolean isEllipsized(TextView textView) {
        // Safeguard against NPE
        if (textView == null) {
            return false;
        }

        // Get TextView layout
        Layout layout = textView.getLayout();

        // Got a valid layout?
        if (layout != null) {
            // Get displayed line count
            int lines = textView.getLineCount();

            // Traverse lines
            for (int i = 0; i < lines; i++) {
                // Any ellipsis occurring on this line?
                if (layout.getEllipsisCount(i) > 0) {
                    return true;
                }
            }
        }

        // Default to false
        return false;
    }
}