package ru.vadirss.bot.service;

import ru.vadirss.bot.config.Config;
import ru.vadirss.bot.db.Database;
import ru.vadirss.bot.model.TeamSchedule;
import ru.vadirss.bot.model.TrainingSession;
import ru.vadirss.bot.util.TimeUtil;

import java.sql.*;
import java.time.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TrainingSessionService {

    private final Database db;
    private final Config cfg;
    private final ScheduleService schedules;

    public TrainingSessionService(Database db, Config cfg, ScheduleService schedules) {
        this.db = db;
        this.cfg = cfg;
        this.schedules = schedules;
    }

    public Optional<TrainingSession> findByTeamAndDate(long teamId, LocalDate date) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM training_sessions WHERE team_id=? AND date=?")) {
                ps.setLong(1, teamId);
                ps.setString(2, TimeUtil.fmt(date));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public TrainingSession getOrCreateForTodayIfTraining(long teamId) {
        ZoneId zone = cfg.zoneId();
        LocalDate today = LocalDate.now(zone);
        DayOfWeek dow = today.getDayOfWeek();
        Optional<TeamSchedule> sched = schedules.findForTeamAndDay(teamId, dow);
        if (sched.isEmpty()) return null;
        return getOrCreate(teamId, today, sched.get().startTime, sched.get().endTime);
    }

    public TrainingSession getOrCreate(long teamId, LocalDate date, LocalTime start, LocalTime end) {
        Optional<TrainingSession> existing = findByTeamAndDate(teamId, date);
        if (existing.isPresent()) return existing.get();

        ZoneId zone = cfg.zoneId();
        LocalDateTime startDt = LocalDateTime.of(date, start);
        LocalDateTime endDt = LocalDateTime.of(date, end);
        if (end.isBefore(start) || end.equals(start)) {
            endDt = endDt.plusDays(1);
        }

        String now = TimeUtil.nowIso(zone);

        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO training_sessions(team_id, date, start_datetime, end_datetime, status, created_at) VALUES(?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS
            )) {
                ps.setLong(1, teamId);
                ps.setString(2, TimeUtil.fmt(date));
                ps.setString(3, TimeUtil.fmt(startDt));
                ps.setString(4, TimeUtil.fmt(endDt));
                ps.setString(5, "PLANNED");
                ps.setString(6, now);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No id");
                    long id = keys.getLong(1);
                    TrainingSession s = new TrainingSession();
                    s.id = id;
                    s.teamId = teamId;
                    s.date = date;
                    s.startDateTime = startDt;
                    s.endDateTime = endDt;
                    s.status = "PLANNED";
                    return s;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<TrainingSession> listSessionsForDate(LocalDate date) {
        List<TrainingSession> out = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM training_sessions WHERE date=?")) {
                ps.setString(1, TimeUtil.fmt(date));
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public void setFlag(long sessionId, String flagField, boolean value) {
        String sql = "UPDATE training_sessions SET " + flagField + "=? WHERE id=?";
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                ps.setInt(1, value ? 1 : 0);
                ps.setLong(2, sessionId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static TrainingSession map(ResultSet rs) throws SQLException {
        TrainingSession s = new TrainingSession();
        s.id = rs.getLong("id");
        s.teamId = rs.getLong("team_id");
        s.date = TimeUtil.parseDate(rs.getString("date"));
        s.startDateTime = TimeUtil.parseDateTime(rs.getString("start_datetime"));
        s.endDateTime = TimeUtil.parseDateTime(rs.getString("end_datetime"));
        s.status = rs.getString("status");
        s.morningPollSent = rs.getInt("morning_poll_sent") == 1;
        s.reminderSent = rs.getInt("reminder_sent") == 1;
        s.challengesSent = rs.getInt("challenges_sent") == 1;
        s.coachPromptSent = rs.getInt("coach_prompt_sent") == 1;
        s.eveningPollSent = rs.getInt("evening_poll_sent") == 1;
        s.coachRatingSent = rs.getInt("coach_rating_sent") == 1;
        s.challengesExpired = rs.getInt("challenges_expired") == 1;
        return s;
    }
}
