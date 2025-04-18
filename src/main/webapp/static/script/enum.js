// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

const REGION = Object.freeze
({
    US: {code:1, name: "us", fullName: "US", order: 1},
    EU: {code:2, name: "eu", fullName: "EU", order: 2},
    KR: {code:3, name: "kr", fullName: "KR", order: 3},
    CN: {code:5, name: "cn", fullName: "CN", order: 4}
});

const RACE = Object.freeze
({
    TERRAN: {code: 1, name: "terran", fullName: "TERRAN", order: 1},
    PROTOSS: {code: 2, name: "protoss", fullName: "PROTOSS", order: 2},
    ZERG: {code: 3, name: "zerg", fullName: "ZERG", order: 3},
    RANDOM: {code: 4, name: "random", fullName: "RANDOM", order: 4}
});

const LEAGUE = Object.freeze
({
    BRONZE: {code:0, name: "bronze", shortName: "bro", fullName: "BRONZE", order: 1},
    SILVER: {code:1, name: "silver", shortName: "sil", fullName: "SILVER", order: 2},
    GOLD: {code:2, name: "gold", shortName: "gol", fullName: "GOLD", order: 3},
    PLATINUM: {code:3, name: "platinum", shortName: "pla", fullName: "PLATINUM", order: 4},
    DIAMOND: {code:4, name: "diamond", shortName: "dia", fullName: "DIAMOND", order: 5},
    MASTER: {code:5, name: "master", shortName: "mas", fullName: "MASTER", order: 6},
    GRANDMASTER: {code:6, name: "grandmaster", shortName: "gra", fullName: "GRANDMASTER", order: 7}
});

const LEAGUE_TIER = Object.freeze
({
    FIRST: {code: 0, name: "1", fullName: "FIRST", order: 1},
    SECOND: {code: 1, name: "2", fullName: "SECOND", order: 2},
    THIRD: {code: 2, name: "3", fullName: "THIRD", order: 3}
});

const TEAM_FORMAT = Object.freeze
({
    _1V1: {code:201, name: "1V1", fullName: "LOTV_1V1", formatName: "_1V1", memberCount: 1, order: 1},
    _2V2: {code:202, name: "2V2", fullName: "LOTV_2V2", formatName: "_2V2", memberCount: 2, order: 2},
    _3V3: {code:203, name: "3V3", fullName: "LOTV_3V3", formatName: "_3V3", memberCount: 3, order: 3},
    _4V4: {code:204, name: "4V4", fullName: "LOTV_4V4", formatName: "_4V4", memberCount: 4, order: 4},
    ARCHON: {code:206, name: "Archon", fullName: "LOTV_ARCHON", formatName: "ARCHON", memberCount: 2, order: 5}
});

const TEAM_FORMAT_TYPE = Object.freeze
({
    _1V1: {name: "1V1", fullName: "_1V1", teamFormats: [TEAM_FORMAT._1V1], order: 1},
    TEAM: {name: "Team", fullName: "TEAM", teamFormats: Object.values(TEAM_FORMAT).filter(f=>f.memberCount > 1), order: 2}
});

const TEAM_TYPE = Object.freeze
({
    ARRANGED: {code:0, name: "Arranged", fullName: "ARRANGED", secondaryName: "Team", order: 1},
    RANDOM: {code:1, name: "Random", fullName: "RANDOM", secondaryName: "Solo", order: 2}
});

const PERIOD = Object.freeze
({
   DAY: {name: "day", timeUnit: "hour", order: 1},
   WEEK: {name: "week", timeUnit: "day", order: 2},
   MONTH: {name: "month", timeUnit: "day", order: 3}
});

const PAGE_TYPE = Object.freeze
({
    FIRST: {}, LAST: {}, GENERAL: {}
});

const AGE_DISTRIBUTION = Object.freeze
({
    GLOBAL: {name: "global", order: 1},
    OLD: {name: "old", order: 2},
    NEW: {name: "new", order: 3}
});

const INTENSITY = Object.freeze
({
  LOW: {name: "low", order: 1},
  MEDIUM: {name: "medium", order: 2},
  HIGH: {name: "high", order: 3}
});

