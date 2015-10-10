package com.red.alert.model.res;


import com.fasterxml.jackson.annotation.JsonProperty;

public class VersionInfo
{
    @JsonProperty("version")
    public String version;

    @JsonProperty("version_code")
    public int versionCode;

    @JsonProperty("show_dialog")
    public boolean showDialog;

    @Override
    public String toString()
    {
        return version;
    }
}