package com.red.alert.model.req;

import java.util.List;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;

public class SubscribeRequest {
    @JsonProperty("uid")
    public long uid;

    @JsonProperty("hash")
    public String hash;

    @JsonProperty("primary")
    public String[] primary;

    @JsonProperty("secondary")
    public String[] secondary;

    public SubscribeRequest(long uid, String hash, List<String> primarySubscriptions, List<String> secondarySubscriptions) {
        // Set members
        this.uid = uid;
        this.hash = hash;
        this.primary = primarySubscriptions.toArray(new String[0]);
        this.secondary = secondarySubscriptions.toArray(new String[0]);
    }
}