const VIEW = Object.freeze
({
    GLOBAL: {name: "global", order: 1},
    LADDER: {name: "ladder", order: 2},
    FOLLOWING_LADDER: {name: "following-ladder", order: 3},
    CHARACTER: {name: "character", order: 4},
    CHARACTER_SEARCH: {name: "character-search", order: 5},
    PERSONAL_CHARACTERS: {name: "personal-characters", order: 6},
    ONLINE: {name: "online", order: 7},
    TEAM_BUFFER: {name: "team-buffer", order: 8},
    TEAM_MMR:{name: "team-mmr", order: 9},
    CHARACTER_REPORTS: {name:"all-character-reports", order: 10},
    FOLLOWING_CHARACTERS: {name: "following-characters", order: 11},
    CLAN_SEARCH: {name: "clan-search", order: 12},
    CLAN_BUFFER: {name: "clan-buffer", order: 13},
    VERSUS: {name: "versus", order: 14},
    VOD_SEARCH: {name: "vod-search", order: 15},
    GROUP: {name: "group", order: 16},
    STREAM_SEARCH: {name: "stream-search", order: 17},
    TEAM_SEARCH: {name: "team-search", order: 18},
});

const VIEW_DATA = Object.freeze
({
    SEARCH: {name: "search", order: 1},
    LADDER_STATS: {name: "ladder-stats", order: 2},
    QUEUE_STATS: {name: "queue-stats", order: 3},
    LEAGUE_BOUNDS: {name: "league-bounds", order: 4},
    BUNDLE: {name: "bundle", order: 5},
    CHARACTER_STATS: {name: "character-stats", order: 6},
    VAR: {name: "var", order: 7},
    TEAMS: {name: "teams", order: 8}
});

const STATUS = Object.freeze
({
  BEGIN: {name: "begin", order: 1},
  SUCCESS: {name: "success", order: 2},
  ERROR: {name: "error", order: 3}
});

const LOADING_STATUS = Object.freeze
({
    NONE: {name: "none", className: "loading-none", order: 1},
    IN_PROGRESS: {name: "in-progress", className: "loading-in-progress", order: 2},
    COMPLETE: {name: "complete", className: "loading-complete", order: 3},
    ERROR: {name: "error", className: "loading-error", order: 4},
});

const THEME = Object.freeze
({
  LIGHT: {name: "light", order: 1},
  DARK: {name: "dark", order: 2}
});

const START_MODE = Object.freeze
({
  FULL: {name: "full", order: 1},
  MINIMAL: {name: "minimal", order: 2},
  ESSENTIAL: {name: "essential", order: 3},
  BARE: {name: "bare", order: 4}
});

const LADDER_RACE_STATS_TYPE = Object.freeze
({
  GAMES_PLAYED: {name: "games-played", description: "Games played by race", parameterSuffix: "GamesPlayed", order: 1},
  TEAM_COUNT: {name: "team-count", description: "Team count by race", parameterSuffix: "TeamCount", order: 2}
});

const TIER_RANGE = Object.freeze
({
    1: {bottomThreshold: 1.333, league: LEAGUE.MASTER, tierType: 0, order: 1},
    2: {bottomThreshold: 2.666, league: LEAGUE.MASTER, tierType: 1, order: 2},
    3: {bottomThreshold: 4, league: LEAGUE.MASTER, tierType: 2, order: 3},
    4: {bottomThreshold: 11.666, league: LEAGUE.DIAMOND, tierType: 0, order: 4},
    5: {bottomThreshold: 19.333, league: LEAGUE.DIAMOND, tierType: 1, order: 5},
    6: {bottomThreshold: 27, league: LEAGUE.DIAMOND, tierType: 2, order: 6},
    7: {bottomThreshold: 34.666, league: LEAGUE.PLATINUM, tierType: 0, order: 7},
    8: {bottomThreshold: 42.333, league: LEAGUE.PLATINUM, tierType: 1, order: 8},
    9: {bottomThreshold: 50, league: LEAGUE.PLATINUM, tierType: 2, order: 9},
    10: {bottomThreshold: 57.666, league: LEAGUE.GOLD, tierType: 0, order: 10},
    11: {bottomThreshold: 65.333, league: LEAGUE.GOLD, tierType: 1, order: 11},
    12: {bottomThreshold: 73, league: LEAGUE.GOLD, tierType: 2, order: 12},
    13: {bottomThreshold: 80.666, league: LEAGUE.SILVER, tierType: 0, order: 13},
    14: {bottomThreshold: 88.333, league: LEAGUE.SILVER, tierType: 1, order: 14},
    15: {bottomThreshold: 96, league: LEAGUE.SILVER, tierType: 2, order: 15},
    16: {bottomThreshold: 97.333, league: LEAGUE.BRONZE, tierType: 0, order: 16},
    17: {bottomThreshold: 98.666, league: LEAGUE.BRONZE, tierType: 1, order: 17},
    18: {bottomThreshold: 100, league: LEAGUE.BRONZE, tierType: 2, order: 18}
});

