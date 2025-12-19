package ru.vadirss.bot.service;

import com.google.gson.JsonObject;
import ru.vadirss.bot.db.Database;
import ru.vadirss.bot.model.Role;
import ru.vadirss.bot.model.User;
import ru.vadirss.bot.model.UserState;
import ru.vadirss.bot.util.JsonUtils;
import ru.vadirss.bot.util.TimeUtil;

import java.sql.*;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class UserService {
    private final Database db;

    public UserService(Database db) {
        this.db = db;
    }

    public User getOrCreate(long tgId, long chatId, ZoneId zone) {
        Optional<User> u = findById(tgId);
        if (u.isPresent()) {
            // keep chatId up-to-date (user can change it when writing in groups / etc)
            if (u.get().chatId != chatId) {
                setChatId(tgId, chatId, zone);
                u.get().chatId = chatId;
            }
            return u.get();
        }
        String now = TimeUtil.nowIso(zone);
        User nu = new User();
        nu.tgId = tgId;
        nu.chatId = chatId;
        nu.role = Role.PLAYER;
        nu.consent = false;
        nu.state = UserState.WAIT_CONSENT;
        nu.stateData = new JsonObject();
        nu.createdAt = now;
        nu.updatedAt = now;

        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO users(tg_id, chat_id, role, consent, state, state_data, created_at, updated_at) VALUES(?,?,?,?,?,?,?,?)"
            )) {
                ps.setLong(1, tgId);
                ps.setLong(2, chatId);
                ps.setString(3, nu.role.name());
                ps.setInt(4, 0);
                ps.setString(5, nu.state.name());
                ps.setString(6, JsonUtils.GSON.toJson(nu.stateData));
                ps.setString(7, now);
                ps.setString(8, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return nu;
    }

    public Optional<User> findById(long tgId) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE tg_id=?")) {
                ps.setLong(1, tgId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(mapUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<User> listUsers(int offset, int limit) {
        List<User> out = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM users ORDER BY tg_id LIMIT ? OFFSET ?")) {
                ps.setInt(1, limit);
                ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(mapUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public List<User> listAdmins(int offset, int limit) {
        List<User> out = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE role='ADMIN' ORDER BY tg_id LIMIT ? OFFSET ?")) {
                ps.setInt(1, limit);
                ps.setInt(2, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(mapUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public List<User> listPlayersByTeam(long teamId) {
        List<User> out = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM users WHERE role='PLAYER' AND team_id=? ORDER BY full_name")) {
                ps.setLong(1, teamId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(mapUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public List<User> listCoachesByTeam(long teamId) {
        List<User> out = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT u.* FROM users u JOIN team_coaches tc ON tc.coach_id=u.tg_id WHERE tc.team_id=? ORDER BY u.full_name"
            )) {
                ps.setLong(1, teamId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(mapUser(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public void setChatId(long tgId, long chatId, ZoneId zone) {
        updateField(tgId, "chat_id", chatId, zone);
    }

    public void setConsent(long tgId, boolean consent, ZoneId zone) {
        updateField(tgId, "consent", consent ? 1 : 0, zone);
    }

    public void setFullName(long tgId, String fullName, ZoneId zone) {
        updateField(tgId, "full_name", fullName, zone);
    }

    public void setPhone(long tgId, String phone, ZoneId zone) {
        updateField(tgId, "phone", phone, zone);
    }

    public void setTeam(long tgId, Long teamId, ZoneId zone) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE users SET team_id=?, updated_at=? WHERE tg_id=?")) {
                if (teamId == null) ps.setNull(1, Types.INTEGER);
                else ps.setLong(1, teamId);
                ps.setString(2, TimeUtil.nowIso(zone));
                ps.setLong(3, tgId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setPosition(long tgId, String position, ZoneId zone) {
        updateField(tgId, "position", position, zone);
    }

    public void setRole(long tgId, Role role, ZoneId zone) {
        updateField(tgId, "role", role.name(), zone);
    }

    public void setState(long tgId, UserState state, JsonObject stateData, ZoneId zone) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("UPDATE users SET state=?, state_data=?, updated_at=? WHERE tg_id=?")) {
                ps.setString(1, state.name());
                ps.setString(2, JsonUtils.GSON.toJson(stateData != null ? stateData : new JsonObject()));
                ps.setString(3, TimeUtil.nowIso(zone));
                ps.setLong(4, tgId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void addPoints(long tgId, int delta, String reason, String ref, ZoneId zone) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps1 = c.prepareStatement("UPDATE users SET points = points + ?, updated_at=? WHERE tg_id=?")) {
                ps1.setInt(1, delta);
                ps1.setString(2, TimeUtil.nowIso(zone));
                ps1.setLong(3, tgId);
                ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = c.prepareStatement("INSERT INTO points_ledger(user_id, delta, reason, ref, created_at) VALUES(?,?,?,?,?)")) {
                ps2.setLong(1, tgId);
                ps2.setInt(2, delta);
                ps2.setString(3, reason);
                if (ref == null) ps2.setNull(4, Types.VARCHAR);
                else ps2.setString(4, ref);
                ps2.setString(5, TimeUtil.nowIso(zone));
                ps2.executeUpdate();
            }
            c.commit();
            c.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateField(long tgId, String field, Object value, ZoneId zone) {
        String sql = "UPDATE users SET " + field + "=?, updated_at=? WHERE tg_id=?";
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (value == null) ps.setNull(1, Types.VARCHAR);
                else if (value instanceof String s) ps.setString(1, s);
                else if (value instanceof Integer i) ps.setInt(1, i);
                else if (value instanceof Long l) ps.setLong(1, l);
                else ps.setString(1, value.toString());
                ps.setString(2, TimeUtil.nowIso(zone));
                ps.setLong(3, tgId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static User mapUser(ResultSet rs) throws SQLException {
        User u = new User();
        u.tgId = rs.getLong("tg_id");
        u.chatId = rs.getLong("chat_id");
        u.role = Role.fromDb(rs.getString("role"));
        u.consent = rs.getInt("consent") == 1;
        u.fullName = rs.getString("full_name");
        u.phone = rs.getString("phone");
        long teamId = rs.getLong("team_id");
        u.teamId = rs.wasNull() ? null : teamId;
        u.position = rs.getString("position");
        u.points = rs.getInt("points");
        u.state = UserState.valueOf(rs.getString("state"));
        u.stateData = JsonUtils.parseObj(rs.getString("state_data"));
        u.createdAt = rs.getString("created_at");
        u.updatedAt = rs.getString("updated_at");
        return u;
    }
}
