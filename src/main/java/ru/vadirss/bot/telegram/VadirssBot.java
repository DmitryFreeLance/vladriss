package ru.vadirss.bot.telegram;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.ParseMode;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageCaption;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.vadirss.bot.config.Config;
import ru.vadirss.bot.model.*;
import ru.vadirss.bot.model.User;
import ru.vadirss.bot.service.BotFacade;
import ru.vadirss.bot.util.Html;
import ru.vadirss.bot.util.JsonUtils;
import ru.vadirss.bot.util.TextChunker;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

public final class VadirssBot extends TelegramLongPollingBot {

    private final Config cfg;
    private final BotFacade facade;

    private static final Pattern PHONE = Pattern.compile("^\\+7 \\([0-9]{3}\\) [0-9]{3}-[0-9]{2}-[0-9]{2}$");

    // Media keys in resources (/media)
    private static final String PHOTO_0 = "0.jpg";
    private static final String PHOTO_1 = "1.jpg";
    private static final String PHOTO_2 = "2.jpg";
    private static final String PHOTO_3 = "3.jpg";
    private static final String PHOTO_4 = "4.jpg";
    private static final String PHOTO_5 = "5.jpg";
    private static final String PHOTO_6 = "6.jpg";
    private static final String PHOTO_7 = "7.jpg";

    // interactive session kinds
    private static final String IS_MORNING = "MORNING_POLL";
    private static final String IS_EVENING = "EVENING_POLL";
    private static final String IS_COACH_RATING = "COACH_RATING";
    private static final String IS_COACH_ATTR = "COACH_ATTR";

    public VadirssBot(Config cfg, BotFacade facade) {
        super(cfg.botToken());
        this.cfg = cfg;
        this.facade = facade;

        // Set /start /help /achive commands (best-effort)
        try {
            execute(new SetMyCommands(List.of(
                    new BotCommand("/start", "Открыть меню"),
                    new BotCommand("/help", "Помощь"),
                    new BotCommand("/achive", "Гайд по достижениям")
            ), null, null));
        } catch (Exception ignored) {}
    }

