package ru.vadirss.bot.service;

import ru.vadirss.bot.db.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class NotificationService {

    private final Database db;

    public NotificationService(Database db) {
        this.db = db;
    }

    public boolean isQuoteSent(long teamId, String dateIso) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT quote_sent FROM team_notifications WHERE team_id=? AND date=?")) {
                ps.setLong(1, teamId);
                ps.setString(2, dateIso);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    return rs.getInt("quote_sent") == 1;
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void markQuoteSent(long teamId, String dateIso) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO team_notifications(team_id, date, quote_sent) VALUES(?,?,1) " +
                            "ON CONFLICT(team_id, date) DO UPDATE SET quote_sent=1"
            )) {
                ps.setLong(1, teamId);
                ps.setString(2, dateIso);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
