package ru.vadirss.bot.telegram;

/**
 * Callback-data should be <= 64 bytes.
 * We keep it compact and parse by prefixes.
 */
public final class CallbackData {
    private CallbackData() {}

    // Common
    public static final String BACK_TO_MENU = "m:back";

    // Consent
    public static final String CONSENT_YES = "reg:consent";

    // Registration
    public static final String TEAM_SELECT_PREFIX = "reg:team:"; // + teamId
    public static final String POS_SELECT_PREFIX = "reg:pos:";   // + position

    // Player main menu
    public static final String MENU_PROFILE = "m:p";
    public static final String MENU_STATS = "m:s";
    public static final String MENU_CHALLENGE = "m:c";
    public static final String MENU_ACTIVITIES = "m:a";
    public static final String MENU_TEAM = "m:t";

    // Player team submenu
    public static final String TEAM_FEED = "t:feed";
    public static final String TEAM_PLAYERS = "t:pl";

    // Polls
    public static final String POLL_MORNING_PREFIX = "pm:"; // pm:<value> or pm:MOOd
    public static final String POLL_EVENING_PREFIX = "pe:"; // pe:<value>

    // Coach
    public static final String COACH_TEAMS = "c:teams";
    public static final String COACH_ANNOUNCE = "c:ann";
    public static final String COACH_POOL = "c:pool";
    public static final String COACH_EXCEL = "c:xls";

    public static final String COACH_TEAM_PREFIX = "c:team:"; // + teamId
    public static final String COACH_TEAM_STATS_PREFIX = "c:stats:"; // + teamId
    public static final String COACH_PLAYER_PREFIX = "c:pl:"; // + playerId
    public static final String COACH_EDIT_ATTR_PREFIX = "c:attr:"; // + playerId
    public static final String COACH_MARK_START_PREFIX = "c:mark:"; // + sessionId
    public static final String COACH_CHALLENGE_MARK_PREFIX = "c:cm:"; // c:cm:<challengeId>:<1|0>
    public static final String COACH_RATE_PREFIX = "c:rate:"; // c:rate:<value> handled by interactive session
    public static final String COACH_ATTR_VALUE_PREFIX = "c:av:"; // c:av:<0..10>

    // Admin
    public static final String ADMIN_TEAMS = "a:teams";
    public static final String ADMIN_ADMINS = "a:admins";
    public static final String ADMIN_BACKUP = "a:backup";

    public static final String ADMIN_TEAM_CREATE = "a:t:create";
    public static final String ADMIN_TEAM_ASSIGN_COACH = "a:t:coach";
    public static final String ADMIN_TEAM_SCHEDULE = "a:t:sched";
    public static final String ADMIN_TEAM_DELETE = "a:t:del";

    public static final String ADMIN_PICK_TEAM_PREFIX = "a:team:"; // + teamId
    public static final String ADMIN_SCHED_DAY_PREFIX = "a:day:"; // + dayOfWeek
    public static final String ADMIN_SCHED_DONE = "a:sched:done";

    public static final String ADMIN_ADMINS_ADD = "a:adm:add";
    public static final String ADMIN_ADMINS_REMOVE = "a:adm:rem";
    public static final String ADMIN_PAGE_PREFIX = "pg:"; // pg:<ctx>:<page>
}
