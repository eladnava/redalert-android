package com.red.alert.utils.networking;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;

public class HTTP {
    public static String get(String URL) throws Exception {
        // Get custom http client
        DefaultHttpClient client = getHTTPClient();

        // Create GET request
        HttpGet request = new HttpGet(URL);

        // Execute the request
        HttpResponse response = client.execute(request, new BasicHttpContext());

        // Return response as string
        String responseText = EntityUtils.toString(response.getEntity());

        // Failed?
        if (response.getStatusLine() == null || response.getStatusLine().getStatusCode() != 200) {
            // Throw it out
            throw new Exception(response.getStatusLine().toString() + "\n" + responseText);
        }

        // We're good
        return responseText;
    }

    public static String post(String url, String json) throws Exception {
        // Get custom http client
        HttpClient client = new DefaultHttpClient();

        // Create post request
        HttpPost request = new HttpPost(url);

        // Set content type to JSON
        request.addHeader("Content-Type", "application/json");

        // Send JSON as string
        request.setEntity(new StringEntity(json));

        // Execute the request
        HttpResponse response = client.execute(request, new BasicHttpContext());

        // Return response as string
        String responseText = EntityUtils.toString(response.getEntity());

        // Failed?
        if (response.getStatusLine() == null || response.getStatusLine().getStatusCode() != 200) {
            // Throw it out
            throw new Exception(response.getStatusLine().toString() + "\n" + responseText);
        }

        // We're good
        return responseText;
    }

    public static DefaultHttpClient getHTTPClient() {
        // Return default client
        return new DefaultHttpClient();
    }
}
