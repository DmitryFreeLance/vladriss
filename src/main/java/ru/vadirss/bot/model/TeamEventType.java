package ru.vadirss.bot.model;

public enum TeamEventType {
    ACHIEVEMENT_AWARDED,
    CHALLENGE_COMPLETED,
    LEVEL_UP;

    public static TeamEventType fromDb(String v) {
        if (v == null) return ACHIEVEMENT_AWARDED;
        try {
            return TeamEventType.valueOf(v.trim().toUpperCase());
        } catch (Exception e) {
            return ACHIEVEMENT_AWARDED;
        }
    }
}
