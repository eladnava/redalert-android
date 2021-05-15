package com.red.alert.model.req;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;

public class UpdateNotificationsRequest {
    @JsonProperty("uid")
    public long uid;

    @JsonProperty("hash")
    public String hash;

    @JsonProperty("primary")
    public String primary;

    @JsonProperty("secondary")
    public String secondary;

    public UpdateNotificationsRequest(long uid, String hash, boolean primary, boolean secondary) {
        // Set members
        this.uid = uid;
        this.hash = hash;
        this.primary = primary ? "1" : "0";
        this.secondary = secondary ? "1" : "0";
    }
}
