package ru.vadirss.bot.service;

import com.google.gson.JsonObject;
import ru.vadirss.bot.config.Config;
import ru.vadirss.bot.db.Database;
import ru.vadirss.bot.model.PlayerChallenge;
import ru.vadirss.bot.model.Team;
import ru.vadirss.bot.model.TeamEventType;
import ru.vadirss.bot.model.User;
import ru.vadirss.bot.util.TimeUtil;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public final class ChallengeService {

    private final Database db;
    private final Config cfg;
    private final AiService ai;
    private final PointsService pointsService;
    private final TeamEventService teamEvents;
    private final AchievementService achievements;
    private final Random rnd = new Random();

    public ChallengeService(Database db, Config cfg, AiService ai, PointsService pointsService, TeamEventService teamEvents, AchievementService achievements) {
        this.db = db;
        this.cfg = cfg;
        this.ai = ai;
        this.pointsService = pointsService;
        this.teamEvents = teamEvents;
        this.achievements = achievements;
    }

    public void addCoachPoolChallenge(long teamId, String text, ZoneId zone) {
        String now = TimeUtil.nowIso(zone);
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO coach_challenge_pool(team_id, text, created_at) VALUES(?,?,?)")) {
                ps.setLong(1, teamId);
                ps.setString(2, text.trim());
                ps.setString(3, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void assignChallengesForSession(long teamId, long sessionId, List<User> players) {
        ZoneId zone = cfg.zoneId();
        Team team = getTeam(teamId).orElseThrow(() -> new IllegalStateException("Team not found: " + teamId));

        List<String> coachPool = listCoachPool(teamId);
        for (User p : players) {
            if (p.teamId == null || p.teamId != teamId) continue;
            if (existsForSession(sessionId, p.tgId)) continue;

            boolean fromCoachPool = !coachPool.isEmpty() && rnd.nextBoolean(); // ~50%
            String text;
            String source;
            if (fromCoachPool) {
                text = coachPool.get(rnd.nextInt(coachPool.size()));
                source = "COACH";
            } else {
                text = ai.generateChallenge(p, team);
                source = "AI";
            }
            createChallenge(sessionId, p.tgId, text, source, zone);
        }
    }

    public Optional<PlayerChallenge> getChallengeForPlayer(long sessionId, long playerId) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM player_challenges WHERE session_id=? AND player_id=?")) {
                ps.setLong(1, sessionId);
                ps.setLong(2, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<PlayerChallenge> listChallengesForSession(long sessionId) {
        List<PlayerChallenge> out = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM player_challenges WHERE session_id=? ORDER BY id")) {
                ps.setLong(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public void markChallenge(long challengeId, boolean completed, long coachId) {
        ZoneId zone = cfg.zoneId();
        PlayerChallenge ch = getById(challengeId).orElseThrow();
        if (!"PENDING".equalsIgnoreCase(ch.status)) {
            return; // already processed
        }

        String newStatus = completed ? "COMPLETED" : "FAILED";
        String now = TimeUtil.nowIso(zone);

        // Before level
        User player = getUser(ch.playerId).orElseThrow();
        PointsService.LevelInfo beforeLevel = pointsService.levelOf(player);

        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);

            try (PreparedStatement ps = c.prepareStatement("UPDATE player_challenges SET status=?, marked_by=?, marked_at=? WHERE id=?")) {
                ps.setString(1, newStatus);
                ps.setLong(2, coachId);
                ps.setString(3, now);
                ps.setLong(4, challengeId);
                ps.executeUpdate();
            }

            if (completed) {
                // +25 points
                try (PreparedStatement ps1 = c.prepareStatement("UPDATE users SET points = points + ?, updated_at=? WHERE tg_id=?")) {
                    ps1.setInt(1, 25);
                    ps1.setString(2, now);
                    ps1.setLong(3, ch.playerId);
                    ps1.executeUpdate();
                }
                try (PreparedStatement ps2 = c.prepareStatement("INSERT INTO points_ledger(user_id, delta, reason, ref, created_at) VALUES(?,?,?,?,?)")) {
                    ps2.setLong(1, ch.playerId);
                    ps2.setInt(2, 25);
                    ps2.setString(3, "CHALLENGE");
                    ps2.setString(4, "challenge:" + challengeId);
                    ps2.setString(5, now);
                    ps2.executeUpdate();
                }
            }

            c.commit();
            c.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        // After level
        User playerAfter = getUser(ch.playerId).orElseThrow();
        PointsService.LevelInfo afterLevel = pointsService.levelOf(playerAfter);

        if (completed) {
            // Team event: challenge completed
            if (playerAfter.teamId != null) {
                JsonObject payload = new JsonObject();
                payload.addProperty("challenge", ch.text);
                teamEvents.addEvent(playerAfter.teamId, TeamEventType.CHALLENGE_COMPLETED, playerAfter.tgId, payload, zone);
            }
        }

        if (!beforeLevel.name().equals(afterLevel.name()) && playerAfter.teamId != null) {
            JsonObject payload = new JsonObject();
            payload.addProperty("from", beforeLevel.name());
            payload.addProperty("to", afterLevel.name());
            payload.addProperty("points", playerAfter.points);
            teamEvents.addEvent(playerAfter.teamId, TeamEventType.LEVEL_UP, playerAfter.tgId, payload, zone);
        }

        // Recompute achievements (challenge streak etc)
        achievements.recompute(playerAfter, zone);
    }

    public void expirePendingChallenges(long sessionId) {
        ZoneId zone = cfg.zoneId();
        String now = TimeUtil.nowIso(zone);
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE player_challenges SET status='EXPIRED', marked_at=? WHERE session_id=? AND status='PENDING'"
            )) {
                ps.setString(1, now);
                ps.setLong(2, sessionId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // --- internals ---
    private boolean existsForSession(long sessionId, long playerId) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM player_challenges WHERE session_id=? AND player_id=?")) {
                ps.setLong(1, sessionId);
                ps.setLong(2, playerId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void createChallenge(long sessionId, long playerId, String text, String source, ZoneId zone) {
        String now = TimeUtil.nowIso(zone);
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO player_challenges(session_id, player_id, text, source, status, created_at) VALUES(?,?,?,?,?,?)"
            )) {
                ps.setLong(1, sessionId);
                ps.setLong(2, playerId);
                ps.setString(3, text.trim());
                ps.setString(4, source);
                ps.setString(5, "PENDING");
                ps.setString(6, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private List<String> listCoachPool(long teamId) {
        List<String> out = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT text FROM coach_challenge_pool WHERE team_id=? ORDER BY id DESC LIMIT 200")) {
                ps.setLong(1, teamId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(rs.getString("text"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    private Optional<PlayerChallenge> getById(long id) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM player_challenges WHERE id=?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<User> getUser(long tgId) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE tg_id=?")) {
                ps.setLong(1, tgId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    // Minimal mapping
                    User u = new User();
                    u.tgId = rs.getLong("tg_id");
                    u.chatId = rs.getLong("chat_id");
                    u.role = ru.vadirss.bot.model.Role.fromDb(rs.getString("role"));
                    u.consent = rs.getInt("consent") == 1;
                    u.fullName = rs.getString("full_name");
                    u.phone = rs.getString("phone");
                    long teamId = rs.getLong("team_id");
                    u.teamId = rs.wasNull() ? null : teamId;
                    u.position = rs.getString("position");
                    u.points = rs.getInt("points");
                    return Optional.of(u);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private Optional<Team> getTeam(long teamId) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM teams WHERE id=?")) {
                ps.setLong(1, teamId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    Team t = new Team();
                    t.id = rs.getLong("id");
                    t.name = rs.getString("name");
                    t.createdAt = rs.getString("created_at");
                    return Optional.of(t);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static PlayerChallenge map(ResultSet rs) throws SQLException {
        PlayerChallenge c = new PlayerChallenge();
        c.id = rs.getLong("id");
        c.sessionId = rs.getLong("session_id");
        c.playerId = rs.getLong("player_id");
        c.text = rs.getString("text");
        c.source = rs.getString("source");
        c.status = rs.getString("status");
        long mb = rs.getLong("marked_by");
        c.markedBy = rs.wasNull() ? null : mb;
        String markedAt = rs.getString("marked_at");
        if (markedAt != null) c.markedAt = LocalDateTime.parse(markedAt);
        c.createdAt = LocalDateTime.parse(rs.getString("created_at"));
        return c;
    }
}