const CLAN_CURSOR = Object.freeze
({
    ACTIVE_MEMBERS:
    {
        name: "active-members",
        fullName: "ACTIVE_MEMBERS",
        getter: (c)=>c.activeMembers,
        minParamName: "minActiveMembers",
        maxParamName: "maxActiveMembers",
        order: 1
    },
    AVG_RATING:
    {
        name: "average-rating",
        fullName: "AVG_RATING",
        getter: (c)=>c.avgRating,
        minParamName: "minAverageRating",
        maxParamName: "maxAverageRating",
        order: 2
    },
    GAMES_PER_ACTIVE_MEMBER_PER_DAY:
    {
        name: "games-per-active-member-per-day",
        fullName: "GAMES_PER_ACTIVE_MEMBER_PER_DAY",
        getter: (c)=>c.games / c.activeMembers / CLAN_STATS_DEPTH_DAYS,
        minParamName: "minGamesPerActiveMemberPerDay",
        maxParamName: "maxGamesPerActiveMemberPerDay",
        order: 3
    },
    MEMBERS:
    {
        name: "members",
        fullName: "MEMBERS",
        getter: (c)=>c.members,
        minParamName: "minMembers",
        maxParamName: "maxMembers",
        order: 4
    }
});

const CHEATER_FLAG = Object.freeze
({
  REPORTED: {name: "reported", description: "This player has been reported, but report has not yet been confirmed by the moderators", cssClass: "info", order: 1},
  SUSPICIOUS: {name: "suspicious", description: "This player or one of their linked characters has a confirmed evidence of suspicious activity.", cssClass: "warning", order: 2},
  CHEATER: {name: "cheater", description: "This player or one of their linked characters has a confirmed evidence of cheating.", cssClass: "danger", order: 3}
});

const CLAN_MEMBER_EVENT_TYPE = Object.freeze
({
    JOIN: {name: "join", description: "Joined", element: ElementUtil.createIcoFontElement("arrow-right", "Joined", "text-success"), order: 1},
    LEAVE: {name: "leave", description: "Left", element: ElementUtil.createIcoFontElement("arrow-left", "Left", "text-danger"), order: 2}
});

const AUDIT_LOG_ACTION = Object.freeze
 ({
     INSERT: {name: "I", fullName: "INSERT", order: 1},
     UPDATE: {name: "U", fullName: "UPDATE", order: 2},
     DELETE: {name: "D", fullName: "DELETE", order: 3},
     TRUNCATE: {name: "T", fullName: "TRUNCATE", order: 4}
 });

const LADDER_STATS_GLOBAL_VIEW_MODE = Object.freeze
({
    MIXED:
    {
        code: 1,
        name: "mixed",
        fullName: "MIXED",
        sectionIds: new Set([
            "games-played-day",
            "team-count-global",
            "player-count-global",
            "player-count-daily-activity-tier"
        ]),
        order: 1
    },
    NORMALIZED:
    {
        code: 2,
        name: "normalized",
        fullName: "NORMALIZED",
        sectionIds: new Set([
            "games-played-day",
            "team-count-day",
            "player-count-day",
            "player-count-daily-activity-tier-day"
        ]),
        order: 2
    },
    RAW:
    {
        code: 3,
        name: "raw",
        fullName: "RAW",
        sectionIds: new Set([
            "games-played-global",
            "team-count-global",
            "player-count-global",
            "player-count-daily-activity-tier"
        ]),
        order: 3
    },
    MAX:
    {
        code: 4,
        name: "all",
        fullName: "MAX",
        sectionIds: new Set([
            "games-played-global",
            "team-count-global",
            "player-count-global",
            "player-count-daily-activity-tier",
            "games-played-day",
            "team-count-day",
            "player-count-day",
            "player-count-daily-activity-tier-day"
        ]),
        order: 4
    },
});

