package ru.vadirss.bot.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TrainingSession {
    public long id;
    public long teamId;
    public LocalDate date;
    public LocalDateTime startDateTime;
    public LocalDateTime endDateTime;
    public String status;

    public boolean morningPollSent;
    public boolean reminderSent;
    public boolean challengesSent;
    public boolean coachPromptSent;
    public boolean eveningPollSent;
    public boolean coachRatingSent;
    public boolean challengesExpired;
}
