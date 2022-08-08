package com.red.alert.model.req;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;

public class RegistrationRequest {
    @JsonProperty("platform")
    public String platform;

    @JsonProperty("token")
    public String fcmToken;

    @JsonProperty("pushyToken")
    public String pushyToken;

    public RegistrationRequest(String fcmToken, String pushyToken, String platform) {
        // Set members
        this.platform = platform;
        this.fcmToken = fcmToken;
        this.pushyToken = pushyToken;
    }
}
