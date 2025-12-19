package ru.vadirss.bot.model;

public final class TeamEvent {
    public long id;
    public long teamId;
    public String createdAt;
    public TeamEventType type;
    public Long userId;
    public String payloadJson;
}
