package ru.vadirss.bot.service;

import ru.vadirss.bot.db.Database;
import ru.vadirss.bot.model.TeamSchedule;
import ru.vadirss.bot.util.TimeUtil;

import java.sql.*;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class ScheduleService {
    private final Database db;

    public ScheduleService(Database db) {
        this.db = db;
    }

    public Optional<TeamSchedule> findForTeamAndDay(long teamId, DayOfWeek dow) {
        int d = dow.getValue();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM schedules WHERE team_id=? AND day_of_week=?")) {
                ps.setLong(1, teamId);
                ps.setInt(2, d);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<TeamSchedule> listForTeam(long teamId) {
        List<TeamSchedule> out = new ArrayList<>();
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT * FROM schedules WHERE team_id=? ORDER BY day_of_week")) {
                ps.setLong(1, teamId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) out.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return out;
    }

    public void upsert(long teamId, int dayOfWeek, LocalTime start, LocalTime end) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO schedules(team_id, day_of_week, start_time, end_time) VALUES(?,?,?,?) " +
                            "ON CONFLICT(team_id, day_of_week) DO UPDATE SET start_time=excluded.start_time, end_time=excluded.end_time"
            )) {
                ps.setLong(1, teamId);
                ps.setInt(2, dayOfWeek);
                ps.setString(3, start.toString());
                ps.setString(4, end.toString());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static TeamSchedule map(ResultSet rs) throws SQLException {
        TeamSchedule s = new TeamSchedule();
        s.id = rs.getLong("id");
        s.teamId = rs.getLong("team_id");
        s.dayOfWeek = rs.getInt("day_of_week");
        s.startTime = TimeUtil.parseTime(rs.getString("start_time"));
        s.endTime = TimeUtil.parseTime(rs.getString("end_time"));
        return s;
    }
}
