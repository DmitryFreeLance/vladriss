package ru.vadirss.bot.model;

public final class MorningPoll {
    public long id;
    public Long sessionId;
    public String date; // yyyy-MM-dd
    public long playerId;
    public int energy;
    public int sleep;
    public int readiness;
    public String mood;
    public String createdAt;
}
