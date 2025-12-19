package ru.vadirss.bot.service;

import ru.vadirss.bot.db.Database;
import ru.vadirss.bot.model.User;
import ru.vadirss.bot.util.TimeUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;

public final class PointsService {

    private final Database db;

    public PointsService(Database db) {
        this.db = db;
    }

    public int getTodayPoints(long userId, ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        String d = TimeUtil.fmt(today);
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT COALESCE(SUM(delta),0) AS s FROM points_ledger WHERE user_id=? AND substr(created_at,1,10)=?"
            )) {
                ps.setLong(1, userId);
                ps.setString(2, d);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return 0;
                    return rs.getInt("s");
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public LevelInfo levelOf(User u) {
        return LevelInfo.ofPoints(u.points);
    }

    public record LevelInfo(String emoji, String name, int from, int to) {
        public static LevelInfo ofPoints(int points) {
            if (points <= 100) return new LevelInfo("🐣", "Новичок", 0, 100);
            if (points <= 300) return new LevelInfo("🌱", "Развивающийся", 101, 300);
            if (points <= 600) return new LevelInfo("💎", "Профи", 301, 600);
            if (points <= 900) return new LevelInfo("🦁", "Лидер", 601, 900);
            return new LevelInfo("🏅", "Капитан", 901, Integer.MAX_VALUE);
        }

        public String label() {
            if (to == Integer.MAX_VALUE) {
                return emoji + " " + name + " (901+)";
            }
            return emoji + " " + name + " (" + from + "–" + to + ")";
        }
    }
}
