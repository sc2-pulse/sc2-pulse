// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

const REGION = Object.freeze
({
    US: {code:1, name: "us"},
    EU: {code:2, name: "eu"},
    KR: {code:3, name: "kr"},
    CN: {code:5, name: "cn"}
});

const RACE = Object.freeze
({
    TERRAN: {name: "terran"},
    PROTOSS: {name: "protoss"},
    ZERG: {name: "zerg"},
    RANDOM: {name: "random"}
});

const LEAGUE = Object.freeze
({
    BRONZE: {code:0, name: "bronze"},
    SILVER: {code:1, name: "silver"},
    GOLD: {code:2, name: "gold"},
    PLATINUM: {code:3, name: "platinum"},
    DIAMOND: {code:4, name: "diamond"},
    MASTER: {code:5, name: "master"},
    GRANDMASTER: {code:6, name: "grandmaster"}
});

const TEAM_FORMAT = Object.freeze
({
    _1V1: {code:201, name: "1V1", fullName: "LOTV_1V1", memberCount: 1},
    _2V2: {code:202, name: "2V2", fullName: "LOTV_2V2", memberCount: 2},
    _3V3: {code:203, name: "3V3", fullName: "LOTV_3V3", memberCount: 3},
    _4V4: {code:204, name: "4V4", fullName: "LOTV_4V4", memberCount: 4},
    ARCHON: {code:206, name: "Archon", fullName: "LOTV_ARCHON", memberCount: 2}
});

const TEAM_TYPE = Object.freeze
({
    ARRANGED: {code:0, name: "Arranged", fullName: "ARRANGED"},
    RANDOM: {code:1, name: "Random", fullName: "RANDOM"}
});

const PAGE_TYPE = Object.freeze
({
    FIRST: {}, LAST: {}, GENERAL: {}
});
