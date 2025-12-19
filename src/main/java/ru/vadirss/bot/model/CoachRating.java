package ru.vadirss.bot.model;

public final class CoachRating {
    public long id;
    public long sessionId;
    public long playerId;
    public int lim; // 0..4
    public int t2;  // 0..3
    public int eiq; // 0..2
    public String createdAt;
}
