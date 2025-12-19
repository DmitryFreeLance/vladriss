package ru.vadirss.bot.model;

import java.time.LocalDateTime;

public final class PlayerChallenge {
    public long id;
    public long sessionId;
    public long playerId;
    public String text;
    public String source; // AI or COACH
    public String status; // PENDING, COMPLETED, FAILED, EXPIRED
    public Long markedBy;
    public LocalDateTime markedAt;
    public LocalDateTime createdAt;
}
