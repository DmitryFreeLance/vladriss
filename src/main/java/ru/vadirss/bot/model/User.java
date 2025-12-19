package ru.vadirss.bot.model;

import com.google.gson.JsonObject;

public final class User {
    public long tgId;
    public long chatId;

    public Role role = Role.PLAYER;

    public boolean consent;
    public String fullName;
    public String phone;

    public Long teamId;
    public String position;

    public int points;

    public UserState state = UserState.WAIT_CONSENT;
    public JsonObject stateData = new JsonObject();

    public String createdAt;
    public String updatedAt;

    public boolean isRegistered() {
        return consent && fullName != null && !fullName.isBlank() && phone != null && !phone.isBlank() && teamId != null && position != null && !position.isBlank();
    }
}
