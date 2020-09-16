package com.red.alert.model.req;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;

public class SelfTestRequest {
    @JsonProperty("token")
    public String token;

    @JsonProperty("platform")
    public String platform;

    @JsonProperty("locale")
    public String locale;

    public SelfTestRequest(String token, String locale, String platform) {
        // Set FCM/Pushy registration token
        this.token = token;

        // Set the push type
        this.platform = platform;

        // Set the language for localization
        this.locale = locale;

    }
}