const TEAM_HISTORY_GROUP_MODE = Object.freeze
({
    TEAM: {code:1, name: "team", fullName: "TEAM", order: 1},
    LEGACY_UID: {code:2, name: "legacy-uid", fullName: "LEGACY_UID", order: 2}
});

const TEAM_HISTORY_STATIC_COLUMN = Object.freeze
({
    ID: {code:1, name: "id", fullName: "ID", order: 1},
    REGION: {code:2, name: "region", fullName: "REGION", order: 2},
    QUEUE_TYPE: {code:3, name: "queue", fullName: "QUEUE_TYPE", order: 3},
    TEAM_TYPE: {code:4, name: "type", fullName: "TEAM_TYPE", order: 4},
    LEGACY_ID: {code:5, name: "legacy-id", fullName: "LEGACY_ID", order: 5},
    SEASON: {code:6, name: "season", fullName: "SEASON", order: 6}
});

const TEAM_HISTORY_HISTORY_COLUMN = Object.freeze
({
    TIMESTAMP: {code:1, name: "timestamp", fullName: "TIMESTAMP", order: 1},
    RATING: {code:2, name: "rating", fullName: "RATING", order: 2},
    GAMES: {code:3, name: "games", fullName: "GAMES", order: 3},
    WINS: {code:4, name: "wins", fullName: "WINS", order: 4},
    LEAGUE_TYPE: {code:5, name: "league", fullName: "LEAGUE_TYPE", order: 5},
    TIER_TYPE: {code:6, name: "tier", fullName: "TIER_TYPE", order: 6},
    DIVISION_ID: {code:7, name: "division-id", fullName: "DIVISION_ID", order: 7},
    GLOBAL_RANK: {code:8, name: "global-rank", fullName: "GLOBAL_RANK", order: 8},
    REGION_RANK: {code:9, name: "region-rank", fullName: "REGION_RANK", order: 9},
    LEAGUE_RANK: {code:10, name: "league-rank", fullName: "LEAGUE_RANK", order: 10},
    GLOBAL_TEAM_COUNT: {code:11, name: "global-team-count", fullName: "GLOBAL_TEAM_COUNT", order: 11},
    REGION_TEAM_COUNT: {code:12, name: "region-team-count", fullName: "REGION_TEAM_COUNT", order: 12},
    LEAGUE_TEAM_COUNT: {code:13, name: "league-team-count", fullName: "LEAGUE_TEAM_COUNT", order: 13},
    ID: {code:14, name: "id", fullName: "ID", order: 14},
    SEASON: {code:15, name: "season", fullName: "SEASON", order: 15},
});

const TEAM_HISTORY_SUMMARY_COLUMN = Object.freeze
({
    GAMES: {code:1, name: "games", fullName: "GAMES", textContent: "Games", order: 1},
    RATING_MIN: {code:2, name: "rating-min", fullName: "RATING_MIN", textContent: "Min MMR", order: 2},
    RATING_AVG: {code:3, name: "rating-avg", fullName: "RATING_AVG", textContent: "Avg MMR", order: 3},
    RATING_MAX: {code:4, name: "rating-max", fullName: "RATING_MAX", textContent: "Max MMR", order: 4},
    RATING_LAST: {code:5, name: "rating-last", fullName: "RATING_LAST", textContent: "Last MMR", order: 5},
    REGION_RANK_LAST: {code:6, name: "region-rank-last", fullName: "REGION_RANK_LAST", textContent: "Last rank", order: 6},
    REGION_TEAM_COUNT_LAST: {code:7, name: "region-team-count-last", fullName: "REGION_TEAM_COUNT_LAST", textContent: "Last teams", order: 7},
});
