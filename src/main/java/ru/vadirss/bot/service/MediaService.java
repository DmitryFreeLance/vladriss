package ru.vadirss.bot.service;

import ru.vadirss.bot.config.Config;
import ru.vadirss.bot.db.Database;
import ru.vadirss.bot.util.TimeUtil;

import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Optional;

public final class MediaService {

    private final Database db;
    private final Config cfg;

    public MediaService(Database db, Config cfg) {
        this.db = db;
        this.cfg = cfg;
    }

    public InputFile inputFile(String mediaKey) {
        Optional<String> cached = getCachedFileId(mediaKey);
        if (cached.isPresent()) {
            return new InputFile(cached.get());
        }
        String path = cfg.mediaResourcePath();
        if (!path.endsWith("/")) path = path + "/";
        String resource = (path.startsWith("/") ? path.substring(1) : path) + mediaKey;

        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resource);
        if (is == null) {
            // fallback: attempt with leading slash
            is = Thread.currentThread().getContextClassLoader().getResourceAsStream("/" + resource);
        }
        if (is == null) {
            throw new IllegalStateException("Media resource not found in classpath: " + resource +
                    ". Put file into src/main/resources" + (cfg.mediaResourcePath().startsWith("/") ? cfg.mediaResourcePath() : ("/" + cfg.mediaResourcePath())) +
                    "/" + mediaKey);
        }
        InputFile f = new InputFile();
        f.setMedia(is, mediaKey);
        return f;
    }

    public void cacheIfPossible(String mediaKey, Message sentMessage, ZoneId zone) {
        if (sentMessage == null) return;
        if (sentMessage.getPhoto() == null || sentMessage.getPhoto().isEmpty()) return;

        PhotoSize best = sentMessage.getPhoto().stream()
                .max(Comparator.comparing(PhotoSize::getFileSize, Comparator.nullsLast(Integer::compareTo)))
                .orElse(null);
        if (best == null) return;

        String fileId = best.getFileId();
        if (fileId == null || fileId.isBlank()) return;

        upsert(mediaKey, fileId, zone);
    }

    public Optional<String> getCachedFileId(String mediaKey) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT file_id FROM media_cache WHERE media_key=?")) {
                ps.setString(1, mediaKey);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(rs.getString("file_id"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void upsert(String mediaKey, String fileId, ZoneId zone) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO media_cache(media_key, file_id, updated_at) VALUES(?,?,?) " +
                            "ON CONFLICT(media_key) DO UPDATE SET file_id=excluded.file_id, updated_at=excluded.updated_at"
            )) {
                ps.setString(1, mediaKey);
                ps.setString(2, fileId);
                ps.setString(3, TimeUtil.nowIso(zone));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
