package ru.vadirss.bot.service;

import com.google.gson.JsonObject;
import ru.vadirss.bot.config.Config;
import ru.vadirss.bot.db.Database;
import ru.vadirss.bot.model.EveningPoll;
import ru.vadirss.bot.model.MorningPoll;
import ru.vadirss.bot.model.TeamEventType;
import ru.vadirss.bot.util.TimeUtil;

import java.sql.*;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class PollService {

    private final Database db;
    private final Config cfg;
    private final TeamEventService teamEvents;

    public PollService(Database db, Config cfg, TeamEventService teamEvents) {
        this.db = db;
        this.cfg = cfg;
        this.teamEvents = teamEvents;
    }

    public void saveMorningPoll(Long sessionId, long playerId, int energy, int sleep, int readiness, String mood) {
        ZoneId zone = cfg.zoneId();
        String now = TimeUtil.nowIso(zone);
        String date = TimeUtil.todayIso(zone);

        Integer beforePoints = null;
        Long teamId = null;

        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);

            // read before points/team
            try (PreparedStatement ps0 = c.prepareStatement("SELECT points, team_id FROM users WHERE tg_id=?")) {
                ps0.setLong(1, playerId);
                try (ResultSet rs = ps0.executeQuery()) {
                    if (rs.next()) {
                        beforePoints = rs.getInt("points");
                        long t = rs.getLong("team_id");
                        teamId = rs.wasNull() ? null : t;
                    }
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO polls_morning(session_id, date, player_id, energy, sleep, readiness, mood, created_at) VALUES(?,?,?,?,?,?,?,?)"
            )) {
                if (sessionId == null) ps.setNull(1, Types.INTEGER);
                else ps.setLong(1, sessionId);
                ps.setString(2, date);
                ps.setLong(3, playerId);
                ps.setInt(4, energy);
                ps.setInt(5, sleep);
                ps.setInt(6, readiness);
                ps.setString(7, mood);
                ps.setString(8, now);
                ps.executeUpdate();
            }

            // +5 points
            try (PreparedStatement ps1 = c.prepareStatement("UPDATE users SET points = points + ?, updated_at=? WHERE tg_id=?")) {
                ps1.setInt(1, 5);
                ps1.setString(2, now);
                ps1.setLong(3, playerId);
                ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = c.prepareStatement("INSERT INTO points_ledger(user_id, delta, reason, ref, created_at) VALUES(?,?,?,?,?)")) {
                ps2.setLong(1, playerId);
                ps2.setInt(2, 5);
                ps2.setString(3, "MORNING_POLL");
                ps2.setString(4, "date:" + date);
                ps2.setString(5, now);
                ps2.executeUpdate();
            }

            c.commit();
            c.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Level up event (outside transaction)
        if (beforePoints != null && teamId != null) {
            var before = PointsService.LevelInfo.ofPoints(beforePoints);
            var after = PointsService.LevelInfo.ofPoints(beforePoints + 5);
            if (!before.name().equals(after.name())) {
                JsonObject payload = new JsonObject();
                payload.addProperty("from", before.name());
                payload.addProperty("to", after.name());
                payload.addProperty("points", beforePoints + 5);
                teamEvents.addEvent(teamId, TeamEventType.LEVEL_UP, playerId, payload, zone);
            }
        }
    }

    public void saveEveningPoll(Long sessionId, long playerId, int selfRating) {
        ZoneId zone = cfg.zoneId();
        String now = TimeUtil.nowIso(zone);
        String date = TimeUtil.todayIso(zone);

        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO polls_evening(session_id, date, player_id, self_rating, created_at) VALUES(?,?,?,?,?)"
            )) {
                if (sessionId == null) ps.setNull(1, Types.INTEGER);
                else ps.setLong(1, sessionId);
                ps.setString(2, date);
                ps.setLong(3, playerId);
                ps.setInt(4, selfRating);
                ps.setString(5, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public ProfileCounts counts(long playerId) {
        int morning = count("polls_morning", playerId);
        int evening = count("polls_evening", playerId);
        int challenges = countChallengesCompleted(playerId);
        return new ProfileCounts(morning, evening, challenges);
    }

    public List<MorningPoll> lastMorning(long playerId, int limit) {
        List<MorningPoll> out = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM polls_morning WHERE player_id=? ORDER BY created_at DESC LIMIT ?"
            )) {
                ps.setLong(1, playerId);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        MorningPoll p = new MorningPoll();
                        p.id = rs.getLong("id");
                        long sid = rs.getLong("session_id");
                        p.sessionId = rs.wasNull() ? null : sid;
                        p.date = rs.getString("date");
                        p.playerId = rs.getLong("player_id");
                        p.energy = rs.getInt("energy");
                        p.sleep = rs.getInt("sleep");
                        p.readiness = rs.getInt("readiness");
                        p.mood = rs.getString("mood");
                        p.createdAt = rs.getString("created_at");
                        out.add(p);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public List<EveningPoll> lastEvening(long playerId, int limit) {
        List<EveningPoll> out = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM polls_evening WHERE player_id=? ORDER BY created_at DESC LIMIT ?"
            )) {
                ps.setLong(1, playerId);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        EveningPoll p = new EveningPoll();
                        p.id = rs.getLong("id");
                        long sid = rs.getLong("session_id");
                        p.sessionId = rs.wasNull() ? null : sid;
                        p.date = rs.getString("date");
                        p.playerId = rs.getLong("player_id");
                        p.selfRating = rs.getInt("self_rating");
                        p.createdAt = rs.getString("created_at");
                        out.add(p);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    private int count(String table, long playerId) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(1) AS c FROM " + table + " WHERE player_id=?"
            )) {
                ps.setLong(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return 0;
                    return rs.getInt("c");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private int countChallengesCompleted(long playerId) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COUNT(1) AS c FROM player_challenges WHERE player_id=? AND status='COMPLETED'"
            )) {
                ps.setLong(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return 0;
                    return rs.getInt("c");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public record ProfileCounts(int morningPolls, int eveningPolls, int completedChallenges) {}
}
