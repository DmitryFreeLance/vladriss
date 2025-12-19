package ru.vadirss.bot.scheduler;

import com.google.gson.JsonObject;
import org.telegram.telegrambots.meta.api.objects.Message;
import ru.vadirss.bot.config.Config;
import ru.vadirss.bot.model.Team;
import ru.vadirss.bot.model.TeamSchedule;
import ru.vadirss.bot.model.TrainingSession;
import ru.vadirss.bot.model.User;
import ru.vadirss.bot.service.BotFacade;
import ru.vadirss.bot.telegram.CallbackData;
import ru.vadirss.bot.telegram.Keyboards;
import ru.vadirss.bot.telegram.VadirssBot;
import ru.vadirss.bot.util.TimeUtil;

import java.time.*;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class SchedulerService {

    private final Config cfg;
    private final BotFacade facade;
    private final VadirssBot bot;

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "vadirss-scheduler");
        t.setDaemon(true);
        return t;
    });

    public SchedulerService(Config cfg, BotFacade facade, VadirssBot bot) {
        this.cfg = cfg;
        this.facade = facade;
        this.bot = bot;
    }

    public void start() {
        exec.scheduleAtFixedRate(this::tickSafe, 5, cfg.schedulerIntervalSeconds(), TimeUnit.SECONDS);
    }

    private void tickSafe() {
        try {
            tick();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void tick() {
        ZoneId zone = cfg.zoneId();
        LocalDateTime now = LocalDateTime.now(zone).withSecond(0).withNano(0);
        LocalDate today = now.toLocalDate();
        LocalTime nowTime = now.toLocalTime();

        List<Team> teams = facade.teams().listTeams();
        for (Team team : teams) {
            Optional<TeamSchedule> schedOpt = facade.schedules().findForTeamAndDay(team.id, now.getDayOfWeek());
            if (schedOpt.isPresent()) {
                TeamSchedule sched = schedOpt.get();
                TrainingSession session = facade.sessions().getOrCreate(team.id, today, sched.startTime, sched.endTime);
                handleTrainingDay(team, session, now);
            } else {
                handleNoTrainingDay(team, today, nowTime);
            }
        }
    }

    private void handleNoTrainingDay(Team team, LocalDate today, LocalTime nowTime) {
        // 09:00 quote (only if not training for this team today)
        if (nowTime.isBefore(LocalTime.of(9, 0))) return;
        String dateIso = TimeUtil.fmt(today);
        if (facade.notifications().isQuoteSent(team.id, dateIso)) return;

        String quote = facade.ai().getOrCreateDailyQuote(today, cfg.zoneId());
        String text = "✨ <b>Цитата дня</b>\n\n" + quote;

        List<User> players = facade.users().listPlayersByTeam(team.id);
        for (User p : players) {
            if (!p.consent) continue;
            bot.sendHtml(p.chatId, text, null);
        }
        facade.notifications().markQuoteSent(team.id, dateIso);
    }

    private void handleTrainingDay(Team team, TrainingSession session, LocalDateTime now) {
        ZoneId zone = cfg.zoneId();
        LocalTime nowTime = now.toLocalTime();

        // Morning poll 09:00
        if (!session.morningPollSent && !nowTime.isBefore(LocalTime.of(9, 0))) {
            sendMorningPoll(team, session);
            facade.sessions().setFlag(session.id, "morning_poll_sent", true);
            session.morningPollSent = true;
        }

        // Reminder: start - 2h30m
        LocalDateTime reminderAt = session.startDateTime.minusMinutes(150);
        if (!session.reminderSent && !now.isBefore(reminderAt) && now.isBefore(session.startDateTime)) {
            sendReminder(team, session);
            facade.sessions().setFlag(session.id, "reminder_sent", true);
            session.reminderSent = true;
        }

        // Challenge: start - 2h
        LocalDateTime challengeAt = session.startDateTime.minusMinutes(120);
        if (!session.challengesSent && !now.isBefore(challengeAt) && now.isBefore(session.startDateTime)) {
            sendChallenges(team, session);
            facade.sessions().setFlag(session.id, "challenges_sent", true);
            session.challengesSent = true;
        }

        // Coach prompt: start - 10m
        LocalDateTime coachPromptAt = session.startDateTime.minusMinutes(10);
        if (!session.coachPromptSent && !now.isBefore(coachPromptAt) && now.isBefore(session.startDateTime)) {
            sendCoachPrompt(team, session);
            facade.sessions().setFlag(session.id, "coach_prompt_sent", true);
            session.coachPromptSent = true;
        }

        // Coach rating after training end + 5m
        LocalDateTime coachRatingAt = session.endDateTime.plusMinutes(5);
        if (!session.coachRatingSent && !now.isBefore(coachRatingAt)) {
            startCoachRating(team, session);
            facade.sessions().setFlag(session.id, "coach_rating_sent", true);
            session.coachRatingSent = true;
        }

        // Expire pending challenges after training end + 30m
        LocalDateTime expireAt = session.endDateTime.plusMinutes(30);
        if (!session.challengesExpired && !now.isBefore(expireAt)) {
            facade.challenges().expirePendingChallenges(session.id);
            facade.sessions().setFlag(session.id, "challenges_expired", true);
            session.challengesExpired = true;
        }

        // Evening poll at 22:00
        if (!session.eveningPollSent && !nowTime.isBefore(LocalTime.of(22, 0))) {
            sendEveningPoll(team, session);
            facade.sessions().setFlag(session.id, "evening_poll_sent", true);
            session.eveningPollSent = true;
        }
    }

    private void sendMorningPoll(Team team, TrainingSession session) {
        List<User> players = facade.users().listPlayersByTeam(team.id);
        for (User p : players) {
            if (!p.consent) continue;
            bot.startMorningPoll(p, session);
        }
    }

    private void sendReminder(Team team, TrainingSession session) {
        String text = "⏰ <b>Напоминание</b>\n" +
                "Сегодня тренировка в <b>" + session.startDateTime.toLocalTime() + "</b>.\n\n" +
                "💧 Не забудь воду, бутсы и настрой на победу!";
        List<User> players = facade.users().listPlayersByTeam(team.id);
        for (User p : players) {
            if (!p.consent) continue;
            bot.sendHtml(p.chatId, text, null);
        }
    }

    private void sendChallenges(Team team, TrainingSession session) {
        List<User> players = facade.users().listPlayersByTeam(team.id);
        // Assign in DB
        facade.challenges().assignChallengesForSession(team.id, session.id, players);

        for (User p : players) {
            if (!p.consent) continue;
            bot.sendTodayChallenge(p, session, true);
        }
    }

    private void sendCoachPrompt(Team team, TrainingSession session) {
        List<User> coaches = facade.users().listCoachesByTeam(team.id);
        if (coaches.isEmpty()) return;

        // Build summary
        StringBuilder sb = new StringBuilder();
        sb.append("🧩 <b>Челленджи игроков на тренировку</b>\n");
        sb.append("Команда: <b>").append(team.name).append("</b>\n");
        sb.append("Время: ").append(session.startDateTime.toLocalTime()).append("\n\n");

        var challenges = facade.challenges().listChallengesForSession(session.id);
        int i = 1;
        for (var ch : challenges) {
            var playerOpt = facade.users().findById(ch.playerId);
            String fio = playerOpt.map(u -> u.fullName).orElse("Игрок " + ch.playerId);
            sb.append(i++).append(") ").append(fio).append(" — ").append(ch.text).append("\n");
        }

        String msg = sb.toString();
        for (User coach : coaches) {
            bot.sendHtml(coach.chatId, msg, Keyboards.ofRows(
                    java.util.List.of(Keyboards.btn("✅ Отметить выполнение", CallbackData.COACH_MARK_START_PREFIX + session.id)),
                    java.util.List.of(Keyboards.btn("⬅️ В меню", CallbackData.BACK_TO_MENU))
            ));
        }
    }

    private void sendEveningPoll(Team team, TrainingSession session) {
        List<User> players = facade.users().listPlayersByTeam(team.id);
        for (User p : players) {
            if (!p.consent) continue;
            bot.startEveningPoll(p, session);
        }
    }

    private void startCoachRating(Team team, TrainingSession session) {
        List<User> coaches = facade.users().listCoachesByTeam(team.id);
        if (coaches.isEmpty()) return;
        List<User> players = facade.users().listPlayersByTeam(team.id);
        if (players.isEmpty()) return;

        for (User coach : coaches) {
            bot.startCoachRatingFlow(coach, team, session, players);
        }
    }
}
