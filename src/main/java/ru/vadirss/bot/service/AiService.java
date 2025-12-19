package ru.vadirss.bot.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import ru.vadirss.bot.config.Config;
import ru.vadirss.bot.db.Database;
import ru.vadirss.bot.model.Team;
import ru.vadirss.bot.model.User;
import ru.vadirss.bot.util.JsonUtils;
import ru.vadirss.bot.util.TimeUtil;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public final class AiService {

    private final Config cfg;
    private final Database db;
    private final HttpClient http;
    private final Random rnd = new Random();

    // Prompts
    private static final String SYSTEM_PROMPT =
            "Ты — спортивный помощник футбольной академии vadirss.ru.\n" +
            "Пиши на русском, дружелюбно, мотивационно, кратко, с уместными эмодзи.\n" +
            "Не используй мат, не упоминай политику. Не добавляй ссылки.\n";

    private static final List<String> FALLBACK_QUOTES = List.of(
            "💥 Сегодня ты на шаг ближе к лучшей версии себя. Работай и верь! 💪",
            "⚽️ Побеждает не талант, а дисциплина. Начни с малого — сделай это идеально.",
            "🔥 Тренировка — это инвестиция. Делай вклад каждый день!",
            "🏆 Чем тяжелее сегодня — тем сильнее ты завтра. Давай! 🚀",
            "🌱 Маленький прогресс каждый день дает большой результат. Продолжай!"
    );

    private static final List<String> FALLBACK_CHALLENGES = List.of(
            "🎯 Сделай 5 точных передач на тренировке.",
            "🤝 Помоги 2 товарищам улучшить технику: подскажи и покажи.",
            "🔥 Возглавь разминку на одной из тренировок.",
            "🧠 Проанализируй свою частую ошибку и исправь ее в игре.",
            "⚡ Добавь 3 ускорения на максимуме в игровых упражнениях."
    );

    public AiService(Config cfg, Database db) {
        this.cfg = cfg;
        this.db = db;
        this.http = HttpClient.newHttpClient();
    }

    public String getOrCreateDailyQuote(LocalDate date, ZoneId zone) {
        Optional<String> cached = getQuote(date);
        if (cached.isPresent()) return cached.get();

        String prompt = "Сгенерируй 1 короткую мотивационную цитату дня для футболиста (1–2 предложения). " +
                "Добавь 1–2 эмодзи в конце. Не используй кавычки «».";

        String text = tryGenerate(prompt);
        if (text == null || text.isBlank()) {
            text = FALLBACK_QUOTES.get(rnd.nextInt(FALLBACK_QUOTES.size()));
            saveQuote(date, text, "FALLBACK", zone);
        } else {
            text = normalizeOneLine(text);
            saveQuote(date, text, "TIMEWEB", zone);
        }
        return text;
    }

    public String generateChallenge(User player, Team team) {
        String prompt = "Сгенерируй ОДИН челлендж для футболиста на ближайшую тренировку. " +
                "Челлендж должен быть конкретным, измеримым и кратким (1 строка). " +
                "Примеры: " +
                "- \"Сделай 3 точные передачи в каждом упражнении\"; " +
                "- \"Возглавь разминку\"; " +
                "- \"Отдай 2 голевые передачи\". " +
                "Контекст игрока: ФИО=" + safe(player.fullName) +
                ", позиция=" + safe(player.position) +
                ", команда=" + safe(team.name) + ".";

        String text = tryGenerate(prompt);
        if (text == null || text.isBlank()) {
            return FALLBACK_CHALLENGES.get(rnd.nextInt(FALLBACK_CHALLENGES.size()));
        }
        return normalizeOneLine(text);
    }

    private Optional<String> getQuote(LocalDate date) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("SELECT text FROM daily_quotes WHERE date=?")) {
                ps.setString(1, TimeUtil.fmt(date));
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return Optional.empty();
                    return Optional.of(rs.getString("text"));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void saveQuote(LocalDate date, String text, String source, ZoneId zone) {
        try (Connection c = db.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement("INSERT OR REPLACE INTO daily_quotes(date, text, source, created_at) VALUES(?,?,?,?)")) {
                ps.setString(1, TimeUtil.fmt(date));
                ps.setString(2, text);
                ps.setString(3, source);
                ps.setString(4, TimeUtil.nowIso(zone));
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private String tryGenerate(String userPrompt) {
        if (cfg.timewebBaseUrl().isBlank() || cfg.timewebApiToken().isBlank()) {
            return null;
        }
        try {
            String endpoint = cfg.timewebBaseUrl();
            if (endpoint.endsWith("/")) endpoint = endpoint.substring(0, endpoint.length() - 1);
            // OpenAI-compatible Chat Completions
            String url = endpoint + "/v1/chat/completions";

            JsonObject body = new JsonObject();
            body.addProperty("model", "ignored");
            JsonArray messages = new JsonArray();

            JsonObject sys = new JsonObject();
            sys.addProperty("role", "system");
            sys.addProperty("content", SYSTEM_PROMPT.trim());
            messages.add(sys);

            JsonObject usr = new JsonObject();
            usr.addProperty("role", "user");
            usr.addProperty("content", userPrompt);
            messages.add(usr);

            body.add("messages", messages);
            body.addProperty("temperature", 0.8);
            body.addProperty("max_tokens", 256);

            HttpRequest.Builder req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json");

            req.header(cfg.timewebAuthHeader(), cfg.timewebAuthPrefix() + cfg.timewebApiToken());

            HttpRequest request = req.POST(HttpRequest.BodyPublishers.ofString(JsonUtils.GSON.toJson(body))).build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                return null;
            }

            JsonObject json = JsonUtils.GSON.fromJson(resp.body(), JsonObject.class);
            if (json == null) return null;

            JsonArray choices = json.getAsJsonArray("choices");
            if (choices == null || choices.isEmpty()) return null;
            JsonObject choice0 = choices.get(0).getAsJsonObject();
            if (choice0 == null) return null;
            JsonObject message = choice0.getAsJsonObject("message");
            if (message == null) return null;
            String content = message.get("content").getAsString();
            return content;
        } catch (Exception e) {
            return null;
        }
    }

    private static String normalizeOneLine(String s) {
        String t = s.trim();
        String[] lines = t.split("\\r?\\n");
        for (String line : lines) {
            String l = line.trim();
            if (l.isEmpty()) continue;
            l = l.replaceAll("^[\\-•*\\d.\\)\\s]+", "").trim();
            l = l.replaceAll("^\"|\"$", "");
            l = l.replaceAll("^«|»$", "");
            if (!l.isEmpty()) return l;
        }
        return t;
    }

    private static String safe(String s) {
        return s == null ? "" : s.trim();
    }
}
