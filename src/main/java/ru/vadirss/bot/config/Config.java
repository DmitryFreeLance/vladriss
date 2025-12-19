package ru.vadirss.bot.config;

import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Objects;

public final class Config {
    private final String botToken;
    private final String botUsername;
    private final Path dbPath;
    private final ZoneId zoneId;

    // Timeweb Agent (OpenAI-compatible)
    private final String timewebBaseUrl;      // e.g. https://<your-agent-endpoint>
    private final String timewebApiToken;     // key/token
    private final String timewebAuthHeader;   // default Authorization
    private final String timewebAuthPrefix;   // default Bearer

    // Scheduler
    private final int schedulerIntervalSeconds;

    // Media
    private final String mediaResourcePath; // classpath folder, default /media
    private final int maxMessageLen;

    private Config(
            String botToken,
            String botUsername,
            Path dbPath,
            ZoneId zoneId,
            String timewebBaseUrl,
            String timewebApiToken,
            String timewebAuthHeader,
            String timewebAuthPrefix,
            int schedulerIntervalSeconds,
            String mediaResourcePath,
            int maxMessageLen
    ) {
        this.botToken = Objects.requireNonNull(botToken);
        this.botUsername = Objects.requireNonNull(botUsername);
        this.dbPath = Objects.requireNonNull(dbPath);
        this.zoneId = Objects.requireNonNull(zoneId);
        this.timewebBaseUrl = Objects.requireNonNull(timewebBaseUrl);
        this.timewebApiToken = Objects.requireNonNull(timewebApiToken);
        this.timewebAuthHeader = Objects.requireNonNull(timewebAuthHeader);
        this.timewebAuthPrefix = Objects.requireNonNull(timewebAuthPrefix);
        this.schedulerIntervalSeconds = schedulerIntervalSeconds;
        this.mediaResourcePath = Objects.requireNonNull(mediaResourcePath);
        this.maxMessageLen = maxMessageLen;
    }

    public static Config load() {
        String botToken = get("BOT_TOKEN", "");
        String botUsername = get("BOT_USERNAME", "vadirss_bot");
        String dbPath = get("DB_PATH", "./data/bot.db");
        String tz = get("BOT_TIMEZONE", "Europe/Moscow");

        String timewebBase = get("TIMEWEB_BASE_URL", "");
        String timewebToken = get("TIMEWEB_API_TOKEN", "");
        String timewebAuthHeader = get("TIMEWEB_AUTH_HEADER", "Authorization");
        String timewebAuthPrefix = get("TIMEWEB_AUTH_PREFIX", "Bearer ");

        int schedulerIntervalSeconds = getInt("SCHEDULER_INTERVAL_SECONDS", 30);

        String mediaPath = get("MEDIA_CLASSPATH_DIR", "/media");
        int maxLen = getInt("MAX_MESSAGE_LEN", 3900);

        if (botToken.isBlank()) {
            System.err.println("BOT_TOKEN is empty. Set env BOT_TOKEN or VM option -DBOT_TOKEN=...");
            // We do not exit hard because user may want to run tests; but for production you want to exit.
        }

        return new Config(
                botToken,
                botUsername,
                Path.of(dbPath),
                ZoneId.of(tz),
                timewebBase,
                timewebToken,
                timewebAuthHeader,
                timewebAuthPrefix,
                schedulerIntervalSeconds,
                mediaPath,
                maxLen
        );
    }

    private static String get(String key, String def) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env;
        String prop = System.getProperty(key);
        if (prop != null && !prop.isBlank()) return prop;
        return def;
    }

    private static int getInt(String key, int def) {
        String v = get(key, "");
        if (v.isBlank()) return def;
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return def;
        }
    }

    public String botToken() { return botToken; }
    public String botUsername() { return botUsername; }
    public Path dbPath() { return dbPath; }
    public ZoneId zoneId() { return zoneId; }

    public String timewebBaseUrl() { return timewebBaseUrl; }
    public String timewebApiToken() { return timewebApiToken; }
    public String timewebAuthHeader() { return timewebAuthHeader; }
    public String timewebAuthPrefix() { return timewebAuthPrefix; }

    public int schedulerIntervalSeconds() { return schedulerIntervalSeconds; }
    public String mediaResourcePath() { return mediaResourcePath; }
    public int maxMessageLen() { return maxMessageLen; }
}
