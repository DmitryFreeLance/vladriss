package ru.vadirss.bot.model;

import com.google.gson.JsonObject;

public final class InteractiveSession {
    public long id;
    public long userId;
    public long chatId;
    public int messageId;
    public String kind;
    public JsonObject data;
    public String createdAt;
    public String updatedAt;
    public String expiresAt;
}
