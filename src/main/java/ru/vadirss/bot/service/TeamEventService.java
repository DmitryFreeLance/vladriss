package ru.vadirss.bot.service;

import com.google.gson.JsonObject;
import ru.vadirss.bot.db.Database;
import ru.vadirss.bot.model.TeamEvent;
import ru.vadirss.bot.model.TeamEventType;
import ru.vadirss.bot.util.JsonUtils;
import ru.vadirss.bot.util.TimeUtil;

import java.sql.*;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public final class TeamEventService {

    private final Database db;

    public TeamEventService(Database db) {
        this.db = db;
    }

    public void addEvent(long teamId, TeamEventType type, Long userId, JsonObject payload, ZoneId zone) {
        String now = TimeUtil.nowIso(zone);
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO team_events(team_id, created_at, type, user_id, payload) VALUES(?,?,?,?,?)"
            )) {
                ps.setLong(1, teamId);
                ps.setString(2, now);
                ps.setString(3, type.name());
                if (userId == null) ps.setNull(4, Types.INTEGER);
                else ps.setLong(4, userId);
                ps.setString(5, JsonUtils.GSON.toJson(payload != null ? payload : new JsonObject()));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<TeamEvent> lastEvents(long teamId, int limit) {
        List<TeamEvent> out = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM team_events WHERE team_id=? ORDER BY created_at DESC LIMIT ?"
            )) {
                ps.setLong(1, teamId);
                ps.setInt(2, limit);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        TeamEvent ev = new TeamEvent();
                        ev.id = rs.getLong("id");
                        ev.teamId = rs.getLong("team_id");
                        ev.createdAt = rs.getString("created_at");
                        ev.type = TeamEventType.fromDb(rs.getString("type"));
                        long uid = rs.getLong("user_id");
                        ev.userId = rs.wasNull() ? null : uid;
                        ev.payloadJson = rs.getString("payload");
                        out.add(ev);
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }
}
