package com.red.alert.model.req;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;

public class UpdateTokenRequest {
    @JsonProperty("uid")
    public long uid;

    @JsonProperty("hash")
    public String hash;

    @JsonProperty("token")
    public String fcmToken;

    @JsonProperty("pushyToken")
    public String pushyToken;

    public UpdateTokenRequest(long uid, String hash, String fcmToken, String pushyToken) {
        // Set members
        this.uid = uid;
        this.hash = hash;
        this.fcmToken = fcmToken;
        this.pushyToken = pushyToken;
    }
}
