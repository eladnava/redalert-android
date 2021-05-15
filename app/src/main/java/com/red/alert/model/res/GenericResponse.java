package com.red.alert.model.res;

import me.pushy.sdk.lib.jackson.annotation.JsonProperty;

public class GenericResponse {
    @JsonProperty("success")
    public boolean success;

    @JsonProperty("error")
    public String error;
}