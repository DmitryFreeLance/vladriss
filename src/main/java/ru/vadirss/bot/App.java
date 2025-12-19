package ru.vadirss.bot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import ru.vadirss.bot.config.Config;
import ru.vadirss.bot.db.Database;
import ru.vadirss.bot.db.Schema;
import ru.vadirss.bot.scheduler.SchedulerService;
import ru.vadirss.bot.service.*;
import ru.vadirss.bot.telegram.VadirssBot;

public final class App {

    public static void main(String[] args) throws Exception {
        Config cfg = Config.load();

        System.out.println("DB_PATH env=" + System.getenv("DB_PATH"));
        System.out.println("Config dbPath=" + cfg.dbPath().toAbsolutePath());

        Database db = new Database(cfg);
        Schema.migrate(db);

        try (var c = db.getConnection();
             var st = c.createStatement();
             var rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")) {
            System.out.print("Tables: ");
            while (rs.next()) System.out.print(rs.getString(1) + " ");
            System.out.println();
        }

        // --- Services ---
        UserService userService = new UserService(db);
        TeamService teamService = new TeamService(db);
        ScheduleService scheduleService = new ScheduleService(db);
        TrainingSessionService trainingSessionService = new TrainingSessionService(db, cfg, scheduleService);

        PointsService pointsService = new PointsService(db);
        TeamEventService teamEventService = new TeamEventService(db);

        AiService aiService = new AiService(cfg, db);

        AchievementService achievementService = new AchievementService(db, teamEventService);
        ChallengeService challengeService = new ChallengeService(db, cfg, aiService, pointsService, teamEventService, achievementService);
        PollService pollService = new PollService(db, cfg, teamEventService);

        MediaService mediaService = new MediaService(db, cfg);
        ExcelService excelService = new ExcelService(db, cfg);

        InteractiveSessionService interactiveSessions = new InteractiveSessionService(db);
        CoachRatingService coachRatingService = new CoachRatingService(db, cfg);
        NotificationService notificationService = new NotificationService(db);

        BotFacade facade = new BotFacade(
                cfg,
                db,
                userService,
                teamService,
                scheduleService,
                trainingSessionService,
                challengeService,
                pollService,
                achievementService,
                aiService,
                mediaService,
                pointsService,
                excelService,
                teamEventService,
                interactiveSessions,
                coachRatingService,
                notificationService
        );

        VadirssBot bot = new VadirssBot(cfg, facade);

        TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
        api.registerBot(bot);

        SchedulerService scheduler = new SchedulerService(cfg, facade, bot);
        scheduler.start();

        System.out.println("Vadirss bot started. Timezone=" + cfg.zoneId());
    }
}