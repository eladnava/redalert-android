package com.github.timboode.NYP_alert_android.utils.networking;

import android.util.Log;

import com.github.timboode.NYP_alert_android.config.API;
import com.github.timboode.NYP_alert_android.config.Logging;

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

        // Try each API endpoint until request is successful
        for (String endpoint : API.ENDPOINTS) {
            // Throw on error in case this is the last endpoint
            boolean isLastEndpoint = endpoint.equals(API.ENDPOINTS[API.ENDPOINTS.length - 1]);

            try {
                // Convert URL into object
                URL url = new URL(endpoint + urlString);

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
                } else {
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
                    // In case there are more endpoints, try them before throwing
                    if (!isLastEndpoint) {
                        Log.e(Logging.TAG, "HTTP GET error (" + endpoint + "): " + statusCode + "\n" + response.toString());
                        continue;
                    }

                    // Throw error
                    throw new Exception("HTTP GET error (" + endpoint + "): " + statusCode + "\n" + response.toString());
                }

                // Return response as string
                return response.toString();
            } catch (Exception exc) {
                // In case there are more endpoints, try them before throwing
                if (!isLastEndpoint) {
                    Log.e(Logging.TAG, "HTTP GET error (" + endpoint + "): " + exc.toString() + exc.getStackTrace(), exc);
                    continue;
                }

                // Throw connectivity exception
                throw new Exception("HTTP GET error (" + endpoint + "): " + exc.toString() + exc.getStackTrace(), exc);
            } finally {
                // Got an open connection?
                if (httpConnection != null) {
                    // Close it to avoid leaking resources
                    httpConnection.disconnect();
                }
            }
        }

        // Throw connectivity exception
        throw new Exception("HTTP GET error: All endpoints failed");
    }

    public static String post(String urlString, String json) throws Exception {
        // Create request
        HttpsURLConnection httpConnection = null;

        // Try each API endpoint until request is successful
        for (String endpoint : API.ENDPOINTS) {
            // Throw on error in case this is the last endpoint
            boolean isLastEndpoint = endpoint.equals(API.ENDPOINTS[API.ENDPOINTS.length - 1]);

            try {
                // Convert URL into object
                URL url = new URL(endpoint + urlString);

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
                } else {
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

                // Error?
                if (statusCode != 200) {
                    // In case there are more endpoints, try them before throwing
                    if (!isLastEndpoint) {
                        Log.e(Logging.TAG, "HTTP POST error (" + endpoint + "): " + statusCode + "\n" + response.toString());
                        continue;
                    }

                    // Throw error
                    throw new Exception("HTTP POST error (" + endpoint + "): " + statusCode + "\n" + response.toString());
                }

                // Return response as string
                return response.toString();
            } catch (Exception exc) {
                // In case there are more endpoints, try them before throwing
                if (!isLastEndpoint) {
                    Log.e(Logging.TAG, "HTTP POST error (" + endpoint + "): " + exc.toString() + exc.getStackTrace(), exc);
                    continue;
                }

                // Throw connectivity exception
                throw new Exception("HTTP POST error (" + endpoint + "): " + exc.toString() + exc.getStackTrace(), exc);
            } finally {
                // Got an open connection?
                if (httpConnection != null) {
                    // Close it to avoid leaking resources
                    httpConnection.disconnect();
                }
            }
        }

        // Throw connectivity exception
        throw new Exception("HTTP POST error: All endpoints failed");
    }
}
