package ru.vadirss.bot.service;

import ru.vadirss.bot.db.Database;
import ru.vadirss.bot.model.Team;
import ru.vadirss.bot.util.TimeUtil;

import java.sql.*;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TeamService {
    private final Database db;

    public TeamService(Database db) {
        this.db = db;
    }

    public List<Team> listTeams() {
        List<Team> out = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM teams ORDER BY name")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public Optional<Team> findById(long id) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM teams WHERE id=?")) {
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

    public Team createTeam(String name, ZoneId zone) {
        String now = TimeUtil.nowIso(zone);
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO teams(name, created_at) VALUES(?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, name.trim());
                ps.setString(2, now);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("No id returned");
                    Team t = new Team();
                    t.id = keys.getLong(1);
                    t.name = name.trim();
                    t.createdAt = now;
                    return t;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void deleteTeam(long teamId) {
        try (Connection c = db.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps1 = c.prepareStatement("DELETE FROM schedules WHERE team_id=?")) {
                ps1.setLong(1, teamId);
                ps1.executeUpdate();
            }
            try (PreparedStatement ps2 = c.prepareStatement("DELETE FROM team_coaches WHERE team_id=?")) {
                ps2.setLong(1, teamId);
                ps2.executeUpdate();
            }
            try (PreparedStatement ps3 = c.prepareStatement("DELETE FROM teams WHERE id=?")) {
                ps3.setLong(1, teamId);
                ps3.executeUpdate();
            }
            // orphan players: set team_id NULL
            try (PreparedStatement ps4 = c.prepareStatement("UPDATE users SET team_id=NULL WHERE team_id=?")) {
                ps4.setLong(1, teamId);
                ps4.executeUpdate();
            }
            c.commit();
            c.setAutoCommit(true);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void assignCoach(long teamId, long coachId) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR IGNORE INTO team_coaches(team_id, coach_id) VALUES(?,?)")) {
                ps.setLong(1, teamId);
                ps.setLong(2, coachId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void removeCoach(long teamId, long coachId) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("DELETE FROM team_coaches WHERE team_id=? AND coach_id=?")) {
                ps.setLong(1, teamId);
                ps.setLong(2, coachId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<Team> listTeamsForCoach(long coachId) {
        List<Team> out = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT t.* FROM teams t JOIN team_coaches tc ON tc.team_id=t.id WHERE tc.coach_id=? ORDER BY t.name"
            )) {
                ps.setLong(1, coachId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    private static Team map(ResultSet rs) throws SQLException {
        Team t = new Team();
        t.id = rs.getLong("id");
        t.name = rs.getString("name");
        t.createdAt = rs.getString("created_at");
        return t;
    }
}
