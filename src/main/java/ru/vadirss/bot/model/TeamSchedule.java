package ru.vadirss.bot.model;

import java.time.LocalTime;

public final class TeamSchedule {
    public long id;
    public long teamId;
    public int dayOfWeek; // 1=Mon..7=Sun
    public LocalTime startTime;
    public LocalTime endTime;
}
