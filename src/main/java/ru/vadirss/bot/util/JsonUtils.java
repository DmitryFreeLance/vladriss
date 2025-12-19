package ru.vadirss.bot.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public final class JsonUtils {
    private JsonUtils() {}

    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    public static JsonObject obj() {
        return new JsonObject();
    }

    public static JsonObject parseObj(String s) {
        if (s == null || s.isBlank()) return new JsonObject();
        try {
            return GSON.fromJson(s, JsonObject.class);
        } catch (Exception e) {
            return new JsonObject();
        }
    }
}