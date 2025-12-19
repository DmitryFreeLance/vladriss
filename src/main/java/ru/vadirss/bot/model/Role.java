package ru.vadirss.bot.model;

public enum Role {
    PLAYER,
    COACH,
    ADMIN;

    public static Role fromDb(String v) {
        if (v == null) return PLAYER;
        try {
            return Role.valueOf(v.trim().toUpperCase());
        } catch (Exception e) {
            return PLAYER;
        }
    }
}
