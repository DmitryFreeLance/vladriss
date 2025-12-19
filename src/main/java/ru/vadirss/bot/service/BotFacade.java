package ru.vadirss.bot.service;

import ru.vadirss.bot.config.Config;
import ru.vadirss.bot.db.Database;

public final class BotFacade {

    private final Config cfg;
    private final Database db;

    private final UserService userService;
    private final TeamService teamService;
    private final ScheduleService scheduleService;
    private final TrainingSessionService trainingSessionService;
    private final ChallengeService challengeService;
    private final PollService pollService;
    private final AchievementService achievementService;
    private final AiService aiService;
    private final MediaService mediaService;
    private final PointsService pointsService;
    private final ExcelService excelService;
    private final TeamEventService teamEventService;

    private final InteractiveSessionService interactiveSessions;
    private final CoachRatingService coachRatings;
    private final NotificationService notifications;

    public BotFacade(
            Config cfg,
            Database db,
            UserService userService,
            TeamService teamService,
            ScheduleService scheduleService,
            TrainingSessionService trainingSessionService,
            ChallengeService challengeService,
            PollService pollService,
            AchievementService achievementService,
            AiService aiService,
            MediaService mediaService,
            PointsService pointsService,
            ExcelService excelService,
            TeamEventService teamEventService,
            InteractiveSessionService interactiveSessions,
            CoachRatingService coachRatings,
            NotificationService notifications
    ) {
        this.cfg = cfg;
        this.db = db;
        this.userService = userService;
        this.teamService = teamService;
        this.scheduleService = scheduleService;
        this.trainingSessionService = trainingSessionService;
        this.challengeService = challengeService;
        this.pollService = pollService;
        this.achievementService = achievementService;
        this.aiService = aiService;
        this.mediaService = mediaService;
        this.pointsService = pointsService;
        this.excelService = excelService;
        this.teamEventService = teamEventService;
        this.interactiveSessions = interactiveSessions;
        this.coachRatings = coachRatings;
        this.notifications = notifications;
    }

    public Config cfg() { return cfg; }
    public Database db() { return db; }

    public UserService users() { return userService; }
    public TeamService teams() { return teamService; }
    public ScheduleService schedules() { return scheduleService; }
    public TrainingSessionService sessions() { return trainingSessionService; }
    public ChallengeService challenges() { return challengeService; }
    public PollService polls() { return pollService; }
    public AchievementService achievements() { return achievementService; }
    public AiService ai() { return aiService; }
    public MediaService media() { return mediaService; }
    public PointsService points() { return pointsService; }
    public ExcelService excel() { return excelService; }
    public TeamEventService events() { return teamEventService; }

    public InteractiveSessionService interactive() { return interactiveSessions; }
    public CoachRatingService coachRatings() { return coachRatings; }
    public NotificationService notifications() { return notifications; }
}
