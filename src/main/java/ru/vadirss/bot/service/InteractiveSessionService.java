package ru.vadirss.bot.service;

import com.google.gson.JsonObject;
import ru.vadirss.bot.db.Database;
import ru.vadirss.bot.model.InteractiveSession;
import ru.vadirss.bot.util.JsonUtils;
import ru.vadirss.bot.util.TimeUtil;

import java.sql.*;
import java.time.ZoneId;
import java.util.Optional;

public final class InteractiveSessionService {

    private final Database db;

    public InteractiveSessionService(Database db) {
        this.db = db;
    }

    public InteractiveSession create(long userId, long chatId, int messageId, String kind, JsonObject data, String expiresAt, ZoneId zone) {
        String now = TimeUtil.nowIso(zone);
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO interactive_sessions(user_id, chat_id, message_id, kind, data, created_at, updated_at, expires_at) VALUES(?,?,?,?,?,?,?,?)",
                    Statement.RETURN_GENERATED_KEYS
            )) {
                ps.setLong(1, userId);
                ps.setLong(2, chatId);
                ps.setInt(3, messageId);
                ps.setString(4, kind);
                ps.setString(5, JsonUtils.GSON.toJson(data != null ? data : new JsonObject()));
                ps.setString(6, now);
                ps.setString(7, now);
                if (expiresAt == null) ps.setNull(8, Types.VARCHAR);
                else ps.setString(8, expiresAt);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No id");
                    InteractiveSession s = new InteractiveSession();
                    s.id = keys.getLong(1);
                    s.userId = userId;
                    s.chatId = chatId;
                    s.messageId = messageId;
                    s.kind = kind;
                    s.data = data != null ? data : new JsonObject();
                    s.createdAt = now;
                    s.updatedAt = now;
                    s.expiresAt = expiresAt;
                    return s;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Optional<InteractiveSession> find(long chatId, int messageId, String kind) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT * FROM interactive_sessions WHERE chat_id=? AND message_id=? AND kind=?"
            )) {
                ps.setLong(1, chatId);
                ps.setInt(2, messageId);
                ps.setString(3, kind);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void updateData(long id, JsonObject data, ZoneId zone) {
        String now = TimeUtil.nowIso(zone);
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE interactive_sessions SET data=?, updated_at=? WHERE id=?")) {
                ps.setString(1, JsonUtils.GSON.toJson(data != null ? data : new JsonObject()));
                ps.setString(2, now);
                ps.setLong(3, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(long id) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM interactive_sessions WHERE id=?")) {
                ps.setLong(1, id);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static InteractiveSession map(ResultSet rs) throws SQLException {
        InteractiveSession s = new InteractiveSession();
        s.id = rs.getLong("id");
        s.userId = rs.getLong("user_id");
        s.chatId = rs.getLong("chat_id");
        s.messageId = rs.getInt("message_id");
        s.kind = rs.getString("kind");
        s.data = JsonUtils.parseObj(rs.getString("data"));
        s.createdAt = rs.getString("created_at");
        s.updatedAt = rs.getString("updated_at");
        s.expiresAt = rs.getString("expires_at");
        return s;
    }
}
