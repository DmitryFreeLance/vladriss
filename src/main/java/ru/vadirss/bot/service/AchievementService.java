package ru.vadirss.bot.service;

import com.google.gson.JsonObject;
import ru.vadirss.bot.db.Database;
import ru.vadirss.bot.model.Achievement;
import ru.vadirss.bot.model.PlayerAttributes;
import ru.vadirss.bot.model.TeamEventType;
import ru.vadirss.bot.model.User;
import ru.vadirss.bot.util.JsonUtils;
import ru.vadirss.bot.util.TimeUtil;

import java.sql.*;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public final class AchievementService {

    private final Database db;
    private final TeamEventService teamEvents;

    public AchievementService(Database db, TeamEventService teamEvents) {
        this.db = db;
        this.teamEvents = teamEvents;
    }

    public List<Achievement> listForPlayer(long playerId) {
        List<Achievement> out = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT code FROM user_achievements WHERE player_id=? ORDER BY awarded_at")) {
                ps.setLong(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Achievement a = Achievement.byCode(rs.getString("code"));
                        if (a != null) out.add(a);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public void recompute(User player, ZoneId zone) {
        if (player.role != null && player.role != ru.vadirss.bot.model.Role.PLAYER) return;
        if (player.teamId == null) return;

        Set<String> shouldHave = evaluate(player, zone);
        Set<String> hasNow = new HashSet<>();
        for (Achievement a : listForPlayer(player.tgId)) hasNow.add(a.code);

        Set<String> toAdd = new HashSet<>(shouldHave);
        toAdd.removeAll(hasNow);

        Set<String> toRemove = new HashSet<>(hasNow);
        toRemove.removeAll(shouldHave);

        if (toAdd.isEmpty() && toRemove.isEmpty()) return;

        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);

            for (String code : toAdd) {
                try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO user_achievements(player_id, code, awarded_at) VALUES(?,?,?)")) {
                    ps.setLong(1, player.tgId);
                    ps.setString(2, code);
                    ps.setString(3, TimeUtil.nowIso(zone));
                    ps.executeUpdate();
                }
            }

            for (String code : toRemove) {
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM user_achievements WHERE player_id=? AND code=?")) {
                    ps.setLong(1, player.tgId);
                    ps.setString(2, code);
                    ps.executeUpdate();
                }
            }

            c.commit();
            c.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // Team feed events only for newly awarded achievements
        for (String code : toAdd) {
            JsonObject payload = new JsonObject();
            payload.addProperty("achievement", code);
            teamEvents.addEvent(player.teamId, TeamEventType.ACHIEVEMENT_AWARDED, player.tgId, payload, zone);
        }
    }

    private Set<String> evaluate(User player, ZoneId zone) {
        PlayerAttributes a = getAttributes(player.tgId, zone);
        Set<String> out = new HashSet<>();

        // 🤝 Team player
        if (a.shortPass >= 9 && a.longPass >= 9 && a.teamwork >= 9 && a.communication >= 9) out.add(Achievement.TEAM_PLAYER.code);

        // 💪 Iron will
        if (a.ballBattle >= 9 && a.nervousness >= 9 && a.concentration >= 9) out.add(Achievement.IRON_WILL.code);

        // ⚡ Energizer
        if (a.strength >= 8 && a.flexibility >= 8 && a.speed >= 8 && a.endurance >= 8 && a.agility >= 8) out.add(Achievement.ENERGIZER.code);

        // 🧠 Tactician
        if (a.analysis >= 9 && a.positioning >= 9) out.add(Achievement.TACTICIAN.code);

        // 🗣 Leader
        if (a.leadership >= 9 && a.communication >= 9) out.add(Achievement.LEADER.code);

        // 🌅 Early bird: 5 training sessions in a row with morning poll
        if (hasMorningPollStreak(player.teamId, player.tgId, 5)) out.add(Achievement.EARLY_BIRD.code);

        // 🏆 Challenge streak: last 5 challenges completed
        if (hasChallengeStreak(player.tgId, 5)) out.add(Achievement.CHALLENGE_STREAK.code);

        // 📅 Discipline week: last 7 days, all training days answered morning poll
        if (disciplineWeek(player.teamId, player.tgId, zone)) out.add(Achievement.DISCIPLINE_WEEK.code);

        return out;
    }

    public PlayerAttributes getAttributes(long playerId, ZoneId zone) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM player_attributes WHERE player_id=?")) {
                ps.setLong(1, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        // ensure row exists
                        PlayerAttributes empty = PlayerAttributes.emptyNow(TimeUtil.nowIso(zone));
                        upsertAttributes(playerId, empty, zone, false);
                        return empty;
                    }
                    PlayerAttributes a = new PlayerAttributes();
                    a.shortPass = rs.getDouble("short_pass");
                    a.firstTouch = rs.getDouble("first_touch");
                    a.longPass = rs.getDouble("long_pass");
                    a.positioning = rs.getDouble("positioning");
                    a.heading = rs.getDouble("heading");
                    a.ballBattle = rs.getDouble("ball_battle");

                    a.strength = rs.getDouble("strength");
                    a.flexibility = rs.getDouble("flexibility");
                    a.speed = rs.getDouble("speed");
                    a.endurance = rs.getDouble("endurance");
                    a.agility = rs.getDouble("agility");

                    a.analysis = rs.getDouble("analysis");
                    a.communication = rs.getDouble("communication");
                    a.teamwork = rs.getDouble("teamwork");
                    a.concentration = rs.getDouble("concentration");
                    a.nervousness = rs.getDouble("nervousness");
                    a.leadership = rs.getDouble("leadership");

                    a.updatedAt = rs.getString("updated_at");
                    return a;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void upsertAttributes(long playerId, PlayerAttributes a, ZoneId zone, boolean writeHistory) {
        String now = TimeUtil.nowIso(zone);

        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);

            if (writeHistory) {
                JsonObject snapshot = JsonUtils.obj();
                for (var e : a.asOrderedMap().entrySet()) {
                    snapshot.addProperty(e.getKey(), e.getValue());
                }
                try (PreparedStatement ph = c.prepareStatement("INSERT INTO player_attribute_history(player_id, snapshot_json, created_at) VALUES(?,?,?)")) {
                    ph.setLong(1, playerId);
                    ph.setString(2, JsonUtils.GSON.toJson(snapshot));
                    ph.setString(3, now);
                    ph.executeUpdate();
                }
            }

            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO player_attributes(player_id, short_pass, first_touch, long_pass, positioning, heading, ball_battle," +
                            "strength, flexibility, speed, endurance, agility," +
                            "analysis, communication, teamwork, concentration, nervousness, leadership, updated_at) " +
                            "VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) " +
                            "ON CONFLICT(player_id) DO UPDATE SET " +
                            "short_pass=excluded.short_pass, first_touch=excluded.first_touch, long_pass=excluded.long_pass, positioning=excluded.positioning, heading=excluded.heading, ball_battle=excluded.ball_battle," +
                            "strength=excluded.strength, flexibility=excluded.flexibility, speed=excluded.speed, endurance=excluded.endurance, agility=excluded.agility," +
                            "analysis=excluded.analysis, communication=excluded.communication, teamwork=excluded.teamwork, concentration=excluded.concentration, nervousness=excluded.nervousness, leadership=excluded.leadership," +
                            "updated_at=excluded.updated_at"
            )) {
                ps.setLong(1, playerId);
                ps.setDouble(2, a.shortPass);
                ps.setDouble(3, a.firstTouch);
                ps.setDouble(4, a.longPass);
                ps.setDouble(5, a.positioning);
                ps.setDouble(6, a.heading);
                ps.setDouble(7, a.ballBattle);

                ps.setDouble(8, a.strength);
                ps.setDouble(9, a.flexibility);
                ps.setDouble(10, a.speed);
                ps.setDouble(11, a.endurance);
                ps.setDouble(12, a.agility);

                ps.setDouble(13, a.analysis);
                ps.setDouble(14, a.communication);
                ps.setDouble(15, a.teamwork);
                ps.setDouble(16, a.concentration);
                ps.setDouble(17, a.nervousness);
                ps.setDouble(18, a.leadership);

                ps.setString(19, now);
                ps.executeUpdate();
            }

            c.commit();
            c.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasMorningPollStreak(long teamId, long playerId, int needed) {
        // Look at latest training sessions for team and ensure morning polls exist for each.
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ts.date, (SELECT COUNT(1) FROM polls_morning pm WHERE pm.player_id=? AND pm.date=ts.date) AS has_poll " +
                            "FROM training_sessions ts WHERE ts.team_id=? ORDER BY ts.date DESC LIMIT ?"
            )) {
                ps.setLong(1, playerId);
                ps.setLong(2, teamId);
                ps.setInt(3, needed);
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        int has = rs.getInt("has_poll");
                        if (has <= 0) return false;
                        count++;
                    }
                    return count >= needed;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean hasChallengeStreak(long playerId, int needed) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT pc.status FROM player_challenges pc " +
                            "JOIN training_sessions ts ON ts.id=pc.session_id " +
                            "WHERE pc.player_id=? ORDER BY ts.date DESC LIMIT ?"
            )) {
                ps.setLong(1, playerId);
                ps.setInt(2, needed);
                try (ResultSet rs = ps.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        String status = rs.getString("status");
                        if (!"COMPLETED".equalsIgnoreCase(status)) return false;
                        count++;
                    }
                    return count >= needed;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean disciplineWeek(long teamId, long playerId, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.minusDays(7);
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT ts.date, (SELECT COUNT(1) FROM polls_morning pm WHERE pm.player_id=? AND pm.date=ts.date) AS has_poll " +
                            "FROM training_sessions ts WHERE ts.team_id=? AND ts.date>=? AND ts.date<=? ORDER BY ts.date DESC"
            )) {
                ps.setLong(1, playerId);
                ps.setLong(2, teamId);
                ps.setString(3, TimeUtil.fmt(from));
                ps.setString(4, TimeUtil.fmt(today));
                try (ResultSet rs = ps.executeQuery()) {
                    boolean hasAtLeastOne = false;
                    while (rs.next()) {
                        hasAtLeastOne = true;
                        int has = rs.getInt("has_poll");
                        if (has <= 0) return false;
                    }
                    return hasAtLeastOne;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
