package com.github.timboode.NYP_alert_android.utils.formatting;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils {
    public static boolean stringIsNullOrEmpty(String inputString) {
        // String is null? return true
        if (inputString == null) {
            return true;
        }

        // String is empty? true
        if (inputString.trim().equals("")) {
            return true;
        }

        // String is not empty
        return false;
    }

    public static String capitalize(String str) {
        // Need at least 2 characters to proceed
        if (str.length() < 2) {
            return str;
        }

        // Capitalize string
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public static String implode(String separator, List<String> data) {
        // No data?
        if (data.size() == 0) {
            return "";
        }

        // Create temp StringBuilder
        StringBuilder builder = new StringBuilder();

        // Loop over list except last item
        for (int i = 0; i < data.size() - 1; i++) {
            // Append current item
            builder.append(data.get(i));

            // Append separator
            builder.append(separator);
        }

        // Add last item without separator
        builder.append(data.get(data.size() - 1));

        // Return string
        return builder.toString();
    }

    public static String regexMatch(String source, String compilePattern) {
        // Compile regex
        Pattern compiledPattern = Pattern.compile(compilePattern);

        // Match against source input
        Matcher matcher = compiledPattern.matcher(source);

        // Prepare match object
        String match = "";

        // Did we find anything?
        if (matcher.find()) {
            // Get first group
            match = matcher.group(1);
        }

        // Return match
        return match;
    }
}
