// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

const REGION = Object.freeze
({
    US: {code:1, name: "us", order: 1},
    EU: {code:2, name: "eu", order: 2},
    KR: {code:3, name: "kr", order: 3},
    CN: {code:5, name: "cn", order: 4}
});

const RACE = Object.freeze
({
    TERRAN: {name: "terran", order: 1},
    PROTOSS: {name: "protoss", order: 2},
    ZERG: {name: "zerg", order: 3},
    RANDOM: {name: "random", order: 4}
});

const LEAGUE = Object.freeze
({
    BRONZE: {code:0, name: "bronze", shortName: "bro", order: 1},
    SILVER: {code:1, name: "silver", shortName: "sil", order: 2},
    GOLD: {code:2, name: "gold", shortName: "gol", order: 3},
    PLATINUM: {code:3, name: "platinum", shortName: "pla", order: 4},
    DIAMOND: {code:4, name: "diamond", shortName: "dia", order: 5},
    MASTER: {code:5, name: "master", shortName: "mas", order: 6},
    GRANDMASTER: {code:6, name: "grandmaster", shortName: "gra", order: 7}
});

const TEAM_FORMAT = Object.freeze
({
    _1V1: {code:201, name: "1V1", fullName: "LOTV_1V1", memberCount: 1, order: 1},
    _2V2: {code:202, name: "2V2", fullName: "LOTV_2V2", memberCount: 2, order: 2},
    _3V3: {code:203, name: "3V3", fullName: "LOTV_3V3", memberCount: 3, order: 3},
    _4V4: {code:204, name: "4V4", fullName: "LOTV_4V4", memberCount: 4, order: 4},
    ARCHON: {code:206, name: "Archon", fullName: "LOTV_ARCHON", memberCount: 2, order: 5}
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
    CHARACTER_REPORTS: {name:"all-character-reports", order: 10}
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

const THEME = Object.freeze
({
  LIGHT: {name: "light", order: 1},
  DARK: {name: "dark", order: 2}
});

const START_MODE = Object.freeze
({
  FULL: {name: "full", order: 1},
  MINIMAL: {name: "minimal", order: 2}
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
