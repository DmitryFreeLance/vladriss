package ru.vadirss.bot.service;

import ru.vadirss.bot.config.Config;
import ru.vadirss.bot.db.Database;
import ru.vadirss.bot.util.TimeUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.ZoneId;

public final class CoachRatingService {

    private final Database db;
    private final Config cfg;

    public CoachRatingService(Database db, Config cfg) {
        this.db = db;
        this.cfg = cfg;
    }

    public void upsertRating(long sessionId, long playerId, int lim, int t2, int eiq) {
        ZoneId zone = cfg.zoneId();
        String now = TimeUtil.nowIso(zone);

        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO coach_ratings(session_id, player_id, lim, t2, eiq, created_at) VALUES(?,?,?,?,?,?) " +
                            "ON CONFLICT(session_id, player_id) DO UPDATE SET lim=excluded.lim, t2=excluded.t2, eiq=excluded.eiq, created_at=excluded.created_at"
            )) {
                ps.setLong(1, sessionId);
                ps.setLong(2, playerId);
                ps.setInt(3, lim);
                ps.setInt(4, t2);
                ps.setInt(5, eiq);
                ps.setString(6, now);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
