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
    BRONZE: {code:0, name: "bronze", order: 1},
    SILVER: {code:1, name: "silver", order: 2},
    GOLD: {code:2, name: "gold", order: 3},
    PLATINUM: {code:3, name: "platinum", order: 4},
    DIAMOND: {code:4, name: "diamond", order: 5},
    MASTER: {code:5, name: "master", order: 6},
    GRANDMASTER: {code:6, name: "grandmaster", order: 7}
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
    ARRANGED: {code:0, name: "Arranged", fullName: "ARRANGED", order: 1},
    RANDOM: {code:1, name: "Random", fullName: "RANDOM", order: 2}
});

const PAGE_TYPE = Object.freeze
({
    FIRST: {}, LAST: {}, GENERAL: {}
});
