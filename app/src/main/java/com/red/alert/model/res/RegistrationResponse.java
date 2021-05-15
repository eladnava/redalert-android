package com.red.alert.model.res;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;

public class RegistrationResponse {
    @JsonProperty("id")
    public long id;

    @JsonProperty("hash")
    public String hash;
}