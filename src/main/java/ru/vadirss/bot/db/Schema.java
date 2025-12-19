package ru.vadirss.bot.db;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class Schema {

    private Schema() {}

    public static void migrate(Database db) throws SQLException {
        try (Connection c = db.getConnection()) {
            try (Statement st = c.createStatement()) {

                st.execute("CREATE TABLE IF NOT EXISTS users (" +
                        "tg_id INTEGER PRIMARY KEY," +
                        "chat_id INTEGER NOT NULL," +
                        "role TEXT NOT NULL DEFAULT 'PLAYER'," +
                        "consent INTEGER NOT NULL DEFAULT 0," +
                        "full_name TEXT," +
                        "phone TEXT," +
                        "team_id INTEGER," +
                        "position TEXT," +
                        "points INTEGER NOT NULL DEFAULT 0," +
                        "state TEXT NOT NULL DEFAULT 'WAIT_CONSENT'," +
                        "state_data TEXT NOT NULL DEFAULT '{}'," +
                        "created_at TEXT NOT NULL," +
                        "updated_at TEXT NOT NULL" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS teams (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL UNIQUE," +
                        "created_at TEXT NOT NULL" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS team_coaches (" +
                        "team_id INTEGER NOT NULL," +
                        "coach_id INTEGER NOT NULL," +
                        "PRIMARY KEY(team_id, coach_id)" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS schedules (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "team_id INTEGER NOT NULL," +
                        "day_of_week INTEGER NOT NULL," +
                        "start_time TEXT NOT NULL," +
                        "end_time TEXT NOT NULL," +
                        "UNIQUE(team_id, day_of_week)" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS training_sessions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "team_id INTEGER NOT NULL," +
                        "date TEXT NOT NULL," +
                        "start_datetime TEXT NOT NULL," +
                        "end_datetime TEXT NOT NULL," +
                        "status TEXT NOT NULL DEFAULT 'PLANNED'," +
                        "morning_poll_sent INTEGER NOT NULL DEFAULT 0," +
                        "reminder_sent INTEGER NOT NULL DEFAULT 0," +
                        "challenges_sent INTEGER NOT NULL DEFAULT 0," +
                        "coach_prompt_sent INTEGER NOT NULL DEFAULT 0," +
                        "evening_poll_sent INTEGER NOT NULL DEFAULT 0," +
                        "coach_rating_sent INTEGER NOT NULL DEFAULT 0," +
                        "challenges_expired INTEGER NOT NULL DEFAULT 0," +
                        "created_at TEXT NOT NULL," +
                        "UNIQUE(team_id, date)" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS coach_challenge_pool (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "team_id INTEGER NOT NULL," +
                        "text TEXT NOT NULL," +
                        "created_at TEXT NOT NULL" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS users (" +
                        "tg_id INTEGER PRIMARY KEY," +
                        "chat_id INTEGER NOT NULL," +
                        "role TEXT NOT NULL DEFAULT 'PLAYER'," +
                        "consent INTEGER NOT NULL DEFAULT 0," +
                        "full_name TEXT," +
                        "phone TEXT," +
                        "team_id INTEGER," +
                        "position TEXT," +
                        "points INTEGER NOT NULL DEFAULT 0," +
                        "state TEXT NOT NULL DEFAULT 'WAIT_CONSENT'," +
                        "state_data TEXT NOT NULL DEFAULT '{}'," +
                        "created_at TEXT NOT NULL," +
                        "updated_at TEXT NOT NULL" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS teams (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "name TEXT NOT NULL UNIQUE," +
                        "created_at TEXT NOT NULL" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS player_challenges (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "session_id INTEGER NOT NULL," +
                        "player_id INTEGER NOT NULL," +
                        "text TEXT NOT NULL," +
                        "source TEXT NOT NULL," +
                        "status TEXT NOT NULL DEFAULT 'PENDING'," +
                        "marked_by INTEGER," +
                        "marked_at TEXT," +
                        "created_at TEXT NOT NULL," +
                        "UNIQUE(session_id, player_id)" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS polls_morning (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "session_id INTEGER," +
                        "date TEXT NOT NULL," +
                        "player_id INTEGER NOT NULL," +
                        "energy INTEGER NOT NULL," +
                        "sleep INTEGER NOT NULL," +
                        "readiness INTEGER NOT NULL," +
                        "mood TEXT NOT NULL," +
                        "created_at TEXT NOT NULL" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS polls_evening (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "session_id INTEGER," +
                        "date TEXT NOT NULL," +
                        "player_id INTEGER NOT NULL," +
                        "self_rating INTEGER NOT NULL," +
                        "created_at TEXT NOT NULL" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS coach_ratings (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "session_id INTEGER NOT NULL," +
                        "player_id INTEGER NOT NULL," +
                        "lim INTEGER NOT NULL," +
                        "t2 INTEGER NOT NULL," +
                        "eiq INTEGER NOT NULL," +
                        "created_at TEXT NOT NULL," +
                        "UNIQUE(session_id, player_id)" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS player_attributes (" +
                        "player_id INTEGER PRIMARY KEY," +
                        "short_pass REAL DEFAULT 0," +
                        "first_touch REAL DEFAULT 0," +
                        "long_pass REAL DEFAULT 0," +
                        "positioning REAL DEFAULT 0," +
                        "heading REAL DEFAULT 0," +
                        "ball_battle REAL DEFAULT 0," +
                        "strength REAL DEFAULT 0," +
                        "flexibility REAL DEFAULT 0," +
                        "speed REAL DEFAULT 0," +
                        "endurance REAL DEFAULT 0," +
                        "agility REAL DEFAULT 0," +
                        "analysis REAL DEFAULT 0," +
                        "communication REAL DEFAULT 0," +
                        "teamwork REAL DEFAULT 0," +
                        "concentration REAL DEFAULT 0," +
                        "nervousness REAL DEFAULT 0," +
                        "leadership REAL DEFAULT 0," +
                        "updated_at TEXT NOT NULL" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS player_attribute_history (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "player_id INTEGER NOT NULL," +
                        "snapshot_json TEXT NOT NULL," +
                        "created_at TEXT NOT NULL" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS user_achievements (" +
                        "player_id INTEGER NOT NULL," +
                        "code TEXT NOT NULL," +
                        "awarded_at TEXT NOT NULL," +
                        "PRIMARY KEY(player_id, code)" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS team_events (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "team_id INTEGER NOT NULL," +
                        "created_at TEXT NOT NULL," +
                        "type TEXT NOT NULL," +
                        "user_id INTEGER," +
                        "payload TEXT NOT NULL DEFAULT '{}' " +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS points_ledger (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "user_id INTEGER NOT NULL," +
                        "delta INTEGER NOT NULL," +
                        "reason TEXT NOT NULL," +
                        "ref TEXT," +
                        "created_at TEXT NOT NULL" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS daily_quotes (" +
                        "date TEXT PRIMARY KEY," +
                        "text TEXT NOT NULL," +
                        "source TEXT NOT NULL," +
                        "created_at TEXT NOT NULL" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS media_cache (" +
                        "media_key TEXT PRIMARY KEY," +
                        "file_id TEXT NOT NULL," +
                        "updated_at TEXT NOT NULL" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS team_notifications (" +
                        "team_id INTEGER NOT NULL," +
                        "date TEXT NOT NULL," +
                        "quote_sent INTEGER NOT NULL DEFAULT 0," +
                        "PRIMARY KEY(team_id, date)" +
                        ");");

                st.execute("CREATE TABLE IF NOT EXISTS interactive_sessions (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "user_id INTEGER NOT NULL," +
                        "chat_id INTEGER NOT NULL," +
                        "message_id INTEGER NOT NULL," +
                        "kind TEXT NOT NULL," +
                        "data TEXT NOT NULL," +
                        "created_at TEXT NOT NULL," +
                        "updated_at TEXT NOT NULL," +
                        "expires_at TEXT," +
                        "UNIQUE(chat_id, message_id, kind)" +
                        ");");
            }
        }
    }
}