    @Override
    public String getBotUsername() {
        return cfg.botUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        try {
            if (update.hasCallbackQuery()) {
                onCallback(update.getCallbackQuery());
                return;
            }
            if (update.hasMessage()) {
                onMessage(update.getMessage());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onMessage(Message msg) {
        if (msg.getFrom() == null) return;
        long tgId = msg.getFrom().getId();
        long chatId = msg.getChatId();
        ZoneId zone = cfg.zoneId();

        User user = facade.users().getOrCreate(tgId, chatId, zone);

        String text = msg.getText();
        if (text == null) return;

        if (text.startsWith("/start")) {
            handleStart(user);
            return;
        }
        if (text.startsWith("/help")) {
            sendHelp(chatId);
            return;
        }
        if (text.startsWith("/achive")) {
            sendAchiveGuide(chatId);
            return;
        }

        // State-driven text input
        handleTextInput(user, text.trim());
    }

    private void onCallback(CallbackQuery cb) {
        if (cb.getFrom() == null || cb.getMessage() == null) return;
        long tgId = cb.getFrom().getId();
        long chatId = cb.getMessage().getChatId();
        int msgId = cb.getMessage().getMessageId();
        String data = cb.getData();
        ZoneId zone = cfg.zoneId();

        User user = facade.users().getOrCreate(tgId, chatId, zone);

        if (data == null) return;

        // Poll callbacks
        if (data.startsWith(CallbackData.POLL_MORNING_PREFIX)) {
            handleMorningPollCallback(user, cb);
            return;
        }
        if (data.startsWith(CallbackData.POLL_EVENING_PREFIX)) {
            handleEveningPollCallback(user, cb);
            return;
        }

        // Coach rating and attr value callbacks
        if (data.startsWith(CallbackData.COACH_RATE_PREFIX)) {
            handleCoachRatingCallback(user, cb);
            return;
        }
        if (data.startsWith(CallbackData.COACH_ATTR_VALUE_PREFIX)) {
            handleCoachAttrValueCallback(user, cb);
            return;
        }

        // Common navigation
        if (CallbackData.BACK_TO_MENU.equals(data)) {
            sendMenu(user);
            answer(cb.getId(), "✅", false);
            return;
        }

        // Registration
        if (CallbackData.CONSENT_YES.equals(data)) {
            facade.users().setConsent(user.tgId, true, zone);
            facade.users().setState(user.tgId, UserState.WAIT_FULLNAME, JsonUtils.obj(), zone);
            sendPhoto(user.chatId, PHOTO_1, "📝 <b>Пожалуйста, укажите ваше ФИО:</b>", null);
            answer(cb.getId(), "Спасибо! Продолжаем ✅", false);
            return;
        }
        if (data.startsWith(CallbackData.TEAM_SELECT_PREFIX)) {
            long teamId = Long.parseLong(data.substring(CallbackData.TEAM_SELECT_PREFIX.length()));
            facade.users().setTeam(user.tgId, teamId, zone);
            facade.users().setState(user.tgId, UserState.WAIT_POSITION, JsonUtils.obj(), zone);
            // Ask position
            sendHtml(user.chatId, "📍 <b>Выберите вашу позицию:</b>", positionsKeyboard());
            answer(cb.getId(), "Готово ✅", false);
            return;
        }
        if (data.startsWith(CallbackData.POS_SELECT_PREFIX)) {
            String pos = data.substring(CallbackData.POS_SELECT_PREFIX.length());
            facade.users().setPosition(user.tgId, pos, zone);
            facade.users().setState(user.tgId, UserState.IDLE, JsonUtils.obj(), zone);

            // After 1 second show menu (best-effort)
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}

            // Refresh user (team/position changed)
            user = facade.users().findById(user.tgId).orElse(user);
            sendMenu(user);
            answer(cb.getId(), "Регистрация завершена 🎉", false);
            return;
        }

        // Player menu
        if (CallbackData.MENU_PROFILE.equals(data)) {
            sendPlayerProfile(user);
            answer(cb.getId(), "👤 Профиль", false);
            return;
        }
        if (CallbackData.MENU_STATS.equals(data)) {
            sendPlayerStats(user);
            answer(cb.getId(), "📊 Статистика", false);
            return;
        }
        if (CallbackData.MENU_CHALLENGE.equals(data)) {
            sendPlayerChallenge(user);
            answer(cb.getId(), "🔥 Челленджи", false);
            return;
        }
        if (CallbackData.MENU_ACTIVITIES.equals(data)) {
            sendPlayerActivities(user);
            answer(cb.getId(), "🎯 Активности", false);
            return;
        }
        if (CallbackData.MENU_TEAM.equals(data)) {
            sendPlayerTeamMenu(user);
            answer(cb.getId(), "👥 Моя команда", false);
            return;
        }

        // Team submenu
        if (CallbackData.TEAM_FEED.equals(data)) {
            sendTeamFeed(user);
            answer(cb.getId(), "📰 Лента", false);
            return;
        }
        if (CallbackData.TEAM_PLAYERS.equals(data)) {
            sendTeamPlayers(user);
            answer(cb.getId(), "👥 Игроки", false);
            return;
        }

        // Coach main menu
        if (CallbackData.COACH_TEAMS.equals(data)) {
            sendCoachTeams(user);
            answer(cb.getId(), "🗂", false);
            return;
        }
        if (CallbackData.COACH_ANNOUNCE.equals(data)) {
            startCoachAnnouncement(user);
            answer(cb.getId(), "📢", false);
            return;
        }
        if (CallbackData.COACH_POOL.equals(data)) {
            startCoachAddPoolChallenge(user);
            answer(cb.getId(), "➕", false);
            return;
        }
        if (CallbackData.COACH_EXCEL.equals(data)) {
            startCoachExcel(user);
            answer(cb.getId(), "📈", false);
            return;
        }

        if (data.startsWith(CallbackData.COACH_TEAM_PREFIX)) {
            long teamId = Long.parseLong(data.substring(CallbackData.COACH_TEAM_PREFIX.length()));
            sendCoachTeamRoster(user, teamId);
            answer(cb.getId(), "Команда", false);
            return;
        }
        if (data.startsWith(CallbackData.COACH_TEAM_STATS_PREFIX)) {
            long teamId = Long.parseLong(data.substring(CallbackData.COACH_TEAM_STATS_PREFIX.length()));
            sendCoachTeamStatsPickPlayer(user, teamId);
            answer(cb.getId(), "Статистика", false);
            return;
        }
        if (data.startsWith(CallbackData.COACH_PLAYER_PREFIX)) {
            long playerId = Long.parseLong(data.substring(CallbackData.COACH_PLAYER_PREFIX.length()));
            sendCoachPlayerDetails(user, playerId);
            answer(cb.getId(), "Игрок", false);
            return;
        }
        if (data.startsWith(CallbackData.COACH_EDIT_ATTR_PREFIX)) {
            long playerId = Long.parseLong(data.substring(CallbackData.COACH_EDIT_ATTR_PREFIX.length()));
            startCoachEditAttributes(user, playerId, cb.getMessage());
            answer(cb.getId(), "Характеристики", false);
            return;
        }

        if (data.startsWith(CallbackData.COACH_MARK_START_PREFIX)) {
            long sessionId = Long.parseLong(data.substring(CallbackData.COACH_MARK_START_PREFIX.length()));
            sendCoachChallengeMarking(user, sessionId);
            answer(cb.getId(), "Ок, отметим 👇", false);
            return;
        }

        if (data.startsWith(CallbackData.COACH_CHALLENGE_MARK_PREFIX)) {
            // c:cm:<challengeId>:<1|0>
            String rest = data.substring(CallbackData.COACH_CHALLENGE_MARK_PREFIX.length());
            String[] parts = rest.split(":");
            if (parts.length >= 2) {
                long challengeId = Long.parseLong(parts[0]);
                boolean completed = "1".equals(parts[1]);
                facade.challenges().markChallenge(challengeId, completed, user.tgId);
                String status = completed ? "✅ Выполнено" : "❌ Не выполнено";
                // Edit message to confirm
                String newText = "Готово! " + status;
                editText(chatId, msgId, newText, Keyboards.backOnly());
            }
            answer(cb.getId(), "Сохранено", false);
            return;
        }

        
        if (data.startsWith("c:xls:")) {
            long teamId = Long.parseLong(data.substring("c:xls:".length()));
            sendTeamExcel(user, teamId);
            facade.users().setState(user.tgId, UserState.IDLE, JsonUtils.obj(), zone);
            answer(cb.getId(), "Отправляю файл…", false);
            return;
        }

// Admin main menu
        if (CallbackData.ADMIN_TEAMS.equals(data)) {
            sendAdminTeamsMenu(user);
            answer(cb.getId(), "🏟", false);
            return;
        }
        if (CallbackData.ADMIN_ADMINS.equals(data)) {
            sendAdminAdminsMenu(user);
            answer(cb.getId(), "🛡", false);
            return;
        }
        if (CallbackData.ADMIN_BACKUP.equals(data)) {
            sendBackup(user);
            answer(cb.getId(), "💾", false);
            return;
        }

        // Admin team submenu actions
        if (CallbackData.ADMIN_TEAM_CREATE.equals(data)) {
            JsonObject sd = JsonUtils.obj();
            facade.users().setState(user.tgId, UserState.ADMIN_CREATE_TEAM_NAME, sd, zone);
            sendHtml(user.chatId, "➕ <b>Создать команду</b>\n\nВведите название команды:", Keyboards.backOnly());
            answer(cb.getId(), "Ок", false);
            return;
        }
        if (CallbackData.ADMIN_TEAM_DELETE.equals(data)) {
            JsonObject sd = JsonUtils.obj();
            facade.users().setState(user.tgId, UserState.ADMIN_DELETE_TEAM_NUMBER, sd, zone);
            sendAdminTeamsListNumbered(user, "🗑 <b>Удалить команду</b>\n\nВведите номер команды для удаления:");
            answer(cb.getId(), "Ок", false);
            return;
        }
        if (CallbackData.ADMIN_TEAM_ASSIGN_COACH.equals(data)) {
            startAdminAssignCoachPickTeam(user);
            answer(cb.getId(), "Ок", false);
            return;
        }
        if (CallbackData.ADMIN_TEAM_SCHEDULE.equals(data)) {
            startAdminSchedulePickTeam(user);
            answer(cb.getId(), "Ок", false);
            return;
        }

        if (data.startsWith(CallbackData.ADMIN_PICK_TEAM_PREFIX)) {
            long teamId = Long.parseLong(data.substring(CallbackData.ADMIN_PICK_TEAM_PREFIX.length()));
            handleAdminPickedTeam(user, teamId);
            answer(cb.getId(), "Ок", false);
            return;
        }

        if (data.startsWith(CallbackData.ADMIN_SCHED_DAY_PREFIX)) {
            int day = Integer.parseInt(data.substring(CallbackData.ADMIN_SCHED_DAY_PREFIX.length()));
            handleAdminScheduleDayPicked(user, day);
            answer(cb.getId(), "Ок", false);
            return;
        }

        if (CallbackData.ADMIN_SCHED_DONE.equals(data)) {
            facade.users().setState(user.tgId, UserState.IDLE, JsonUtils.obj(), zone);
            sendHtml(user.chatId, "✅ Расписание сохранено.", Keyboards.backOnly());
            answer(cb.getId(), "Готово", false);
            return;
        }

        if (CallbackData.ADMIN_ADMINS_ADD.equals(data)) {
            startAdminAddAdmin(user);
            answer(cb.getId(), "Ок", false);
            return;
        }
        if (CallbackData.ADMIN_ADMINS_REMOVE.equals(data)) {
            startAdminRemoveAdmin(user);
            answer(cb.getId(), "Ок", false);
            return;
        }

        if (data.startsWith(CallbackData.ADMIN_PAGE_PREFIX)) {
            handleAdminPagination(user, data);
            answer(cb.getId(), "⏭", false);
            return;
        }

        // fallback
        answer(cb.getId(), "Не понял кнопку 🤔", false);
    }

    // --- Public send helpers for scheduler ---

    public Message sendHtml(long chatId, String text, InlineKeyboardMarkup kb) {
        SendMessage m = new SendMessage();
        m.setChatId(chatId);
        m.setText(limit(text));
        m.setParseMode(ParseMode.HTML);
        if (kb != null) m.setReplyMarkup(kb);
        try {
            return execute(m);
        } catch (TelegramApiException e) {
            return null;
        }
    }

    public Message sendPhoto(long chatId, String mediaKey, String caption, InlineKeyboardMarkup kb) {
        SendPhoto p = new SendPhoto();
        p.setChatId(chatId);
        p.setPhoto(facade.media().inputFile(mediaKey));
        if (caption != null) {
            p.setCaption(limit(caption));
            p.setParseMode(ParseMode.HTML);
        }
        if (kb != null) p.setReplyMarkup(kb);
        try {
            Message msg = execute(p);
            facade.media().cacheIfPossible(mediaKey, msg, cfg.zoneId());
            return msg;
        } catch (TelegramApiException e) {
            return null;
        }
    }

    public void editText(long chatId, int messageId, String text, InlineKeyboardMarkup kb) {
        EditMessageText em = new EditMessageText();
        em.setChatId(chatId);
        em.setMessageId(messageId);
        em.setText(limit(text));
        em.setParseMode(ParseMode.HTML);
        if (kb != null) em.setReplyMarkup(kb);
        try {
            execute(em);
        } catch (TelegramApiException ignored) {}
    }

    // Poll starters used by scheduler
    public void startMorningPoll(User player, TrainingSession session) {
        String text = "🌞 <b>Утренний опрос</b>\n\n" +
                "1/4: <b>Уровень энергии</b> (1–10)";
        Message m = sendHtml(player.chatId, text, Keyboards.numbers1to10(CallbackData.POLL_MORNING_PREFIX + "E:"));
        if (m == null) return;

        JsonObject data = JsonUtils.obj();
        data.addProperty("step", "ENERGY");
        data.addProperty("sessionId", session.id);
        facade.interactive().create(player.tgId, player.chatId, m.getMessageId(), IS_MORNING, data, null, cfg.zoneId());
    }

    public void startEveningPoll(User player, TrainingSession session) {
        String text = "🌙 <b>Вечерний опрос</b>\n\n" +
                "<b>Самооценка тренировки</b> (1–10)";
        Message m = sendHtml(player.chatId, text, Keyboards.numbers1to10(CallbackData.POLL_EVENING_PREFIX));
        if (m == null) return;

        JsonObject data = JsonUtils.obj();
        data.addProperty("step", "SELF");
        data.addProperty("sessionId", session.id);
        facade.interactive().create(player.tgId, player.chatId, m.getMessageId(), IS_EVENING, data, null, cfg.zoneId());
    }

    public void sendTodayChallenge(User player, TrainingSession session, boolean withPhoto) {
        var chOpt = facade.challenges().getChallengeForPlayer(session.id, player.tgId);
        String text;
        if (chOpt.isEmpty()) {
            text = "🔥 <b>Челлендж</b>\n\n⏳ Пока не назначен.";
        } else {
            PlayerChallenge ch = chOpt.get();
            text = "🔥 <b>Челлендж на тренировку</b>\n\n" +
                    "🎯 " + Html.esc(ch.text) + "\n" +
                    "Источник: " + ("COACH".equalsIgnoreCase(ch.source) ? "Тренер" : "ИИ") + "\n\n" +
                    "⚠️ Выполнение подтверждает тренер после тренировки.";
        }
        InlineKeyboardMarkup kb = Keyboards.backOnly();
        if (withPhoto) {
            sendPhoto(player.chatId, PHOTO_5, text, kb);
        } else {
            sendHtml(player.chatId, text, kb);
        }
    }

    public void startCoachRatingFlow(User coach, Team team, TrainingSession session, List<User> players) {
        if (coach.role != Role.COACH && coach.role != Role.ADMIN) return;

        // Create interactive session: store playerIds, index, step, sessionId
        JsonObject data = JsonUtils.obj();
        data.addProperty("teamId", team.id);
        data.addProperty("sessionId", session.id);
        data.addProperty("idx", 0);
        data.addProperty("step", "LIM");

        JsonArray ids = new JsonArray();
        for (User p : players) ids.add(p.tgId);
        data.add("players", ids);

        String text = coachRatingText(players.get(0), "LIM", null, null, null);
        Message m = sendHtml(coach.chatId, text, Keyboards.numbers0to4(CallbackData.COACH_RATE_PREFIX));
        if (m == null) return;

        facade.interactive().create(coach.tgId, coach.chatId, m.getMessageId(), IS_COACH_RATING, data, null, cfg.zoneId());
    }

    // --- Core flows ---

    private void handleStart(User user) {
        if (!user.consent || user.state == UserState.WAIT_CONSENT) {
            sendConsent(user.chatId);
            return;
        }
        if (!user.isRegistered()) {
            // continue registration
            if (user.state == UserState.WAIT_FULLNAME) {
                sendPhoto(user.chatId, PHOTO_1, "📝 <b>Пожалуйста, укажите ваше ФИО:</b>", null);
            } else if (user.state == UserState.WAIT_PHONE) {
                sendPhoto(user.chatId, PHOTO_1, "📞 <b>Укажите ваш номер телефона в формате:</b>\n+7 (XXX) XXX-XX-XX", null);
            } else if (user.state == UserState.WAIT_TEAM) {
                sendTeamPick(user.chatId);
            } else if (user.state == UserState.WAIT_POSITION) {
                sendHtml(user.chatId, "📍 <b>Выберите вашу позицию:</b>", positionsKeyboard());
            } else {
                sendConsent(user.chatId);
            }
            return;
        }
        // already registered
        sendMenu(user);
    }

    private void sendMenu(User user) {
        if (user.role == Role.ADMIN) {
            sendAdminMenu(user.chatId);
            return;
        }
        if (user.role == Role.COACH) {
            sendCoachMenu(user.chatId);
            return;
        }
        sendPlayerMenu(user.chatId);
    }

    private void sendConsent(long chatId) {
        String text =
                "Добро пожаловать в <b>vadirss.ru</b>! 🚀\n\n" +
                "Первым делом небольшой, но важный пункт:\n" +
                "Нажимая кнопку согласия, Вы подтверждаете своё " +
                "<a href=\"https://docs.google.com/document/d/1_tdSQB5NT3d6jtMCiZK0f9xYfeOtI2fOsFT7oJGwxRA/edit?tab=t.0\">согласие на обработку персональных данных</a> " +
                "в соответствии с нашей " +
                "<a href=\"https://docs.google.com/document/d/1HaA_KzljAyr3h43hCFIt1Q_yrN-sMFjxsoqQSpkwz0s/edit?tab=t.0\">Политикой конфиденциальности</a>.\n\n" +
                "📌 Помните: без согласия функционал будет доступен частично.\n" +
                "Жмите «Даю согласие» 👇 и продолжим регистрацию!";
        sendPhoto(chatId, PHOTO_0, text, Keyboards.ofRows(List.of(Keyboards.btn("✅ Даю согласие", CallbackData.CONSENT_YES))));
    }

    private void sendPlayerMenu(long chatId) {
        InlineKeyboardMarkup kb = Keyboards.ofRows(
                List.of(Keyboards.btn("👤 Профиль", CallbackData.MENU_PROFILE), Keyboards.btn("📊 Статистика", CallbackData.MENU_STATS)),
                List.of(Keyboards.btn("🔥 Челленджи", CallbackData.MENU_CHALLENGE), Keyboards.btn("🎯 Активности", CallbackData.MENU_ACTIVITIES)),
                List.of(Keyboards.btn("👥 Моя команда", CallbackData.MENU_TEAM))
        );
        sendHtml(chatId, "🏠 <b>Главное меню игрока</b>", kb);
    }

    private void sendCoachMenu(long chatId) {
        InlineKeyboardMarkup kb = Keyboards.ofRows(
                List.of(Keyboards.btn("🗂 Мои команды", CallbackData.COACH_TEAMS)),
                List.of(Keyboards.btn("📢 Сделать объявление", CallbackData.COACH_ANNOUNCE)),
                List.of(Keyboards.btn("➕ Создание челленджей", CallbackData.COACH_POOL)),
                List.of(Keyboards.btn("📈 Статистика (Excel)", CallbackData.COACH_EXCEL))
        );
        sendHtml(chatId, "🏠 <b>Меню тренера</b>", kb);
    }

    private void sendAdminMenu(long chatId) {
        InlineKeyboardMarkup kb = Keyboards.ofRows(
                List.of(Keyboards.btn("🏟 Команды", CallbackData.ADMIN_TEAMS)),
                List.of(Keyboards.btn("🛡 Администраторы", CallbackData.ADMIN_ADMINS)),
                List.of(Keyboards.btn("💾 Резервное копирование данных", CallbackData.ADMIN_BACKUP))
        );
        sendHtml(chatId, "🏠 <b>Меню администратора</b>", kb);
    }

    private void sendTeamPick(long chatId) {
        List<Team> teams = facade.teams().listTeams();
        if (teams.isEmpty()) {
            sendHtml(chatId, "⚠️ Пока нет созданных команд. Напишите администратору.", null);
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Team t : teams) {
            rows.add(List.of(Keyboards.btn("🏟 " + t.name, CallbackData.TEAM_SELECT_PREFIX + t.id)));
        }
        sendPhoto(chatId, PHOTO_2, "🏟 <b>Выберите команду:</b>", Keyboards.rows(rows));
    }

    private InlineKeyboardMarkup positionsKeyboard() {
        String[] positions = new String[] {
                "ВРТ", "ЦЗ", "ЛЗ", "ПЗ",
                "ЦП", "ЦАП", "ЛП", "ПП",
                "ФРВ", "НП"
        };
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (int i = 0; i < positions.length; i += 3) {
            List<InlineKeyboardButton> r = new ArrayList<>();
            for (int j = i; j < i + 3 && j < positions.length; j++) {
                String p = positions[j];
                r.add(Keyboards.btn(p, CallbackData.POS_SELECT_PREFIX + p));
            }
            rows.add(r);
        }
        return Keyboards.rows(rows);
    }

    private void handleTextInput(User user, String text) {
        ZoneId zone = cfg.zoneId();

        // Reload actual state (it can change after callback)
        user = facade.users().findById(user.tgId).orElse(user);

        switch (user.state) {
            case WAIT_FULLNAME -> {
                if (text.length() < 5) {
                    sendHtml(user.chatId, "⚠️ Похоже, ФИО слишком короткое. Попробуйте еще раз:", null);
                    return;
                }
                facade.users().setFullName(user.tgId, text, zone);
                facade.users().setState(user.tgId, UserState.WAIT_PHONE, JsonUtils.obj(), zone);
                sendPhoto(user.chatId, PHOTO_1, "📞 <b>Укажите ваш номер телефона в формате:</b>\n+7 (XXX) XXX-XX-XX", null);
            }
            case WAIT_PHONE -> {
                if (!PHONE.matcher(text).matches()) {
                    sendHtml(user.chatId, "⚠️ Неверный формат. Пример: <b>+7 (999) 123-45-67</b>\nПопробуйте еще раз:", null);
                    return;
                }
                facade.users().setPhone(user.tgId, text, zone);
                facade.users().setState(user.tgId, UserState.WAIT_TEAM, JsonUtils.obj(), zone);
                sendTeamPick(user.chatId);
            }
            case ADMIN_CREATE_TEAM_NAME -> {
                if (user.role != Role.ADMIN) return;
                if (text.length() < 2) {
                    sendHtml(user.chatId, "⚠️ Название слишком короткое. Введите еще раз:", null);
                    return;
                }
                try {
                    Team t = facade.teams().createTeam(text, zone);
                    facade.users().setState(user.tgId, UserState.IDLE, JsonUtils.obj(), zone);
                    sendHtml(user.chatId, "✅ Команда создана: <b>" + Html.esc(t.name) + "</b>", Keyboards.backOnly());
                } catch (Exception e) {
                    sendHtml(user.chatId, "⚠️ Не удалось создать команду (возможно, уже существует).", Keyboards.backOnly());
                }
            }
            case ADMIN_DELETE_TEAM_NUMBER -> {
                if (user.role != Role.ADMIN) return;
                int n;
                try { n = Integer.parseInt(text); } catch (Exception e) {
                    sendHtml(user.chatId, "⚠️ Введите номер (число).", null);
                    return;
                }
                List<Team> teams = facade.teams().listTeams();
                if (n < 1 || n > teams.size()) {
                    sendHtml(user.chatId, "⚠️ Нет команды с таким номером.", null);
                    return;
                }
                Team t = teams.get(n - 1);
                facade.teams().deleteTeam(t.id);
                facade.users().setState(user.tgId, UserState.IDLE, JsonUtils.obj(), zone);
                sendHtml(user.chatId, "🗑 Команда удалена: <b>" + Html.esc(t.name) + "</b>", Keyboards.backOnly());
            }
            case ADMIN_ASSIGN_COACH_PICK_USER_NUMBER -> {
                if (user.role != Role.ADMIN) return;
                handleAdminAssignCoachNumber(user, text);
            }
            case ADMIN_SCHEDULE_ENTER_TIME -> {
                if (user.role != Role.ADMIN) return;
                handleAdminScheduleTimeEntered(user, text);
            }
            case COACH_ANNOUNCE_TEXT -> {
                if (user.role != Role.COACH && user.role != Role.ADMIN) return;
                handleCoachAnnouncementText(user, text);
            }
            case COACH_ADD_POOL_CHALLENGE_TEXT -> {
                if (user.role != Role.COACH && user.role != Role.ADMIN) return;
                handleCoachPoolChallengeText(user, text);
            }
            case COACH_ADD_POOL_CHALLENGE_PICK_TEAM, COACH_ANNOUNCE_PICK_TEAM -> {
                // pick team only by buttons
                sendHtml(user.chatId, "Выберите команду кнопкой 👇", null);
            }
            case ADMIN_ADD_ADMIN_PICK_USER_NUMBER -> {
                if (user.role != Role.ADMIN) return;
                handleAdminAddAdminNumber(user, text);
            }
            case ADMIN_REMOVE_ADMIN_PICK_NUMBER -> {
                if (user.role != Role.ADMIN) return;
                handleAdminRemoveAdminNumber(user, text);
            }
            default -> {
                // default: show menu
                sendMenu(user);
            }
        }
    }

    // --- Player views ---

    private void sendPlayerProfile(User user) {
        ZoneId zone = cfg.zoneId();
        user = facade.users().findById(user.tgId).orElse(user);
        // refresh achievements (they may expire)
        facade.achievements().recompute(user, zone);

        String teamName = user.teamId == null ? "—" : facade.teams().findById(user.teamId).map(t -> t.name).orElse("—");
        var counts = facade.polls().counts(user.tgId);
        var ach = facade.achievements().listForPlayer(user.tgId);
        String quote = facade.ai().getOrCreateDailyQuote(LocalDate.now(zone), zone);
        var level = facade.points().levelOf(user);

        StringBuilder sb = new StringBuilder();
        sb.append("👤 <b>Профиль</b>\n\n");
        sb.append("🙋‍♂️ ФИО: <b>").append(Html.esc(nvl(user.fullName))).append("</b>\n");
        sb.append("🏟 Команда: <b>").append(Html.esc(teamName)).append("</b>\n");
        sb.append("📍 Позиция: <b>").append(Html.esc(nvl(user.position))).append("</b>\n\n");

        sb.append("⭐ Очки: <b>").append(user.points).append("</b>\n");
        sb.append("🎚 Уровень: <b>").append(level.emoji()).append(" ").append(level.name()).append("</b>\n\n");

        sb.append("📋 Выполнено:\n");
        sb.append("🌞 Утренних опросов: <b>").append(counts.morningPolls()).append("</b>\n");
        sb.append("🌙 Вечерних опросов: <b>").append(counts.eveningPolls()).append("</b>\n");
        sb.append("🔥 Челленджей: <b>").append(counts.completedChallenges()).append("</b>\n\n");

        sb.append("🏅 Достижения:\n");
        if (ach.isEmpty()) {
            sb.append("— Пока нет. Загляните в /achive 😉\n\n");
        } else {
            for (Achievement a : ach) sb.append("• ").append(a.label()).append("\n");
            sb.append("\n");
        }

        sb.append("✨ <b>Цитата дня</b>:\n").append(Html.esc(quote));

        // caption can be long; send photo and text separately to be safe
        sendPhoto(user.chatId, PHOTO_3, "👤 <b>Ваш профиль</b>", Keyboards.backOnly());
        for (String part : TextChunker.splitByLines(sb.toString(), cfg.maxMessageLen())) {
            sendHtml(user.chatId, part, Keyboards.backOnly());
        }
    }

    private void sendPlayerStats(User user) {
        ZoneId zone = cfg.zoneId();
        user = facade.users().findById(user.tgId).orElse(user);

        PlayerAttributes a = facade.achievements().getAttributes(user.tgId, zone);

        StringBuilder sb = new StringBuilder();
        sb.append("📊 <b>Статистика игрока</b>\n\n");

        sb.append("<b>А) Технические</b>\n");
        sb.append(line("Короткий пас", a.shortPass));
        sb.append(line("Первое касание", a.firstTouch));
        sb.append(line("Дальний пас", a.longPass));
        sb.append(line("Выбор позиции", a.positioning));
        sb.append(line("Удар головой", a.heading));
        sb.append(line("Навыки борьбы за мяч", a.ballBattle)).append("\n");

        sb.append("<b>Б) Физические</b>\n");
        sb.append(line("Сила", a.strength));
        sb.append(line("Гибкость", a.flexibility));
        sb.append(line("Скорость", a.speed));
        sb.append(line("Выносливость", a.endurance));
        sb.append(line("Ловкость", a.agility)).append("\n");

        sb.append("<b>В) Ментальные</b>\n");
        sb.append(line("Аналитическое мышление", a.analysis));
        sb.append(line("Общение", a.communication));
        sb.append(line("Работа в команде", a.teamwork));
        sb.append(line("Концентрация", a.concentration));
        sb.append(line("Волнение в игре", a.nervousness));
        sb.append(line("Лидерство", a.leadership));

        sendPhoto(user.chatId, PHOTO_4, sb.toString(), Keyboards.backOnly());
    }

    private void sendPlayerChallenge(User user) {
        ZoneId zone = cfg.zoneId();
        user = facade.users().findById(user.tgId).orElse(user);
        if (user.teamId == null) {
            sendHtml(user.chatId, "⚠️ У вас не выбрана команда.", Keyboards.backOnly());
            return;
        }
        TrainingSession session = facade.sessions().getOrCreateForTodayIfTraining(user.teamId);
        if (session == null) {
            sendPhoto(user.chatId, PHOTO_5, "🔥 <b>Челленджи</b>\n\nСегодня тренировки нет — челлендж не назначается.", Keyboards.backOnly());
            return;
        }
        // If challenges are not assigned yet — show info
        var chOpt = facade.challenges().getChallengeForPlayer(session.id, user.tgId);
        if (chOpt.isEmpty()) {
            sendPhoto(user.chatId, PHOTO_5, "🔥 <b>Челленджи</b>\n\n⏳ Челлендж будет предложен <b>за 2 часа</b> до тренировки.", Keyboards.backOnly());
            return;
        }
        sendTodayChallenge(user, session, true);
    }

    private void sendPlayerActivities(User user) {
        ZoneId zone = cfg.zoneId();
        user = facade.users().findById(user.tgId).orElse(user);

        String challengeLine = "—";
        if (user.teamId != null) {
            TrainingSession session = facade.sessions().getOrCreateForTodayIfTraining(user.teamId);
            if (session != null) {
                var chOpt = facade.challenges().getChallengeForPlayer(session.id, user.tgId);
                if (chOpt.isPresent()) {
                    var ch = chOpt.get();
                    String icon = switch (ch.status.toUpperCase()) {
                        case "COMPLETED" -> "✅";
                        case "FAILED" -> "❌";
                        case "EXPIRED" -> "⌛";
                        default -> "⏳";
                    };
                    challengeLine = icon + " " + ch.text;
                } else {
                    challengeLine = "⏳ Пока не назначен";
                }
            } else {
                challengeLine = "Сегодня тренировки нет";
            }
        }

        int todayPts = facade.points().getTodayPoints(user.tgId, zone);
        var level = facade.points().levelOf(user);

        StringBuilder sb = new StringBuilder();
        sb.append("🎯 <b>Активности</b>\n\n");
        sb.append("🔥 Активный челлендж: ").append(Html.esc(challengeLine)).append("\n\n");
        sb.append("⭐ Сегодняшние очки: <b>").append(todayPts).append("</b>\n");
        sb.append("🏅 Всего очков: <b>").append(user.points).append("</b>\n");
        sb.append("🎚 Уровень развития: <b>").append(level.emoji()).append(" ").append(level.name()).append("</b>\n\n");
        sb.append("📈 <b>Уровни развития</b>:\n");
        sb.append("🐣 Новичок (0-100)\n");
        sb.append("🌱 Развивающийся (101-300)\n");
        sb.append("💎 Профи (301-600)\n");
        sb.append("🦁 Лидер (601-900)\n");
        sb.append("🏅 Капитан (901+)\n\n");
        sb.append("✅ За каждый завершенный челлендж: <b>+25</b> очков.");

        sendPhoto(user.chatId, PHOTO_6, sb.toString(), Keyboards.backOnly());
    }

    private void sendPlayerTeamMenu(User user) {
        if (user.teamId == null) {
            sendHtml(user.chatId, "⚠️ У вас не выбрана команда.", Keyboards.backOnly());
            return;
        }
        InlineKeyboardMarkup kb = Keyboards.ofRows(
                List.of(Keyboards.btn("📰 Лента активности команды", CallbackData.TEAM_FEED)),
                List.of(Keyboards.btn("👥 Игроки", CallbackData.TEAM_PLAYERS)),
                List.of(Keyboards.btn("⬅️ Вернуться в меню", CallbackData.BACK_TO_MENU))
        );
        sendHtml(user.chatId, "👥 <b>Моя команда</b>\nВыберите действие:", kb);
    }

    private void sendTeamFeed(User user) {
        ZoneId zone = cfg.zoneId();
        user = facade.users().findById(user.tgId).orElse(user);
        if (user.teamId == null) {
            sendHtml(user.chatId, "⚠️ У вас не выбрана команда.", Keyboards.backOnly());
            return;
        }

        var events = facade.events().lastEvents(user.teamId, 5);
        if (events.isEmpty()) {
            sendHtml(user.chatId, "📰 <b>Лента активности</b>\n\nПока событий нет.", Keyboards.backOnly());
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📰 <b>Лента активности команды</b>\n\n");

        for (var ev : events) {
            String who = ev.userId == null ? "Кто-то" : facade.users().findById(ev.userId).map(u -> nvl(u.fullName)).orElse("Игрок");
            JsonObject payload = JsonUtils.parseObj(ev.payloadJson);
            String line = switch (ev.type) {
                case ACHIEVEMENT_AWARDED -> {
                    String code = payload.has("achievement") ? payload.get("achievement").getAsString() : "";
                    Achievement a = Achievement.byCode(code);
                    String lbl = a == null ? code : a.label();
                    yield "🏅 " + Html.esc(who) + " получил достижение: <b>" + Html.esc(lbl) + "</b>";
                }
                case CHALLENGE_COMPLETED -> {
                    String ch = payload.has("challenge") ? payload.get("challenge").getAsString() : "";
                    yield "✅ " + Html.esc(who) + " закрыл челлендж: " + Html.esc(ch);
                }
                case LEVEL_UP -> {
                    String to = payload.has("to") ? payload.get("to").getAsString() : "новый уровень";
                    yield "⬆️ " + Html.esc(who) + " достиг уровня: <b>" + Html.esc(to) + "</b>";
                }
            };
            sb.append("• ").append(line).append("\n");
        }

        sendHtml(user.chatId, sb.toString(), Keyboards.backOnly());
    }

    private void sendTeamPlayers(User user) {
        ZoneId zone = cfg.zoneId();
        user = facade.users().findById(user.tgId).orElse(user);
        if (user.teamId == null) {
            sendHtml(user.chatId, "⚠️ У вас не выбрана команда.", Keyboards.backOnly());
            return;
        }
        List<User> players = facade.users().listPlayersByTeam(user.teamId);

        StringBuilder sb = new StringBuilder();
        sb.append("👥 <b>Игроки команды</b>\n\n");

        int i = 1;
        for (User p : players) {
            var lvl = facade.points().levelOf(p);
            List<Achievement> ach = facade.achievements().listForPlayer(p.tgId);
            String achText;
            if (ach.isEmpty()) achText = "—";
            else if (ach.size() <= 3) achText = joinAch(ach);
            else achText = joinAch(ach.subList(0, 3)) + " … +" + (ach.size() - 3);

            sb.append(i++).append(") ")
                    .append(Html.esc(nvl(p.fullName)))
                    .append(" — <b>").append(Html.esc(nvl(p.position))).append("</b>")
                    .append(" — ").append(lvl.emoji()).append(" ").append(lvl.name())
                    .append("\n   🏅 ").append(Html.esc(achText))
                    .append("\n");
        }

        for (String part : TextChunker.splitByLines(sb.toString(), cfg.maxMessageLen())) {
            sendHtml(user.chatId, part, Keyboards.backOnly());
        }
    }

    // --- Poll callbacks ---

    private void handleMorningPollCallback(User user, CallbackQuery cb) {
        long chatId = cb.getMessage().getChatId();
        int msgId = cb.getMessage().getMessageId();
        ZoneId zone = cfg.zoneId();

        var sessOpt = facade.interactive().find(chatId, msgId, IS_MORNING);
        if (sessOpt.isEmpty()) {
            answer(cb.getId(), "Опрос уже завершён или устарел.", false);
            return;
        }
        InteractiveSession s = sessOpt.get();
        JsonObject data = s.data;

        String step = data.has("step") ? data.get("step").getAsString() : "ENERGY";
        String payload = cb.getData().substring(CallbackData.POLL_MORNING_PREFIX.length());

        if ("ENERGY".equals(step)) {
            int val = parseIntAfterPrefix(payload, "E:");
            data.addProperty("energy", val);
            data.addProperty("step", "SLEEP");
            facade.interactive().updateData(s.id, data, zone);

            editText(chatId, msgId, "🌞 <b>Утренний опрос</b>\n\n2/4: <b>Качество сна</b> (1–10)",
                    Keyboards.numbers1to10(CallbackData.POLL_MORNING_PREFIX + "S:"));
            return;
        }
        if ("SLEEP".equals(step)) {
            int val = parseIntAfterPrefix(payload, "S:");
            data.addProperty("sleep", val);
            data.addProperty("step", "READY");
            facade.interactive().updateData(s.id, data, zone);

            editText(chatId, msgId, "🌞 <b>Утренний опрос</b>\n\n3/4: <b>Готовность к тренировке</b> (1–10)",
                    Keyboards.numbers1to10(CallbackData.POLL_MORNING_PREFIX + "R:"));
            return;
        }
        if ("READY".equals(step)) {
            int val = parseIntAfterPrefix(payload, "R:");
            data.addProperty("readiness", val);
            data.addProperty("step", "MOOD");
            facade.interactive().updateData(s.id, data, zone);

            editText(chatId, msgId, "🌞 <b>Утренний опрос</b>\n\n4/4: <b>Настроение</b>",
                    Keyboards.moodButtons(CallbackData.POLL_MORNING_PREFIX + "M:"));
            return;
        }
        if ("MOOD".equals(step)) {
            String mood = payload.startsWith("M:") ? payload.substring(2) : payload;
            data.addProperty("mood", mood);

            Long sessionId = data.has("sessionId") ? data.get("sessionId").getAsLong() : null;
            int energy = data.has("energy") ? data.get("energy").getAsInt() : 0;
            int sleep = data.has("sleep") ? data.get("sleep").getAsInt() : 0;
            int readiness = data.has("readiness") ? data.get("readiness").getAsInt() : 0;

            facade.polls().saveMorningPoll(sessionId, user.tgId, energy, sleep, readiness, mood);
            facade.interactive().delete(s.id);

            // recompute achievements (discipline/week etc) - requires fresh user points and team
            facade.users().findById(user.tgId).ifPresent(u -> facade.achievements().recompute(u, zone));

            editText(chatId, msgId, "✅ Спасибо за ответы. Вам начислено <b>5</b> баллов.", null);
            return;
        }

        answer(cb.getId(), "Неожиданный шаг опроса", false);
    }

    private void handleEveningPollCallback(User user, CallbackQuery cb) {
        long chatId = cb.getMessage().getChatId();
        int msgId = cb.getMessage().getMessageId();
        ZoneId zone = cfg.zoneId();

        var sessOpt = facade.interactive().find(chatId, msgId, IS_EVENING);
        if (sessOpt.isEmpty()) {
            answer(cb.getId(), "Опрос уже завершён или устарел.", false);
            return;
        }
        InteractiveSession s = sessOpt.get();
        JsonObject data = s.data;

        int val;
        try { val = Integer.parseInt(cb.getData().substring(CallbackData.POLL_EVENING_PREFIX.length())); }
        catch (Exception e) { answer(cb.getId(), "Нужно число 1-10", false); return; }

        Long sessionId = data.has("sessionId") ? data.get("sessionId").getAsLong() : null;

        facade.polls().saveEveningPoll(sessionId, user.tgId, val);
        facade.interactive().delete(s.id);

        // Show challenge status
        String chLine = "—";
        if (sessionId != null) {
            var chOpt = facade.challenges().getChallengeForPlayer(sessionId, user.tgId);
            if (chOpt.isPresent()) {
                PlayerChallenge ch = chOpt.get();
                String icon = switch (ch.status.toUpperCase()) {
                    case "COMPLETED" -> "✅";
                    case "FAILED" -> "❌";
                    case "EXPIRED" -> "⌛";
                    default -> "⏳";
                };
                chLine = icon + " " + ch.text;
            }
        }

        String text = "✅ Спасибо!\n\n" +
                "⭐ Самооценка тренировки: <b>" + val + "</b>\n" +
                "🎯 Выполнение целей дня (челлендж): " + Html.esc(chLine);

        editText(chatId, msgId, text, null);
    }

    // --- Coach rating callbacks ---

    private void handleCoachRatingCallback(User coach, CallbackQuery cb) {
        if (coach.role != Role.COACH && coach.role != Role.ADMIN) {
            answer(cb.getId(), "Нет прав", true);
            return;
        }
        long chatId = cb.getMessage().getChatId();
        int msgId = cb.getMessage().getMessageId();
        ZoneId zone = cfg.zoneId();

        var sessOpt = facade.interactive().find(chatId, msgId, IS_COACH_RATING);
        if (sessOpt.isEmpty()) {
            answer(cb.getId(), "Сессия оценок устарела.", false);
            return;
        }
        InteractiveSession s = sessOpt.get();
        JsonObject data = s.data;

        String step = data.has("step") ? data.get("step").getAsString() : "LIM";
        int val;
        try { val = Integer.parseInt(cb.getData().substring(CallbackData.COACH_RATE_PREFIX.length())); }
        catch (Exception e) { answer(cb.getId(), "Нужно число", false); return; }

        int idx = data.has("idx") ? data.get("idx").getAsInt() : 0;
        JsonArray players = data.getAsJsonArray("players");
        if (players == null || players.isEmpty() || idx >= players.size()) {
            facade.interactive().delete(s.id);
            editText(chatId, msgId, "✅ Оценки завершены.", Keyboards.backOnly());
            return;
        }
        long playerId = players.get(idx).getAsLong();
        User player = facade.users().findById(playerId).orElse(null);
        if (player == null) {
            answer(cb.getId(), "Игрок не найден", false);
            return;
        }

        if ("LIM".equals(step)) {
            data.addProperty("lim", val);
            data.addProperty("step", "T2");
            facade.interactive().updateData(s.id, data, zone);
            editText(chatId, msgId, coachRatingText(player, "T2", val, null, null), Keyboards.numbers0to3(CallbackData.COACH_RATE_PREFIX));
            return;
        }
        if ("T2".equals(step)) {
            data.addProperty("t2", val);
            data.addProperty("step", "EIQ");
            facade.interactive().updateData(s.id, data, zone);
            Integer lim = data.has("lim") ? data.get("lim").getAsInt() : null;
            editText(chatId, msgId, coachRatingText(player, "EIQ", lim, val, null), Keyboards.numbers0to2(CallbackData.COACH_RATE_PREFIX));
            return;
        }
        if ("EIQ".equals(step)) {
            data.addProperty("eiq", val);
            Long sessionId = data.has("sessionId") ? data.get("sessionId").getAsLong() : null;

            Integer lim = data.has("lim") ? data.get("lim").getAsInt() : 0;
            Integer t2 = data.has("t2") ? data.get("t2").getAsInt() : 0;

            if (sessionId != null) {
                facade.coachRatings().upsertRating(sessionId, playerId, lim, t2, val);
            }

            // Next player
            idx++;
            data.addProperty("idx", idx);
            data.addProperty("step", "LIM");
            data.remove("lim");
            data.remove("t2");
            data.remove("eiq");

            if (idx >= players.size()) {
                facade.interactive().delete(s.id);
                editText(chatId, msgId, "✅ Спасибо! Оценки сохранены.", Keyboards.backOnly());
                return;
            }

            long nextPlayerId = players.get(idx).getAsLong();
            User nextPlayer = facade.users().findById(nextPlayerId).orElse(null);
            if (nextPlayer == null) {
                facade.interactive().delete(s.id);
                editText(chatId, msgId, "✅ Оценки завершены (некоторые игроки не найдены).", Keyboards.backOnly());
                return;
            }

            facade.interactive().updateData(s.id, data, zone);
            editText(chatId, msgId, coachRatingText(nextPlayer, "LIM", null, null, null), Keyboards.numbers0to4(CallbackData.COACH_RATE_PREFIX));
            return;
        }

        answer(cb.getId(), "Неожиданный шаг", false);
    }

    private static String coachRatingText(User player, String metric, Integer lim, Integer t2, Integer eiq) {
        StringBuilder sb = new StringBuilder();
        sb.append("📝 <b>Оценка после тренировки</b>\n\n");
        sb.append("Игрок: <b>").append(Html.esc(nvl(player.fullName))).append("</b>\n\n");
        if (lim != null) sb.append("LIM: ").append(lim).append("\n");
        if (t2 != null) sb.append("T2: ").append(t2).append("\n");
        if (eiq != null) sb.append("EIQ: ").append(eiq).append("\n");
        sb.append("\n");
        sb.append("<b>").append(metric).append("</b>: выберите значение");
        return sb.toString();
    }

    // --- Coach: attributes edit ---

    private void startCoachEditAttributes(User coach, long playerId, MaybeInaccessibleMessage maybeMsg) {
        if (coach.role != Role.COACH && coach.role != Role.ADMIN) return;
        ZoneId zone = cfg.zoneId();

        User player = facade.users().findById(playerId).orElse(null);
        if (player == null) {
            sendHtml(coach.chatId, "Игрок не найден.", Keyboards.backOnly());
            return;
        }

        List<String> keys = attributeKeys();
        JsonObject data = JsonUtils.obj();
        data.addProperty("playerId", playerId);
        data.addProperty("idx", 0);

        PlayerAttributes current = facade.achievements().getAttributes(playerId, zone);
        data.add("values", toJsonValues(current));

        String key = keys.get(0);
        double cur = getValueByKey(current, key);

        String text = "⭐ <b>Обновление характеристик</b>\n\n" +
                "Игрок: <b>" + Html.esc(nvl(player.fullName)) + "</b>\n" +
                "Показатель 1/" + keys.size() + ": <b>" + Html.esc(key) + "</b>\n" +
                "Текущее значение: <b>" + fmt1(cur) + "</b>\n\n" +
                "Выберите новое значение (0–10):";

        Message m = sendHtml(coach.chatId, text, Keyboards.numbers0to10(CallbackData.COACH_ATTR_VALUE_PREFIX));
        if (m == null) return;

        facade.interactive().create(coach.tgId, coach.chatId, m.getMessageId(), IS_COACH_ATTR, data, null, zone);
    }

    private void handleCoachAttrValueCallback(User coach, CallbackQuery cb) {
        if (coach.role != Role.COACH && coach.role != Role.ADMIN) {
            answer(cb.getId(), "Нет прав", true);
            return;
        }
        long chatId = cb.getMessage().getChatId();
        int msgId = cb.getMessage().getMessageId();
        ZoneId zone = cfg.zoneId();

        var sessOpt = facade.interactive().find(chatId, msgId, IS_COACH_ATTR);
        if (sessOpt.isEmpty()) {
            answer(cb.getId(), "Сессия устарела.", false);
            return;
        }
        InteractiveSession s = sessOpt.get();
        JsonObject data = s.data;

        int val;
        try { val = Integer.parseInt(cb.getData().substring(CallbackData.COACH_ATTR_VALUE_PREFIX.length())); }
        catch (Exception e) { answer(cb.getId(), "Нужно число", false); return; }

        long playerId = data.get("playerId").getAsLong();
        int idx = data.get("idx").getAsInt();

        List<String> keys = attributeKeys();
        if (idx < 0 || idx >= keys.size()) {
            facade.interactive().delete(s.id);
            editText(chatId, msgId, "✅ Готово.", Keyboards.backOnly());
            return;
        }

        String key = keys.get(idx);

        JsonObject values = data.getAsJsonObject("values");
        if (values == null) values = JsonUtils.obj();
        values.addProperty(key, val);
        data.add("values", values);

        idx++;
        if (idx >= keys.size()) {
            // Save
            PlayerAttributes attrs = fromJsonValues(values);
            facade.achievements().upsertAttributes(playerId, attrs, zone, true);

            // Recompute achievements for player
            facade.users().findById(playerId).ifPresent(u -> facade.achievements().recompute(u, zone));

            facade.interactive().delete(s.id);
            editText(chatId, msgId, "✅ Характеристики обновлены.", Keyboards.backOnly());
            return;
        }

        data.addProperty("idx", idx);
        facade.interactive().updateData(s.id, data, zone);

        User player = facade.users().findById(playerId).orElse(null);
        String nextKey = keys.get(idx);
        double cur = values.has(nextKey) ? values.get(nextKey).getAsDouble() : 0.0;

        String text = "⭐ <b>Обновление характеристик</b>\n\n" +
                "Игрок: <b>" + Html.esc(player != null ? nvl(player.fullName) : String.valueOf(playerId)) + "</b>\n" +
                "Показатель " + (idx + 1) + "/" + keys.size() + ": <b>" + Html.esc(nextKey) + "</b>\n" +
                "Текущее значение: <b>" + fmt1(cur) + "</b>\n\n" +
                "Выберите новое значение (0–10):";

        editText(chatId, msgId, text, Keyboards.numbers0to10(CallbackData.COACH_ATTR_VALUE_PREFIX));
    }

    private static List<String> attributeKeys() {
        return List.of(
                "Короткий пас",
                "Первое касание",
                "Дальний пас",
                "Выбор позиции",
                "Удар головой",
                "Навыки борьбы за мяч",
                "Сила",
                "Гибкость",
                "Скорость",
                "Выносливость",
                "Ловкость",
                "Аналитическое мышление",
                "Общение",
                "Работа в команде",
                "Концентрация",
                "Волнение в игре",
                "Лидерство"
        );
    }

    private static JsonObject toJsonValues(PlayerAttributes a) {
        JsonObject o = JsonUtils.obj();
        o.addProperty("Короткий пас", a.shortPass);
        o.addProperty("Первое касание", a.firstTouch);
        o.addProperty("Дальний пас", a.longPass);
        o.addProperty("Выбор позиции", a.positioning);
        o.addProperty("Удар головой", a.heading);
        o.addProperty("Навыки борьбы за мяч", a.ballBattle);

        o.addProperty("Сила", a.strength);
        o.addProperty("Гибкость", a.flexibility);
        o.addProperty("Скорость", a.speed);
        o.addProperty("Выносливость", a.endurance);
        o.addProperty("Ловкость", a.agility);

        o.addProperty("Аналитическое мышление", a.analysis);
        o.addProperty("Общение", a.communication);
        o.addProperty("Работа в команде", a.teamwork);
        o.addProperty("Концентрация", a.concentration);
        o.addProperty("Волнение в игре", a.nervousness);
        o.addProperty("Лидерство", a.leadership);
        return o;
    }

    private static PlayerAttributes fromJsonValues(JsonObject v) {
        PlayerAttributes a = new PlayerAttributes();
        a.shortPass = get(v, "Короткий пас");
        a.firstTouch = get(v, "Первое касание");
        a.longPass = get(v, "Дальний пас");
        a.positioning = get(v, "Выбор позиции");
        a.heading = get(v, "Удар головой");
        a.ballBattle = get(v, "Навыки борьбы за мяч");

        a.strength = get(v, "Сила");
        a.flexibility = get(v, "Гибкость");
        a.speed = get(v, "Скорость");
        a.endurance = get(v, "Выносливость");
        a.agility = get(v, "Ловкость");

        a.analysis = get(v, "Аналитическое мышление");
        a.communication = get(v, "Общение");
        a.teamwork = get(v, "Работа в команде");
        a.concentration = get(v, "Концентрация");
        a.nervousness = get(v, "Волнение в игре");
        a.leadership = get(v, "Лидерство");
        return a;
    }

    private static double get(JsonObject o, String key) {
        if (o == null || !o.has(key)) return 0.0;
        try { return o.get(key).getAsDouble(); } catch (Exception e) { return 0.0; }
    }

    private static double getValueByKey(PlayerAttributes a, String key) {
        return switch (key) {
            case "Короткий пас" -> a.shortPass;
            case "Первое касание" -> a.firstTouch;
            case "Дальний пас" -> a.longPass;
            case "Выбор позиции" -> a.positioning;
            case "Удар головой" -> a.heading;
            case "Навыки борьбы за мяч" -> a.ballBattle;
            case "Сила" -> a.strength;
            case "Гибкость" -> a.flexibility;
            case "Скорость" -> a.speed;
            case "Выносливость" -> a.endurance;
            case "Ловкость" -> a.agility;
            case "Аналитическое мышление" -> a.analysis;
            case "Общение" -> a.communication;
            case "Работа в команде" -> a.teamwork;
            case "Концентрация" -> a.concentration;
            case "Волнение в игре" -> a.nervousness;
            case "Лидерство" -> a.leadership;
            default -> 0.0;
        };
    }

    // --- Coach menu flows ---

    private void sendCoachTeams(User coach) {
        List<Team> teams = facade.teams().listTeamsForCoach(coach.tgId);
        if (teams.isEmpty()) {
            sendHtml(coach.chatId, "⚠️ У вас нет назначенных команд.", Keyboards.backOnly());
            return;
        }
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Team t : teams) {
            rows.add(List.of(Keyboards.btn("🏟 " + t.name, CallbackData.COACH_TEAM_PREFIX + t.id)));
        }
        rows.add(List.of(Keyboards.btn("⬅️ В меню", CallbackData.BACK_TO_MENU)));
        sendHtml(coach.chatId, "🗂 <b>Мои команды</b>\nВыберите команду:", Keyboards.rows(rows));
    }

    private void sendCoachTeamRoster(User coach, long teamId) {
        Team team = facade.teams().findById(teamId).orElse(null);
        if (team == null) {
            sendHtml(coach.chatId, "Команда не найдена.", Keyboards.backOnly());
            return;
        }
        List<User> players = facade.users().listPlayersByTeam(teamId);

        StringBuilder sb = new StringBuilder();
        sb.append("🏟 <b>").append(Html.esc(team.name)).append("</b>\n\n");
        sb.append("👥 <b>Состав</b>:\n");
        int i = 1;
        for (User p : players) {
            sb.append(i++).append(") ").append(Html.esc(nvl(p.fullName))).append(" — ").append(Html.esc(nvl(p.position))).append("\n");
        }

        InlineKeyboardMarkup kb = Keyboards.ofRows(
                List.of(Keyboards.btn("📋 Статистика", CallbackData.COACH_TEAM_STATS_PREFIX + teamId)),
                List.of(Keyboards.btn("⬅️ Вернуться в меню", CallbackData.BACK_TO_MENU))
        );
        sendHtml(coach.chatId, sb.toString(), kb);
    }

    private void sendCoachTeamStatsPickPlayer(User coach, long teamId) {
        Team team = facade.teams().findById(teamId).orElse(null);
        if (team == null) {
            sendHtml(coach.chatId, "Команда не найдена.", Keyboards.backOnly());
            return;
        }
        List<User> players = facade.users().listPlayersByTeam(teamId);
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (User p : players) {
            rows.add(List.of(Keyboards.btn("👤 " + nvl(p.fullName), CallbackData.COACH_PLAYER_PREFIX + p.tgId)));
        }
        rows.add(List.of(Keyboards.btn("⬅️ Назад к команде", CallbackData.COACH_TEAM_PREFIX + teamId)));
        sendHtml(coach.chatId, "📋 <b>Статистика</b>\nВыберите игрока:", Keyboards.rows(rows));
    }

    private void sendCoachPlayerDetails(User coach, long playerId) {
        User p = facade.users().findById(playerId).orElse(null);
        if (p == null) {
            sendHtml(coach.chatId, "Игрок не найден.", Keyboards.backOnly());
            return;
        }
        var morning = facade.polls().lastMorning(playerId, 3);
        var evening = facade.polls().lastEvening(playerId, 3);

        StringBuilder sb = new StringBuilder();
        sb.append("👤 <b>").append(Html.esc(nvl(p.fullName))).append("</b>\n");
        sb.append("📍 Позиция: ").append(Html.esc(nvl(p.position))).append("\n\n");

        sb.append("🌞 <b>Утренние опросы (последние)</b>:\n");
        if (morning.isEmpty()) sb.append("—\n");
        for (var m : morning) {
            sb.append("• ").append(m.date).append(" | E=").append(m.energy).append(" S=").append(m.sleep).append(" R=").append(m.readiness).append(" M=").append(m.mood).append("\n");
        }
        sb.append("\n");

        sb.append("🌙 <b>Вечерние опросы (последние)</b>:\n");
        if (evening.isEmpty()) sb.append("—\n");
        for (var e : evening) {
            sb.append("• ").append(e.date).append(" | Самооценка=").append(e.selfRating).append("\n");
        }
        sb.append("\n");

        PlayerAttributes a = facade.achievements().getAttributes(playerId, cfg.zoneId());
        sb.append("📊 <b>Текущие характеристики (кратко)</b>:\n");
        sb.append("Техника: ").append(fmt1((a.shortPass + a.longPass + a.firstTouch) / 3)).append("/10\n");
        sb.append("Физика: ").append(fmt1((a.strength + a.speed + a.endurance) / 3)).append("/10\n");
        sb.append("Ментал: ").append(fmt1((a.teamwork + a.concentration + a.communication) / 3)).append("/10\n");

        InlineKeyboardMarkup kb = Keyboards.ofRows(
                List.of(Keyboards.btn("✍️ Обновить характеристики", CallbackData.COACH_EDIT_ATTR_PREFIX + playerId)),
                List.of(Keyboards.btn("⬅️ В меню", CallbackData.BACK_TO_MENU))
        );
        sendHtml(coach.chatId, sb.toString(), kb);
    }

    private void startCoachAnnouncement(User coach) {
        if (coach.role != Role.COACH && coach.role != Role.ADMIN) {
            sendHtml(coach.chatId, "Нет прав.", Keyboards.backOnly());
            return;
        }
        List<Team> teams = facade.teams().listTeamsForCoach(coach.tgId);
        if (teams.isEmpty()) {
            sendHtml(coach.chatId, "⚠️ У вас нет назначенных команд.", Keyboards.backOnly());
            return;
        }
        JsonObject sd = JsonUtils.obj();
        if (teams.size() == 1) {
            sd.addProperty("teamId", teams.get(0).id);
            facade.users().setState(coach.tgId, UserState.COACH_ANNOUNCE_TEXT, sd, cfg.zoneId());
            sendHtml(coach.chatId, "📢 <b>Объявление</b>\n\nВведите текст объявления:", Keyboards.backOnly());
            return;
        }
        // pick team
        facade.users().setState(coach.tgId, UserState.COACH_ANNOUNCE_PICK_TEAM, sd, cfg.zoneId());
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Team t : teams) rows.add(List.of(Keyboards.btn("🏟 " + t.name, CallbackData.ADMIN_PICK_TEAM_PREFIX + t.id)));
        rows.add(List.of(Keyboards.btn("⬅️ В меню", CallbackData.BACK_TO_MENU)));
        sendHtml(coach.chatId, "📢 Выберите команду для объявления:", Keyboards.rows(rows));
    }

    private void handleCoachAnnouncementText(User coach, String text) {
        ZoneId zone = cfg.zoneId();
        coach = facade.users().findById(coach.tgId).orElse(coach);
        long teamId = coach.stateData.has("teamId") ? coach.stateData.get("teamId").getAsLong() : -1;
        if (teamId <= 0) {
            sendHtml(coach.chatId, "⚠️ Не выбрана команда. Начните заново.", Keyboards.backOnly());
            facade.users().setState(coach.tgId, UserState.IDLE, JsonUtils.obj(), zone);
            return;
        }
        List<User> players = facade.users().listPlayersByTeam(teamId);
        String msg = "📢 <b>Объявление от тренера</b>\n\n" + Html.esc(text);

        int sent = 0;
        for (User p : players) {
            if (!p.consent) continue;
            sendHtml(p.chatId, msg, null);
            sent++;
        }

        facade.users().setState(coach.tgId, UserState.IDLE, JsonUtils.obj(), zone);
        sendHtml(coach.chatId, "✅ Объявление отправлено игрокам: <b>" + sent + "</b>", Keyboards.backOnly());
    }

    private void startCoachAddPoolChallenge(User coach) {
        if (coach.role != Role.COACH && coach.role != Role.ADMIN) {
            sendHtml(coach.chatId, "Нет прав.", Keyboards.backOnly());
            return;
        }
        List<Team> teams = facade.teams().listTeamsForCoach(coach.tgId);
        if (teams.isEmpty()) {
            sendHtml(coach.chatId, "⚠️ У вас нет назначенных команд.", Keyboards.backOnly());
            return;
        }

        JsonObject sd = JsonUtils.obj();
        if (teams.size() == 1) {
            sd.addProperty("teamId", teams.get(0).id);
            facade.users().setState(coach.tgId, UserState.COACH_ADD_POOL_CHALLENGE_TEXT, sd, cfg.zoneId());
            sendHtml(coach.chatId, "➕ <b>Создание челленджа</b>\n\nВведите текст челленджа:", Keyboards.backOnly());
            return;
        }

        facade.users().setState(coach.tgId, UserState.COACH_ADD_POOL_CHALLENGE_PICK_TEAM, sd, cfg.zoneId());
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Team t : teams) rows.add(List.of(Keyboards.btn("🏟 " + t.name, CallbackData.ADMIN_PICK_TEAM_PREFIX + t.id)));
        rows.add(List.of(Keyboards.btn("⬅️ В меню", CallbackData.BACK_TO_MENU)));
        sendHtml(coach.chatId, "➕ Выберите команду для челленджа:", Keyboards.rows(rows));
    }

