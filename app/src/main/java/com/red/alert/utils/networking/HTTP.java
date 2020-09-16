package com.red.alert.utils.networking;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class HTTP {
    public static String get(String urlString) throws Exception {
        // Create request
        HttpsURLConnection httpConnection = null;

        try {
            // Convert URL into object
            URL url = new URL(urlString);

            // Prepare request
            httpConnection = (HttpsURLConnection) url.openConnection();

            // Set general properties
            httpConnection.setUseCaches(false);
            httpConnection.setRequestMethod("GET");

            // Execute request
            httpConnection.connect();

            // Execute request and get response code
            int statusCode = httpConnection.getResponseCode();

            // Prepare input stream
            InputStream inputStream;

            // 200 OK?
            if (statusCode == 200) {
                // Use standard stream
                inputStream = httpConnection.getInputStream();
            }
            else {
                // Use error stream
                inputStream = httpConnection.getErrorStream();
            }

            // Prepare buffered reader
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            // Prepare response buffer holder
            StringBuffer response = new StringBuffer();

            // Temporary line placeholder
            String tmpLine;

            // Read from reader until end of stream
            while ((tmpLine = reader.readLine()) != null) {
                // Append line
                response.append(tmpLine);
            }

            // Close reader
            reader.close();

            // Error?
            if (statusCode != 200) {
                // Throw it out
                throw new Exception("HTTP GET error: " + statusCode + "\n" + response.toString());
            }

            // Return response as string
            return response.toString();
        } catch (Exception exc) {
            // Throw connectivity exception
            throw new Exception(exc.toString() + exc.getStackTrace());
        }
        finally {
            // Got an open connection?
            if (httpConnection != null) {
                // Close it to avoid leaking resources
                httpConnection.disconnect();
            }
        }
    }

    public static String post(String urlString, String json) throws Exception {
        // Create request
        HttpsURLConnection httpConnection = null;

        try {
            // Convert URL into object
            URL url = new URL(urlString);

            // Prepare request
            httpConnection = (HttpsURLConnection) url.openConnection();

            // Set JSON headers
            httpConnection.setRequestProperty("Accept", "application/json");
            httpConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            // Set general properties
            httpConnection.setDoInput(true);
            httpConnection.setDoOutput(true);
            httpConnection.setUseCaches(false);
            httpConnection.setRequestMethod("POST");

            // Send JSON post body
            OutputStream outputStream = httpConnection.getOutputStream();
            outputStream.write(json.getBytes("UTF-8"));
            outputStream.close();

            // Execute request and get response code
            int statusCode = httpConnection.getResponseCode();

            // Prepare input stream
            InputStream inputStream;

            // 200 OK?
            if (statusCode == 200) {
                // Use standard stream
                inputStream = httpConnection.getInputStream();
            }
            else {
                // Use error stream
                inputStream = httpConnection.getErrorStream();
            }

            // Prepare buffered reader
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            // Prepare response buffer holder
            StringBuffer response = new StringBuffer();

            // Temporary line placeholder
            String tmpLine;

            // Read from reader until end of stream
            while ((tmpLine = reader.readLine()) != null) {
                // Append line
                response.append(tmpLine);
            }

            // Close reader
            reader.close();

            // Error?
            if (statusCode != 200) {
                // Throw it out
                throw new Exception("HTTP POST error: " + statusCode + "\n" + response.toString());
            }

            // Return response as string
            return response.toString();
        } catch (Exception exc) {
            // Throw connectivity exception
            throw new Exception(exc.toString() + exc.getStackTrace());
        }
        finally {
            // Got an open connection?
            if (httpConnection != null) {
                // Close it to avoid leaking resources
                httpConnection.disconnect();
            }
        }
    }
}
