package com.limelight.binding.input.virtual_controller;

import org.json.JSONException;
import org.json.JSONObject;

/** Lightweight metadata for a saved on-screen-controller layout. */
public final class OscProfile {
    public static final String DEFAULT_ID = "default";

    private final String id;
    private String name;

    public OscProfile(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isDefault() {
        return DEFAULT_ID.equals(id);
    }

    JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("id", id);
        object.put("name", name);
        return object;
    }

    static OscProfile fromJson(JSONObject object) throws JSONException {
        String id = object.getString("id");
        String name = object.optString("name", "OSC Profile");
        return new OscProfile(id, name);
    }
}