    private void handleCoachPoolChallengeText(User coach, String text) {
        ZoneId zone = cfg.zoneId();
        coach = facade.users().findById(coach.tgId).orElse(coach);

        long teamId = coach.stateData.has("teamId") ? coach.stateData.get("teamId").getAsLong() : -1;
        if (teamId <= 0) {
            sendHtml(coach.chatId, "⚠️ Не выбрана команда. Начните заново.", Keyboards.backOnly());
            facade.users().setState(coach.tgId, UserState.IDLE, JsonUtils.obj(), zone);
            return;
        }
        facade.challenges().addCoachPoolChallenge(teamId, text, zone);
        facade.users().setState(coach.tgId, UserState.IDLE, JsonUtils.obj(), zone);
        sendHtml(coach.chatId, "✅ Челлендж добавлен в пул команды.", Keyboards.backOnly());
    }

    private void startCoachExcel(User coach) {
        if (coach.role != Role.COACH && coach.role != Role.ADMIN) {
            sendHtml(coach.chatId, "Нет прав.", Keyboards.backOnly());
            return;
        }
        List<Team> teams = facade.teams().listTeamsForCoach(coach.tgId);
        if (teams.isEmpty()) {
            sendHtml(coach.chatId, "⚠️ У вас нет назначенных команд.", Keyboards.backOnly());
            return;
        }
        if (teams.size() == 1) {
            sendTeamExcel(coach, teams.get(0).id);
            return;
        }
        // Choose team
        JsonObject sd = JsonUtils.obj();
        sd.addProperty("excel", true);
        facade.users().setState(coach.tgId, UserState.IDLE, sd, cfg.zoneId());
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Team t : teams) rows.add(List.of(Keyboards.btn("🏟 " + t.name, "c:xls:" + t.id)));
        rows.add(List.of(Keyboards.btn("⬅️ В меню", CallbackData.BACK_TO_MENU)));
        sendHtml(coach.chatId, "📈 Выберите команду для Excel:", Keyboards.rows(rows));
    }

    private void sendTeamExcel(User coach, long teamId) {
        File file = facade.excel().buildTeamStatsExcel(teamId);
        SendDocument doc = new SendDocument();
        doc.setChatId(coach.chatId);
        doc.setDocument(new InputFile(file));
        doc.setCaption("📈 Статистика команды (LIM/T2/EIQ)");
        try {
            execute(doc);
        } catch (TelegramApiException e) {
            sendHtml(coach.chatId, "⚠️ Не удалось отправить Excel.", Keyboards.backOnly());
        }
    }

    // --- Coach: challenge marking ---

    private void sendCoachChallengeMarking(User coach, long sessionId) {
        if (coach.role != Role.COACH && coach.role != Role.ADMIN) {
            sendHtml(coach.chatId, "Нет прав.", Keyboards.backOnly());
            return;
        }
        var challenges = facade.challenges().listChallengesForSession(sessionId);
        if (challenges.isEmpty()) {
            sendHtml(coach.chatId, "Нет челленджей на эту тренировку.", Keyboards.backOnly());
            return;
        }
        for (PlayerChallenge ch : challenges) {
            User p = facade.users().findById(ch.playerId).orElse(null);
            String fio = p == null ? ("Игрок " + ch.playerId) : nvl(p.fullName);
            String text = "🧩 <b>Отметка челленджа</b>\n\n" +
                    "Игрок: <b>" + Html.esc(fio) + "</b>\n" +
                    "Челлендж: " + Html.esc(ch.text);
            InlineKeyboardMarkup kb = Keyboards.yesNo(
                    CallbackData.COACH_CHALLENGE_MARK_PREFIX + ch.id + ":1",
                    CallbackData.COACH_CHALLENGE_MARK_PREFIX + ch.id + ":0"
            );
            sendHtml(coach.chatId, text, kb);
        }
        sendHtml(coach.chatId, "Готово! После отметок можно вернуться в меню 👇", Keyboards.backOnly());
    }

    // --- Admin flows ---

    private void sendAdminTeamsMenu(User admin) {
        if (admin.role != Role.ADMIN) {
            sendHtml(admin.chatId, "Нет прав.", Keyboards.backOnly());
            return;
        }
        InlineKeyboardMarkup kb = Keyboards.ofRows(
                List.of(Keyboards.btn("➕ Создать команду", CallbackData.ADMIN_TEAM_CREATE)),
                List.of(Keyboards.btn("👔 Назначить тренера", CallbackData.ADMIN_TEAM_ASSIGN_COACH)),
                List.of(Keyboards.btn("🗓 Составить расписание", CallbackData.ADMIN_TEAM_SCHEDULE)),
                List.of(Keyboards.btn("🗑 Удалить команду", CallbackData.ADMIN_TEAM_DELETE)),
                List.of(Keyboards.btn("⬅️ В меню", CallbackData.BACK_TO_MENU))
        );
        sendHtml(admin.chatId, "🏟 <b>Команды</b>", kb);
    }

    private void sendAdminAdminsMenu(User admin) {
        if (admin.role != Role.ADMIN) {
            sendHtml(admin.chatId, "Нет прав.", Keyboards.backOnly());
            return;
        }
        InlineKeyboardMarkup kb = Keyboards.ofRows(
                List.of(Keyboards.btn("➕ Добавить администратора", CallbackData.ADMIN_ADMINS_ADD)),
                List.of(Keyboards.btn("🗑 Удалить администратора", CallbackData.ADMIN_ADMINS_REMOVE)),
                List.of(Keyboards.btn("⬅️ В меню", CallbackData.BACK_TO_MENU))
        );
        sendHtml(admin.chatId, "🛡 <b>Администраторы</b>", kb);
    }

    private void sendBackup(User admin) {
        if (admin.role != Role.ADMIN) {
            sendHtml(admin.chatId, "Нет прав.", Keyboards.backOnly());
            return;
        }
        try {
            File dbFile = cfg.dbPath().toFile();
            SendDocument doc = new SendDocument();
            doc.setChatId(admin.chatId);
            doc.setDocument(new InputFile(dbFile));
            doc.setCaption("💾 Резервная копия базы данных (sqlite)");
            execute(doc);
        } catch (Exception e) {
            sendHtml(admin.chatId, "⚠️ Не удалось отправить резервную копию.", Keyboards.backOnly());
        }
    }

    private void sendAdminTeamsListNumbered(User admin, String header) {
        List<Team> teams = facade.teams().listTeams();
        StringBuilder sb = new StringBuilder();
        sb.append(header).append("\n\n");
        for (int i = 0; i < teams.size(); i++) {
            sb.append(i + 1).append(") ").append(Html.esc(teams.get(i).name)).append("\n");
        }
        sendHtml(admin.chatId, sb.toString(), Keyboards.backOnly());
    }

    private void startAdminAssignCoachPickTeam(User admin) {
        if (admin.role != Role.ADMIN) return;
        List<Team> teams = facade.teams().listTeams();
        if (teams.isEmpty()) {
            sendHtml(admin.chatId, "⚠️ Нет команд.", Keyboards.backOnly());
            return;
        }
        JsonObject sd = JsonUtils.obj();
        sd.addProperty("ctx", "assignCoach");
        facade.users().setState(admin.tgId, UserState.ADMIN_ASSIGN_COACH_PICK_TEAM, sd, cfg.zoneId());

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Team t : teams) rows.add(List.of(Keyboards.btn("🏟 " + t.name, CallbackData.ADMIN_PICK_TEAM_PREFIX + t.id)));
        rows.add(List.of(Keyboards.btn("⬅️ В меню", CallbackData.BACK_TO_MENU)));
        sendHtml(admin.chatId, "👔 <b>Назначить тренера</b>\n\nВыберите команду:", Keyboards.rows(rows));
    }

    private void startAdminSchedulePickTeam(User admin) {
        if (admin.role != Role.ADMIN) return;
        List<Team> teams = facade.teams().listTeams();
        if (teams.isEmpty()) {
            sendHtml(admin.chatId, "⚠️ Нет команд.", Keyboards.backOnly());
            return;
        }
        JsonObject sd = JsonUtils.obj();
        sd.addProperty("ctx", "schedule");
        facade.users().setState(admin.tgId, UserState.ADMIN_SCHEDULE_PICK_TEAM, sd, cfg.zoneId());

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        for (Team t : teams) rows.add(List.of(Keyboards.btn("🏟 " + t.name, CallbackData.ADMIN_PICK_TEAM_PREFIX + t.id)));
        rows.add(List.of(Keyboards.btn("⬅️ В меню", CallbackData.BACK_TO_MENU)));
        sendHtml(admin.chatId, "🗓 <b>Расписание</b>\n\nВыберите команду:", Keyboards.rows(rows));
    }

    private void handleAdminPickedTeam(User admin, long teamId) {
        ZoneId zone = cfg.zoneId();
        admin = facade.users().findById(admin.tgId).orElse(admin);

        String ctx = admin.stateData.has("ctx") ? admin.stateData.get("ctx").getAsString() : "";

        if ("assignCoach".equals(ctx) || admin.state == UserState.ADMIN_ASSIGN_COACH_PICK_TEAM) {
            JsonObject sd = JsonUtils.obj();
            sd.addProperty("teamId", teamId);
            sd.addProperty("page", 0);
            facade.users().setState(admin.tgId, UserState.ADMIN_ASSIGN_COACH_PICK_USER_NUMBER, sd, zone);
            showUsersPageForAdmin(admin, "assignCoach", 0);
            return;
        }

        if ("schedule".equals(ctx) || admin.state == UserState.ADMIN_SCHEDULE_PICK_TEAM) {
            JsonObject sd = JsonUtils.obj();
            sd.addProperty("teamId", teamId);
            facade.users().setState(admin.tgId, UserState.ADMIN_SCHEDULE_PICK_DAY, sd, zone);
            showScheduleDaysMenu(admin, teamId);
            return;
        }

        // Coach pick team via admin prefix (used also in coach flows)
        if ((admin.role == Role.COACH || admin.role == Role.ADMIN) && (admin.state == UserState.COACH_ANNOUNCE_PICK_TEAM || admin.state == UserState.COACH_ADD_POOL_CHALLENGE_PICK_TEAM)) {
            JsonObject sd = JsonUtils.obj();
            sd.addProperty("teamId", teamId);
            if (admin.state == UserState.COACH_ANNOUNCE_PICK_TEAM) {
                facade.users().setState(admin.tgId, UserState.COACH_ANNOUNCE_TEXT, sd, zone);
                sendHtml(admin.chatId, "📢 <b>Объявление</b>\n\nВведите текст объявления:", Keyboards.backOnly());
            } else {
                facade.users().setState(admin.tgId, UserState.COACH_ADD_POOL_CHALLENGE_TEXT, sd, zone);
                sendHtml(admin.chatId, "➕ <b>Создание челленджа</b>\n\nВведите текст челленджа:", Keyboards.backOnly());
            }
            return;
        }

        // Excel team pick
        if ((admin.role == Role.COACH || admin.role == Role.ADMIN) && admin.stateData.has("excel") && admin.stateData.get("excel").getAsBoolean()) {
            sendTeamExcel(admin, teamId);
            admin.stateData.remove("excel");
            facade.users().setState(admin.tgId, UserState.IDLE, JsonUtils.obj(), zone);
        }
    }

    private void handleAdminPagination(User admin, String data) {
        // pg:<ctx>:<page>
        String rest = data.substring(CallbackData.ADMIN_PAGE_PREFIX.length());
        String[] parts = rest.split(":");
        if (parts.length < 2) return;
        String ctx = parts[0];
        int page = Integer.parseInt(parts[1]);

        if ("assignCoach".equals(ctx)) {
            showUsersPageForAdmin(admin, ctx, page);
        }

        if ("addAdmin".equals(ctx)) {
            showUsersPageForAdminAddAdmin(admin, page);
        }
    }

    private void showUsersPageForAdmin(User admin, String ctx, int page) {
        ZoneId zone = cfg.zoneId();
        int limit = 10;
        int offset = Math.max(0, page) * limit;

        List<User> users = facade.users().listUsers(offset, limit);

        // store mapping for current page
        JsonObject sd = admin.stateData.deepCopy();
        sd.addProperty("page", page);
        JsonArray ids = new JsonArray();
        for (User u : users) ids.add(u.tgId);
        sd.add("pageUserIds", ids);
        facade.users().setState(admin.tgId, UserState.ADMIN_ASSIGN_COACH_PICK_USER_NUMBER, sd, zone);

        StringBuilder sb = new StringBuilder();
        sb.append("👔 <b>Назначить тренера</b>\n\n");
        sb.append("Страница: ").append(page + 1).append("\n");
        sb.append("Введите номер пользователя (1-").append(users.size()).append("):\n\n");
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            sb.append(i + 1).append(") ").append(Html.esc(nvl(u.fullName))).append(" | ").append(Html.esc(nvl(u.phone))).append("\n");
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (page > 0) nav.add(Keyboards.btn("⬅️", CallbackData.ADMIN_PAGE_PREFIX + ctx + ":" + (page - 1)));
        nav.add(Keyboards.btn("➡️", CallbackData.ADMIN_PAGE_PREFIX + ctx + ":" + (page + 1)));
        rows.add(nav);
        rows.add(List.of(Keyboards.btn("⬅️ В меню", CallbackData.BACK_TO_MENU)));

        sendHtml(admin.chatId, sb.toString(), Keyboards.rows(rows));
    }

    private void handleAdminAssignCoachNumber(User admin, String text) {
        ZoneId zone = cfg.zoneId();
        admin = facade.users().findById(admin.tgId).orElse(admin);

        int n;
        try { n = Integer.parseInt(text.trim()); } catch (Exception e) {
            sendHtml(admin.chatId, "⚠️ Введите номер (число).", null);
            return;
        }
        JsonArray ids = admin.stateData.getAsJsonArray("pageUserIds");
        if (ids == null || ids.isEmpty()) {
            sendHtml(admin.chatId, "⚠️ Страница пользователей не найдена. Начните заново.", Keyboards.backOnly());
            facade.users().setState(admin.tgId, UserState.IDLE, JsonUtils.obj(), zone);
            return;
        }
        if (n < 1 || n > ids.size()) {
            sendHtml(admin.chatId, "⚠️ Неверный номер. Введите 1-" + ids.size(), null);
            return;
        }

        long userId = ids.get(n - 1).getAsLong();
        long teamId = admin.stateData.has("teamId") ? admin.stateData.get("teamId").getAsLong() : -1;
        if (teamId <= 0) {
            sendHtml(admin.chatId, "⚠️ Команда не выбрана.", Keyboards.backOnly());
            facade.users().setState(admin.tgId, UserState.IDLE, JsonUtils.obj(), zone);
            return;
        }

        facade.teams().assignCoach(teamId, userId);
        facade.users().setRole(userId, Role.COACH, zone);

        facade.users().setState(admin.tgId, UserState.IDLE, JsonUtils.obj(), zone);

        User u = facade.users().findById(userId).orElse(null);
        String name = u != null ? nvl(u.fullName) : String.valueOf(userId);
        sendHtml(admin.chatId, "✅ Пользователь назначен тренером команды.\nТренер: <b>" + Html.esc(name) + "</b>", Keyboards.backOnly());
    }

    private void showScheduleDaysMenu(User admin, long teamId) {
        ZoneId zone = cfg.zoneId();
        List<TeamSchedule> existing = facade.schedules().listForTeam(teamId);
        Map<Integer, TeamSchedule> map = new HashMap<>();
        for (TeamSchedule s : existing) map.put(s.dayOfWeek, s);

        StringBuilder sb = new StringBuilder();
        sb.append("🗓 <b>Расписание команды</b>\n\n");
        sb.append("Выберите день недели и задайте интервал (например 18:00-20:00).\n");
        sb.append("Уже задано:\n");
        if (existing.isEmpty()) sb.append("—\n");
        for (TeamSchedule s : existing) {
            sb.append(dayLabel(s.dayOfWeek)).append(": ").append(s.startTime).append("-").append(s.endTime).append("\n");
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(dayBtn(1, map), dayBtn(2, map), dayBtn(3, map)));
        rows.add(List.of(dayBtn(4, map), dayBtn(5, map), dayBtn(6, map)));
        rows.add(List.of(dayBtn(7, map)));
        rows.add(List.of(Keyboards.btn("✅ Готово", CallbackData.ADMIN_SCHED_DONE)));
        rows.add(List.of(Keyboards.btn("⬅️ В меню", CallbackData.BACK_TO_MENU)));

        sendHtml(admin.chatId, sb.toString(), Keyboards.rows(rows));
    }

    private InlineKeyboardButton dayBtn(int day, Map<Integer, TeamSchedule> existing) {
        String name = dayLabel(day);
        if (existing.containsKey(day)) name = "✅ " + name;
        return Keyboards.btn(name, CallbackData.ADMIN_SCHED_DAY_PREFIX + day);
    }

    private static String dayLabel(int day) {
        return switch (day) {
            case 1 -> "ПН";
            case 2 -> "ВТ";
            case 3 -> "СР";
            case 4 -> "ЧТ";
            case 5 -> "ПТ";
            case 6 -> "СБ";
            case 7 -> "ВС";
            default -> "??";
        };
    }

    private void handleAdminScheduleDayPicked(User admin, int day) {
        ZoneId zone = cfg.zoneId();
        admin = facade.users().findById(admin.tgId).orElse(admin);

        long teamId = admin.stateData.has("teamId") ? admin.stateData.get("teamId").getAsLong() : -1;
        if (teamId <= 0) {
            sendHtml(admin.chatId, "⚠️ Команда не выбрана.", Keyboards.backOnly());
            return;
        }
        JsonObject sd = admin.stateData.deepCopy();
        sd.addProperty("pendingDay", day);
        facade.users().setState(admin.tgId, UserState.ADMIN_SCHEDULE_ENTER_TIME, sd, zone);

        sendHtml(admin.chatId, "⏱ Введите интервал для <b>" + dayLabel(day) + "</b> в формате <b>HH:MM-HH:MM</b>:", Keyboards.backOnly());
    }

    private void handleAdminScheduleTimeEntered(User admin, String text) {
        ZoneId zone = cfg.zoneId();
        admin = facade.users().findById(admin.tgId).orElse(admin);

        long teamId = admin.stateData.has("teamId") ? admin.stateData.get("teamId").getAsLong() : -1;
        int day = admin.stateData.has("pendingDay") ? admin.stateData.get("pendingDay").getAsInt() : -1;
        if (teamId <= 0 || day <= 0) {
            sendHtml(admin.chatId, "⚠️ Не выбраны команда/день.", Keyboards.backOnly());
            facade.users().setState(admin.tgId, UserState.IDLE, JsonUtils.obj(), zone);
            return;
        }

        String[] parts = text.trim().split("-");
        if (parts.length != 2) {
            sendHtml(admin.chatId, "⚠️ Формат должен быть HH:MM-HH:MM. Пример: 18:00-20:00", null);
            return;
        }
        try {
            LocalTime start = LocalTime.parse(parts[0].trim());
            LocalTime end = LocalTime.parse(parts[1].trim());
            facade.schedules().upsert(teamId, day, start, end);

            JsonObject sd = admin.stateData.deepCopy();
            sd.remove("pendingDay");
            sd.addProperty("teamId", teamId);
            facade.users().setState(admin.tgId, UserState.ADMIN_SCHEDULE_PICK_DAY, sd, zone);
            showScheduleDaysMenu(admin, teamId);
        } catch (Exception e) {
            sendHtml(admin.chatId, "⚠️ Не удалось распознать время. Пример: 18:00-20:00", null);
        }
    }

    private void startAdminAddAdmin(User admin) {
        ZoneId zone = cfg.zoneId();
        JsonObject sd = JsonUtils.obj();
        sd.addProperty("page", 0);
        facade.users().setState(admin.tgId, UserState.ADMIN_ADD_ADMIN_PICK_USER_NUMBER, sd, zone);

        showUsersPageForAdminAddAdmin(admin, 0);
    }

    private void showUsersPageForAdminAddAdmin(User admin, int page) {
        int limit = 10;
        int offset = Math.max(0, page) * limit;
        List<User> users = facade.users().listUsers(offset, limit);

        JsonObject sd = admin.stateData.deepCopy();
        sd.addProperty("page", page);
        JsonArray ids = new JsonArray();
        for (User u : users) ids.add(u.tgId);
        sd.add("pageUserIds", ids);
        facade.users().setState(admin.tgId, UserState.ADMIN_ADD_ADMIN_PICK_USER_NUMBER, sd, cfg.zoneId());

        StringBuilder sb = new StringBuilder();
        sb.append("➕ <b>Добавить администратора</b>\n\n");
        sb.append("Страница: ").append(page + 1).append("\n");
        sb.append("Введите номер пользователя (1-").append(users.size()).append("):\n\n");
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            sb.append(i + 1).append(") ").append(Html.esc(nvl(u.fullName))).append(" | ").append(Html.esc(nvl(u.phone))).append(" | роль=").append(u.role).append("\n");
        }

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        List<InlineKeyboardButton> nav = new ArrayList<>();
        if (page > 0) nav.add(Keyboards.btn("⬅️", "pg:addAdmin:" + (page - 1)));
        nav.add(Keyboards.btn("➡️", "pg:addAdmin:" + (page + 1)));
        rows.add(nav);
        rows.add(List.of(Keyboards.btn("⬅️ В меню", CallbackData.BACK_TO_MENU)));

        sendHtml(admin.chatId, sb.toString(), Keyboards.rows(rows));
    }

    private void handleAdminAddAdminNumber(User admin, String text) {
        ZoneId zone = cfg.zoneId();
        admin = facade.users().findById(admin.tgId).orElse(admin);

        int n;
        try { n = Integer.parseInt(text.trim()); } catch (Exception e) {
            sendHtml(admin.chatId, "⚠️ Введите номер (число).", null);
            return;
        }
        JsonArray ids = admin.stateData.getAsJsonArray("pageUserIds");
        if (ids == null || ids.isEmpty() || n < 1 || n > ids.size()) {
            sendHtml(admin.chatId, "⚠️ Неверный номер.", null);
            return;
        }
        long userId = ids.get(n - 1).getAsLong();
        facade.users().setRole(userId, Role.ADMIN, zone);
        facade.users().setState(admin.tgId, UserState.IDLE, JsonUtils.obj(), zone);

        User u = facade.users().findById(userId).orElse(null);
        sendHtml(admin.chatId, "✅ Администратор добавлен: <b>" + Html.esc(u != null ? nvl(u.fullName) : String.valueOf(userId)) + "</b>", Keyboards.backOnly());
    }

    private void startAdminRemoveAdmin(User admin) {
        ZoneId zone = cfg.zoneId();
        JsonObject sd = JsonUtils.obj();
        sd.addProperty("page", 0);
        facade.users().setState(admin.tgId, UserState.ADMIN_REMOVE_ADMIN_PICK_NUMBER, sd, zone);

        showAdminsList(admin);
    }

    private void showAdminsList(User admin) {
        List<User> admins = facade.users().listAdmins(0, 100);
        StringBuilder sb = new StringBuilder();
        sb.append("🗑 <b>Удалить администратора</b>\n\n");
        if (admins.isEmpty()) {
            sb.append("— Нет администраторов (кроме вас?)\n");
            sendHtml(admin.chatId, sb.toString(), Keyboards.backOnly());
            return;
        }
        sb.append("Введите номер администратора для удаления:\n\n");
        for (int i = 0; i < admins.size(); i++) {
            User u = admins.get(i);
            sb.append(i + 1).append(") ").append(Html.esc(nvl(u.fullName))).append(" | ").append(Html.esc(nvl(u.phone))).append("\n");
        }
        // store ids
        JsonObject sd = admin.stateData.deepCopy();
        JsonArray ids = new JsonArray();
        for (User u : admins) ids.add(u.tgId);
        sd.add("adminIds", ids);
        facade.users().setState(admin.tgId, UserState.ADMIN_REMOVE_ADMIN_PICK_NUMBER, sd, cfg.zoneId());

        sendHtml(admin.chatId, sb.toString(), Keyboards.backOnly());
    }

    private void handleAdminRemoveAdminNumber(User admin, String text) {
        ZoneId zone = cfg.zoneId();
        admin = facade.users().findById(admin.tgId).orElse(admin);

        int n;
        try { n = Integer.parseInt(text.trim()); } catch (Exception e) {
            sendHtml(admin.chatId, "⚠️ Введите номер (число).", null);
            return;
        }
        JsonArray ids = admin.stateData.getAsJsonArray("adminIds");
        if (ids == null || ids.isEmpty() || n < 1 || n > ids.size()) {
            sendHtml(admin.chatId, "⚠️ Неверный номер.", null);
            return;
        }
        long userId = ids.get(n - 1).getAsLong();
        if (userId == admin.tgId) {
            sendHtml(admin.chatId, "⚠️ Нельзя удалить самого себя.", null);
            return;
        }
        facade.users().setRole(userId, Role.PLAYER, zone);
        facade.users().setState(admin.tgId, UserState.IDLE, JsonUtils.obj(), zone);

        User u = facade.users().findById(userId).orElse(null);
        sendHtml(admin.chatId, "🗑 Администратор удален: <b>" + Html.esc(u != null ? nvl(u.fullName) : String.valueOf(userId)) + "</b>", Keyboards.backOnly());
    }

    // --- Help & achievements guide ---

    private void sendHelp(long chatId) {
        String text = "🆘 <b>Как пользоваться ботом</b>\n\n" +
                "1) Нажмите /start и пройдите регистрацию (согласие, ФИО, телефон, команда, позиция).\n" +
                "2) Игрок: через меню смотрите профиль, статистику, челлендж и активность.\n" +
                "3) В тренировочный день бот сам отправит:\n" +
                "   • 09:00 — утренний опрос (за него +5 баллов)\n" +
                "   • За 2,5 часа — напоминание\n" +
                "   • За 2 часа — челлендж\n" +
                "   • 22:00 — вечерний опрос\n\n" +
                "4) Тренер подтверждает выполнение челленджей после тренировки.\n" +
                "5) Достижения пересчитываются автоматически и могут пропадать, если условия больше не выполняются.\n\n" +
                "Команды:\n" +
                "• /start — меню\n" +
                "• /help — помощь\n" +
                "• /achive — гайд по достижениям";
        sendPhoto(chatId, PHOTO_7, text, Keyboards.backOnly());
    }

    private void sendAchiveGuide(long chatId) {
        StringBuilder sb = new StringBuilder();
        sb.append("🏅 <b>Гайд по достижениям</b>\n\n");
        sb.append("Достижения выдаются автоматически по вашей статистике и активности.\n");
        sb.append("Важно: если вы перестали попадать под условия — достижение пропадёт.\n\n");

        for (Achievement a : Achievement.values()) {
            sb.append(a.emoji).append(" <b>").append(Html.esc(a.title)).append("</b>\n");
            sb.append("   ").append(Html.esc(a.howToGet)).append("\n\n");
        }

        sendHtml(chatId, sb.toString(), Keyboards.backOnly());
    }

    // --- small helpers ---

    private void answer(String cbId, String text, boolean alert) {
        AnswerCallbackQuery a = new AnswerCallbackQuery();
        a.setCallbackQueryId(cbId);
        a.setText(text);
        a.setShowAlert(alert);
        try {
            execute(a);
        } catch (TelegramApiException ignored) {}
    }

    private String limit(String text) {
        if (text == null) return "";
        if (text.length() <= cfg.maxMessageLen()) return text;
        return text.substring(0, cfg.maxMessageLen() - 3) + "...";
    }

    private static String nvl(String s) {
        return s == null ? "" : s;
    }

    private static String line(String name, double v) {
        return "• " + name + ": <b>" + fmt1(v) + "</b>/10\n";
    }

    private static String fmt1(double v) {
        return String.format(Locale.US, "%.1f", v);
    }

    private static int parseIntAfterPrefix(String payload, String pref) {
        if (!payload.startsWith(pref)) return 0;
        try {
            return Integer.parseInt(payload.substring(pref.length()));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String joinAch(List<Achievement> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(list.get(i).emoji);
        }
        return sb.toString();
    }
}
