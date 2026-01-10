// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class StatsUtil
{

    static init()
    {
        if(StatsUtil.initialized) return;

        StatsUtil.initGlobalStats();
        StatsUtil.initialized = true;
    }

    static initGlobalStats()
    {
        const modeSelect = document.querySelector("#stats-global-mode");
        for(const enumVal of Object.values(LADDER_STATS_GLOBAL_VIEW_MODE))
            modeSelect.appendChild(ElementUtil.createElement("option", null, null, enumVal.name, [["value", enumVal.fullName]]));
        modeSelect.value = EnumUtil.enumOfStoredFullName(
            "stats-global-mode", LADDER_STATS_GLOBAL_VIEW_MODE, LADDER_STATS_GLOBAL_VIEW_MODE.MIXED)
                .fullName;

        StatsUtil.updateGlobalStatsMode();
    }

    static filterStats(stats, nullify, removeCurrentSeason, removeAbnormalSeasons)
    {
        if(removeCurrentSeason == null) removeCurrentSeason
            = localStorage.getItem("stats-global-remove-current-season") !== "false";
        if(removeAbnormalSeasons == null) removeAbnormalSeasons
            = localStorage.getItem("stats-global-remove-abnormal-seasons") !== "false";

        const filters = [];
        if(removeCurrentSeason) filters.push(SeasonUtil.isCurrentSeason);
        if(removeAbnormalSeasons) filters.push(SeasonUtil.isAbnormalSeason);

        if(filters.length == 0) return;

        Object.entries(stats)
            .filter(entry=>filters.some(filter=>filter(entry[0])))
            .forEach(entry=>nullify(entry[1]));
    }

    static updateQueueStatsModel(formParams)
    {
        const params = new URLSearchParams(formParams);
        const webParams = new URLSearchParams();
        webParams.append("queue", EnumUtil.enumOfFullName(params.get("queue"), TEAM_FORMAT).fullName);
        webParams.append("teamType", EnumUtil.enumOfFullName(params.get("teamType"), TEAM_TYPE).fullName);
        const request = `${ROOT_CONTEXT_PATH}api/stats/player-base?${webParams.toString()}`;
        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(Session.verifyJsonResponse)
            .then(json => {
                Model.DATA.get(VIEW.GLOBAL).set(VIEW_DATA.QUEUE_STATS, json);
                return json;
            });
    }

    static updateQueueStatsView()
    {
        StatsUtil.init();
        const searchResult = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.QUEUE_STATS);
        StatsUtil.updateQueueStatsPlayerCount(searchResult);
        StatsUtil.updateQueueStatsActivity(searchResult);
    }

    static updateQueueStatsPlayerCount(searchResult)
    {
        const playerCount = {};
        for(let i = 0; i < searchResult.length; i++)
        {
            const seasonStats = searchResult[i];
            playerCount[seasonStats.season] = {};
            if(i == 0)
            {
                playerCount[seasonStats.season]["new"] = seasonStats.playerBase;
            }
            else
            {
                playerCount[seasonStats.season]["new"] = seasonStats.playerBase - searchResult[i - 1].playerBase;
            }
            playerCount[seasonStats.season]["old"] = seasonStats.playerCount - playerCount[seasonStats.season]["new"];
            playerCount[seasonStats.season]["global"] = seasonStats.playerCount;
        }
        StatsUtil.filterStats(playerCount, s=>{
            s.global = null;
            s.new = null;
            s.old = null;
        });
        TableUtil.updateColRowTable
        (
            document.getElementById("player-count-global-table"),
            playerCount,
            (a, b)=>EnumUtil.enumOfName(a, AGE_DISTRIBUTION).order - EnumUtil.enumOfName(b, AGE_DISTRIBUTION).order,
            null,
            SeasonUtil.seasonIdTranslator
        );
        TableUtil.updateColRowTable
        (
            document.getElementById("player-count-day-table"),
            Util.forObjectValues(StatsUtil.calculateDailyStats(playerCount), v=>Math.round(v)),
            (a, b)=>EnumUtil.enumOfName(a, AGE_DISTRIBUTION).order - EnumUtil.enumOfName(b, AGE_DISTRIBUTION).order,
            null,
            SeasonUtil.seasonIdTranslator
        );
    }

    static updateQueueStatsActivity(searchResult)
    {
        const activity = {};
        for(let i = 0; i < searchResult.length; i++)
        {
            const seasonStats = searchResult[i];
            activity[seasonStats.season] = {};
            activity[seasonStats.season]["low"] = seasonStats.lowActivityPlayerCount;
            activity[seasonStats.season]["medium"] = seasonStats.mediumActivityPlayerCount;
            activity[seasonStats.season]["high"] = seasonStats.highActivityPlayerCount;
        }
        StatsUtil.filterStats(activity, s=>{
            s.low = null;
            s.medium = null;
            s.high = null;
        });
        TableUtil.updateColRowTable
        (
            document.getElementById("player-count-daily-activity-tier-table"),
            activity,
            (a, b)=>EnumUtil.enumOfName(a, INTENSITY).order - EnumUtil.enumOfName(b, INTENSITY).order,
            null,
            SeasonUtil.seasonIdTranslator
        );
        TableUtil.updateColRowTable
        (
            document.getElementById("player-count-daily-activity-tier-day-table"),
            Util.forObjectValues(StatsUtil.calculateDailyStats(activity), v=>Math.round(v)),
            (a, b)=>EnumUtil.enumOfName(a, INTENSITY).order - EnumUtil.enumOfName(b, INTENSITY).order,
            null,
            SeasonUtil.seasonIdTranslator
        );
    }

    static updateQueueStats(formParams)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return StatsUtil.updateQueueStatsModel(formParams)
            .then(json => {
                StatsUtil.updateQueueStatsView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
            })
            .catch(error => Session.onPersonalException(error));
    }

    static updateGlobalStatsView()
    {
        StatsUtil.updateLadderStatsView();
        StatsUtil.updateQueueStatsView();
    }

    static enhanceGlobalStatsCtl()
    {
        const modeCtl = document.querySelector("#stats-global-mode");
        if(modeCtl) modeCtl.addEventListener("change", e=>window.setTimeout(t=>StatsUtil.updateGlobalStatsMode(), 0));

        document.querySelectorAll(".stats-global-reload")
            .forEach(ctl=>ctl.addEventListener("change", e=>window.setTimeout(t=>StatsUtil.updateGlobalStatsView(), 0)));
    }

    static updateGlobalStatsMode(mode)
    {
        if(mode == null) mode = EnumUtil.enumOfStoredFullName(
            "stats-global-mode", LADDER_STATS_GLOBAL_VIEW_MODE, LADDER_STATS_GLOBAL_VIEW_MODE.MIXED);
        document.querySelectorAll("#stats-global .stats-section")
            .forEach(section=>{
                if(mode.sectionIds.has(section.id)) {
                    section.classList.remove("d-none");
                } else {
                    section.classList.add("d-none");
                }
            });
    }

    static updateLadderStatsModel(formParams)
    {

        const urlParams = new URLSearchParams("?" + formParams);
        StatsUtil.resetMapStatsFilm();
        Model.DATA.get(VIEW.GLOBAL).set(VIEW_DATA.LADDER_STATS, {urlParams: urlParams});
        return Promise.all
        ([
            StatsUtil.updateLadderStatsGlobalModel(formParams),
            StatsUtil.updateLadderStatsSeasonModel(urlParams)
         ])
        .then(jsons=>Model.DATA.get(VIEW.GLOBAL).set(VIEW_DATA.LADDER_STATS, {all: jsons[0], current: jsons[1], urlParams: urlParams}));
    }

    static updateLadderStatsGlobalModel(formParams)
    {
        const request = ROOT_CONTEXT_PATH + "api/stats/activity?" + formParams;
        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(Session.verifyJsonResponse)
            .then(json => json);
    }

    static updateLadderStatsSeasonModel(urlParams)
    {
        const request = `${ROOT_CONTEXT_PATH}api/ladder/stats/league/${urlParams.get('season')}/${urlParams.get('queue')}/${urlParams.get('teamType')}/${urlParams.getAll("region").join(',')}/${urlParams.getAll("league").join(',')}`;
        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(Session.verifyJsonResponse)
            .then(json => json);
    }

    static updateLadderStatsView()
    {
        StatsUtil.updateLadderStatsCurrentView();
        const searchResult = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).all;
        const globalResult = {gamesPlayed: {}, teamCount: {}};
        const percentageResult = {};
        for(const [seasonId, stats] of Object.entries(searchResult))
        {
            globalResult.gamesPlayed[seasonId] = {global: Object.values(stats.regionGamesPlayed).reduce((a, b) => a + b, 0)};
            globalResult.teamCount[seasonId] = {global: Object.values(stats.regionTeamCount).reduce((a, b) => a + b, 0)};
            for(const [param, vals] of Object.entries(stats))
            {
                if(percentageResult[param] == null) percentageResult[param] = {};
                percentageResult[param][seasonId] = {};
                const sum = Object.values(vals).reduce((a, b) => a + b, 0);
                for(const [header, value] of Object.entries(vals))
                    percentageResult[param][seasonId][header] = Util.calculatePercentage(value, sum);
            }
        }
        StatsUtil.filterStats(globalResult.teamCount, s=>s.global = null);
        StatsUtil.filterStats(globalResult.gamesPlayed, s=>s.global = null);
        StatsUtil.applyUserSettings(globalResult);
        TableUtil.updateColRowTable
            (document.getElementById("games-played-global-table"), globalResult.gamesPlayed, null, null, SeasonUtil.seasonIdTranslator);
        TableUtil.updateColRowTable
            (document.getElementById("team-count-global-table"), globalResult.teamCount, null, null, SeasonUtil.seasonIdTranslator);
        TableUtil.updateColRowTable
        (
            document.getElementById("games-played-day-table"),
            Util.forObjectValues(StatsUtil.calculateDailyStats(globalResult.gamesPlayed), v=>Math.round(v)),
            null, null, SeasonUtil.seasonIdTranslator
        );
        TableUtil.updateColRowTable
        (
            document.getElementById("team-count-day-table"),
            Util.forObjectValues(StatsUtil.calculateDailyStats(globalResult.teamCount), v=>Math.round(v)),
            null, null, SeasonUtil.seasonIdTranslator
        );

        const raceStatsType = StatsUtil.getRaceStatsType();
        if(StatsUtil.setRaceStatsStatus(raceStatsType, document.querySelectorAll("#games-played-race"))) {
            TableUtil.updateColRowTable
            (
                document.getElementById("games-played-race-table"), percentageResult["race" + raceStatsType.parameterSuffix],
                (a, b)=>EnumUtil.enumOfName(a, RACE).order - EnumUtil.enumOfName(b, RACE).order,
                (name)=>EnumUtil.enumOfName(name, RACE).name,
                SeasonUtil.seasonIdTranslator
            );
            document.querySelectorAll('#games-played-race .header .main')
                .forEach(header=>header.textContent = raceStatsType.description);
        }
        TableUtil.updateColRowTable
        (
            document.getElementById("games-played-region-table"),
            percentageResult.regionGamesPlayed,
            (a, b)=>EnumUtil.enumOfName(a, REGION).order - EnumUtil.enumOfName(b, REGION).order,
            (name)=>EnumUtil.enumOfName(name, REGION).name,
            SeasonUtil.seasonIdTranslator
        );
        TableUtil.updateColRowTable
        (
            document.getElementById("games-played-league-table"),
            percentageResult.leagueGamesPlayed,
            (a, b)=>EnumUtil.enumOfId(a, LEAGUE).order - EnumUtil.enumOfId(b, LEAGUE).order,
            (name)=>EnumUtil.enumOfId(name, LEAGUE).name,
            SeasonUtil.seasonIdTranslator
        );

        TableUtil.updateColRowTable
        (
            document.getElementById("team-count-region-table"),
            percentageResult.regionTeamCount,
            (a, b)=>EnumUtil.enumOfName(a, REGION).order - EnumUtil.enumOfName(b, REGION).order,
            (name)=>EnumUtil.enumOfName(name, REGION).name,
            SeasonUtil.seasonIdTranslator
        );
        TableUtil.updateColRowTable
        (
            document.getElementById("team-count-league-table"),
            percentageResult.leagueTeamCount,
            (a, b)=>EnumUtil.enumOfId(a, LEAGUE).order - EnumUtil.enumOfId(b, LEAGUE).order,
            (name)=>EnumUtil.enumOfId(name, LEAGUE).name,
            SeasonUtil.seasonIdTranslator
        );
    }

    static getRaceStatsType()
    {
        return EnumUtil.enumOfName(localStorage.getItem("stats-race-type") || "team-count", LADDER_RACE_STATS_TYPE);
    }

    static setRaceStatsStatus(raceStatsType, raceContainers)
    {
        const raceMsg = document.querySelector("#stats-race .msg-info");
        if(raceStatsType == LADDER_RACE_STATS_TYPE.TEAM_COUNT
            && EnumUtil.enumOfFullName(Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).urlParams.get("queue"), TEAM_FORMAT) != TEAM_FORMAT._1V1) {
            raceContainers.forEach(container=>container.classList.add("d-none"));
            raceMsg.textContent = "Only in 1v1 mode each race has a separate team. Team can have multiple races in other modes(2v2, 3v3, 4v4, Archon).";
            raceMsg.classList.remove("d-none");
            return false;
        }
        else
        {
            raceContainers.forEach(container=>container.classList.remove("d-none"));
            raceMsg.classList.add("d-none");
            return true;
        }
    }

    static enhanceRaceControls()
    {
        const typeCrl = document.querySelectorAll(".stats-race-ctl")
            .forEach(ctl=>ctl.addEventListener("change", e=>window.setTimeout(StatsUtil.updateLadderStatsView, 1)));
    }

    static applyUserSettings(globalResult)
    {
        const gamesOption = localStorage.getItem("settings-games-played-number");
        if(!gamesOption || gamesOption == "match") {
            const searchResult = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS);
            const matchParticipants = EnumUtil.enumOfFullName(searchResult.urlParams.get("queue"), TEAM_FORMAT).memberCount * 2;
            Object.values(globalResult.gamesPlayed)
                .filter(g=>g.global != null)
                .forEach(g=>g.global = Math.round(g.global / matchParticipants));
        }
    }


    static updateLadderStatsCurrentView()
    {
        const stats = Util.groupBy(Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).current, s=>s.season.region);
        StatsUtil.updateLadderStatsCurrentRaceView(stats);
        StatsUtil.updateLadderStatsCurrentLeagueView(stats);
    }

    static calculateLadderStatsCurrentRaceRegionValues(stats, raceStatsType)
    {
        const result = new Map();
        Object.values(RACE).forEach(race=>{
            const raceValues = new Map();
            result.set(race.name, raceValues);
            for(const [region, regionStats] of stats) {
                let games = 0;
                for(const leagueStats of regionStats)
                    games += leagueStats.leagueStats[race.name + raceStatsType.parameterSuffix];
                raceValues.set(region, games);
            }
        });
        return result;
    }

    static updateLadderStatsCurrentRaceView(stats)
    {
        document.querySelectorAll("#stats-race .table-race-league-region").forEach(t=>t.closest("section").classList.add("d-none"));
        const normalized = localStorage.getItem("stats-race-normalize") == "true";
        const deviation = localStorage.getItem("stats-race-deviation") == "true";
        const formattedLeagueStats = {};
        const formattedStatsPercentage = {};
        const raceStatsType = StatsUtil.getRaceStatsType();
        const raceRegionGames = StatsUtil.calculateLadderStatsCurrentRaceRegionValues(stats, raceStatsType);
        const raceGames = new Map(Array.from(raceRegionGames.entries())
            .map(entry=>[entry[0], Array.from(entry[1].values()).reduce((a, b) => a + b, 0)]));
        const raceOffset = normalized && deviation ? 100 / Object.values(RACE).length : 0;
        for(const [region, regionStats] of stats)
        {
            const regionStatsPercentage = {};
            formattedStatsPercentage[region] = regionStatsPercentage;
            for(const leagueStats of regionStats)
            {
                let totalGamesPlayed = 0;
                Object.values(RACE).forEach(race=>totalGamesPlayed += leagueStats.leagueStats[race.name + raceStatsType.parameterSuffix]);
                const league = EnumUtil.enumOfId(leagueStats.league.type, LEAGUE).name;
                if(!formattedLeagueStats[league]) formattedLeagueStats[league] = {};
                regionStatsPercentage[league] = {};
                for(const race of Object.values(RACE))
                {
                    const raceGamesStr = race.name + raceStatsType.parameterSuffix;
                    if(leagueStats.leagueStats[raceGamesStr]) {
                        formattedLeagueStats[league][race.name] = formattedLeagueStats[league][race.name] == null
                            ? leagueStats.leagueStats[raceGamesStr]
                            : formattedLeagueStats[league][race.name] + leagueStats.leagueStats[raceGamesStr];
                        regionStatsPercentage[league][race.name] = normalized
                            ? (leagueStats.leagueStats[raceGamesStr] / raceRegionGames.get(race.name).get(region))
                            : (leagueStats.leagueStats[raceGamesStr] / totalGamesPlayed) * 100;
                    } 
                }
                if(normalized) {
                    const summ = Object.values(regionStatsPercentage[league]).reduce((a, b) => a + b, 0);
                    for(const race of Object.values(RACE))
                        regionStatsPercentage[league][race.name]
                            = (regionStatsPercentage[league][race.name] / summ) * 100 - raceOffset;
                }
            }
        }
        if(normalized) {
            for(const [league, lStats] of Object.entries(formattedLeagueStats))
                for(const [race, games] of Object.entries(lStats))
                    lStats[race] = lStats[race] / raceGames.get(race)
        }
        for(const [league, lStats] of Object.entries(formattedLeagueStats)) {
            const totalGamesPlayed = Object.values(lStats).reduce((a, b)=>a+b, 0);
            for(const [race, games] of Object.entries(lStats)) lStats[race]
                = (games / totalGamesPlayed) * 100 - raceOffset;
        }

        if(!StatsUtil.setRaceStatsStatus(raceStatsType, document.querySelectorAll('[id^="games-played-race-league"]')))
            return;

        TableUtil.updateColRowTable
        (
            document.getElementById("games-played-race-league-global-table"), formattedLeagueStats,
            (a, b)=>EnumUtil.enumOfName(a, RACE).order - EnumUtil.enumOfName(b, RACE).order,
            (name)=>EnumUtil.enumOfName(name, RACE).name,
            (league)=>EnumUtil.enumOfName(league, LEAGUE).name
        );
        for(const [region, regionStats] of Object.entries(formattedStatsPercentage))
        {
            const table = document.getElementById("games-played-race-league-" + region.toLowerCase() + "-table");
            table.closest("section").classList.remove("d-none");
            TableUtil.updateColRowTable
            (
                table, regionStats,
                (a, b)=>EnumUtil.enumOfName(a, RACE).order - EnumUtil.enumOfName(b, RACE).order,
                (name)=>EnumUtil.enumOfName(name, RACE).name,
                (league)=>EnumUtil.enumOfName(league, LEAGUE).name
            );
        }
        if(Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).current.length > 0 )
        {
            const season = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).current[0].season.battlenetId;
            document.querySelectorAll("#stats-race .season-current").forEach(s=>s.textContent = "s" + season)
        }
        document.querySelectorAll('[id^="games-played-race-league"] .header .main')
            .forEach(header=>header.textContent = raceStatsType.description);
    }

    static updateLadderStatsCurrentLeagueView(stats)
    {
        const formattedStats = {};
        const formattedStatsPercentage = {};
        for(const [region, regionStats] of stats)
        {
             formattedStats[region] = {};
             for(const leagueStats of regionStats)
             {
                 const league = EnumUtil.enumOfId(leagueStats.league.type, LEAGUE).name;
                 formattedStats[region][league] = leagueStats.leagueStats.teamCount;
             }
        }
        for(const [region, regionStats] of Object.entries(formattedStats))
        {
            const teamsTotal = Object.values(regionStats).reduce((a, b)=>a+b, 0);
            formattedStatsPercentage[region] = {};
            for(const [league, games] of Object.entries(regionStats))
            {
                formattedStatsPercentage[region][league] = (formattedStats[region][league] / teamsTotal) * 100;
            }
        }
        TableUtil.updateColRowTable
        (
            document.getElementById("team-count-region-league-table"), formattedStatsPercentage,
            (a, b)=>EnumUtil.enumOfName(a, LEAGUE).order - EnumUtil.enumOfName(b, LEAGUE).order,
            (name)=>EnumUtil.enumOfName(name, LEAGUE).name,
            (region)=>EnumUtil.enumOfName(region, REGION).name
        );
        if(Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).current.length > 0 )
        {
            const season = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).current[0].season.battlenetId;
            document.querySelectorAll("#stats-league .season-current").forEach(s=>s.textContent = "s" + season)
        }
    }

    static updateLadderStats(formParams)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        StatsUtil.init();
        return StatsUtil.updateLadderStatsModel(formParams)
            .then(json => {
                StatsUtil.updateLadderStatsView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
            })
            .catch(error => Session.onPersonalException(error));
    }

    static updateLeagueBoundsModel(formParams)
    {
        const request = ROOT_CONTEXT_PATH + "api/tier-thresholds?" + formParams;
        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(Session.verifyJsonResponse)
            .then(json => {
                Model.DATA.get(VIEW.GLOBAL).set(VIEW_DATA.LEAGUE_BOUNDS, json);
                return json;
            });
    }

    static updateLeagueBoundsView()
    {
        const searchResult = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LEAGUE_BOUNDS);
        const table = document.getElementById("league-bounds-table");
        const headers = table.getElementsByTagName("thead")[0].getElementsByTagName("tr")[0];
        const body = table.getElementsByTagName("tbody")[0];
        ElementUtil.removeChildren(headers);
        ElementUtil.removeChildren(body);
        if(Object.keys(searchResult).length === 0) return;
        const leagueHeader = document.createElement("th");
        leagueHeader.setAttribute("scope", "col");
        leagueHeader.textContent = "Tier";
        headers.appendChild(leagueHeader);
        for(const region of Object.keys(searchResult))
        {
            const th = document.createElement("th");
            th.setAttribute("scope", "col");
            th.appendChild(ElementUtil.createImage("flag/", region.toLowerCase(), "table-image table-image-long"));
            headers.appendChild(th);
        }
        const leagues = new Set(Object.values(searchResult)
            .flatMap(r=>Object.keys(r))
            .sort((a, b)=>b - a));
        for(const leagueId of leagues)
        {
            const league = EnumUtil.enumOfId(leagueId, LEAGUE);
            for(const tierId of league == LEAGUE.GRANDMASTER ? [0] : [0, 1, 2])
            {
                const tr = document.createElement("tr");
                const th = document.createElement("th");
                th.setAttribute("scope", "row");
                const leagueDiv = document.createElement("div");
                leagueDiv.classList.add("text-nowrap");
                leagueDiv.appendChild(ElementUtil.createImage("league/", league.name, "table-image table-image-square mr-1"));
                leagueDiv.appendChild(ElementUtil.createImage("league/", "tier-" + (1 + + tierId), "table-image-additional"));
                th.appendChild(leagueDiv);
                tr.appendChild(th);
                for(const region of Object.keys(searchResult))
                {
                    const td = document.createElement("td");
                    if(league === LEAGUE.GRANDMASTER) {
                        td.textContent = "Top " + SC2Restful.GM_COUNT;
                    }
                    else if
                    (
                        searchResult[region] == null
                        || searchResult[region][leagueId] == null
                        || searchResult[region][leagueId][tierId] == null
                        || (searchResult[region][leagueId][tierId][0] == 0
                            && searchResult[region][leagueId][tierId][1] == 0)
                    )
                    {
                        td.textContent = "";
                    }
                    else
                    {
                        const range = searchResult[region][leagueId][tierId];
                        td.textContent = range[0] + "-" + range[1];
                    }
                    tr.appendChild(td);
                }
                body.appendChild(tr);
            }
        }
    }

    static updateLeagueBounds(formParams)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return StatsUtil.updateLeagueBoundsModel(formParams)
            .then(json => {
                StatsUtil.updateLeagueBoundsView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
            })
            .catch(error => Session.onPersonalException(error));
    }

    static calculateDailyStats(stats)
    {
        SeasonUtil.updateSeasonDuration(Session.currentSeasons[0]);
        const dailyStats = {};
        for(const [seasonId, seasonStats] of Object.entries(stats))
        {
            dailyStats[seasonId] = {};
            const season = Session.currentSeasons.filter(s=>s.battlenetId == seasonId)[0];
            for(const [key, val] of Object.entries(seasonStats))
            {
                dailyStats[seasonId][key] = val == null ? null : val / season.daysProgress;
            }
        }
        return dailyStats;
    }

    static getMapStatsFilm(season, regions, queue, teamType, league, tier, crossTier, races)
    {
        const urlParams = new URLSearchParams();
        urlParams.set("season", season);
        regions.forEach(region=>urlParams.append("region", region.fullName));
        urlParams.set("queue", queue.fullName);
        urlParams.set("teamType", teamType.fullName);
        urlParams.set("league", league.fullName);
        urlParams.set("tier", tier.fullName);
        crossTier.forEach(ct=>urlParams.append("crossTier", ct));
        races.forEach(race=>urlParams.append("race", race.fullName));
        const request = `${ROOT_CONTEXT_PATH}api/stats/balance-reports?${urlParams.toString()}`;
        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(Session.verifyJsonResponse);
    }

    static concatMapStatsResolvedFilm(a, b)
    {
        const conceited = {};
        for(const matchUp of Object.keys(a)) {
            conceited[matchUp] = {};
            for(const map of Object.keys(a[matchUp])) {
                conceited[matchUp][map] = a[matchUp][map].concat(b[matchUp][map]);
            }
        }
        return conceited;
    }

    static deriveMapFilmSummary(specMapFilmsResolved, specMapFilmsResolvedFiltered, propertyNames)
    {
        const conceited = StatsUtil.concatMapStatsResolvedFilm(specMapFilmsResolved, specMapFilmsResolvedFiltered);
        const mapSummaryEntries = Array.from(Object.entries(conceited));
        const mapSummary = new Array(mapSummaryEntries.length);
        for(let mapSummaryIx = 0; mapSummaryIx < mapSummary.length; mapSummaryIx++) {
            const [matchUp, matchUpValue] = mapSummaryEntries[mapSummaryIx];
            const parsedMatchUp = Util.parseMatchUp(matchUp);
            mapSummary[mapSummaryIx] = {
                race: parsedMatchUp[0],
                versusRace: parsedMatchUp[1],
                name: matchUp,
                values: Array.from(Object.entries(matchUpValue))
                    .map(([map, mapValue])=>{return {
                        category: map,
                        value: StatsUtil.calculateMapFrame(Util.addObjects(mapValue, propertyNames))}})
            };
        }
        mapSummary.sort((a, b)=>a.race.order - b.race.order || a.versusRace.order - b.versusRace.order);
        return mapSummary;
    }
    
    static copyFrameSeries(series, propertyName)
    {
        return {
            race: series.race,
            versusRace: series.versusRace,
            label: series.label,
            data: series.data.map(frame=>frame[propertyName])
        }
    }

    static deriveMapFilmDuration(specMapFilmsResolved, propertyNames, mergeFactor = 2)
    {
        const data = Object.entries(specMapFilmsResolved)
            .map(entry=>{
                const matchUp = entry[0];
                const parsedMatchUp = Util.parseMatchUp(matchUp);
                const value = Util.mergeObjects(Util.addObjectColumns(
                    Object.values(entry[1]), propertyNames), propertyNames, mergeFactor)
                        .map(StatsUtil.calculateMapFrame);
                return {
                    race: parsedMatchUp[0],
                    versusRace: parsedMatchUp[1],
                    label: matchUp,
                    data: value
                }
            });
        data.sort((a, b)=>a.race.order - b.race.order || a.versusRace.order - b.versusRace.order);
        const winRateDataSets = new Array(data.length);
        const gamesRateDataSets = new Array(data.length);
        for(let i = 0; i < data.length; i++) {
            winRateDataSets[i] = StatsUtil.copyFrameSeries(data[i], "winRate");
            gamesRateDataSets[i] = StatsUtil.copyFrameSeries(data[i], "games");
        }
        return {winRate: winRateDataSets, games: gamesRateDataSets, mergeFactor: mergeFactor};
    }

    static transformMapStatsFilm(film)
    {
        Object.values(film.films).forEach(film=>{
            film.frames = new Array(StatsUtil.MAP_STATS_FILM_MAX_FRAME + 1);
            film.filteredFrames = [];
        });
        film.frames.forEach(frame=>{
            const curFilm = film.films[frame.mapStatsFilmId];
            if(frame.number != null && frame.number < curFilm.frames.length) {
                curFilm.frames[frame.number] = frame;
            } else {
                curFilm.filteredFrames.push(frame);
            }
        });
        const specMapFilms = Util.groupByObject(Object.values(film.films), film=>[film.mapStatsFilmSpecId, film.mapId]);
        const specMapFilmsResolved = {};
        const specMapFilmsResolvedFiltered = {};
        const propertyNames = ["wins", "games"];
        for(const [specId, matchUpValue] of Object.entries(specMapFilms)) {
            const spec = film.specs[specId];
            const matchUp = spec.race.charAt(0) + "v" + spec.versusRace.charAt(0);
            specMapFilmsResolved[matchUp] = {};
            specMapFilmsResolvedFiltered[matchUp] = {};
            for(const [mapId, mapValue] of  Object.entries(matchUpValue)) {
                specMapFilmsResolved[matchUp][film.maps[mapId].name]
                    = Util.addObjectColumns(mapValue.values.map(film=>film.frames), propertyNames);
                specMapFilmsResolvedFiltered[matchUp][film.maps[mapId].name]
                    = Util.addObjects(mapValue.values.flatMap(film=>film.filteredFrames), propertyNames);
            }
        }
        return {
            summary: StatsUtil.deriveMapFilmSummary(
                specMapFilmsResolved,
                specMapFilmsResolvedFiltered,
                propertyNames),
            duration: StatsUtil.deriveMapFilmDuration(
                specMapFilmsResolved,
                propertyNames,
                parseInt(localStorage.getItem("stats-match-up-group-duration"))
                    || StatsUtil.MAP_STATS_FILM_DEFAULT_GROUP_DURATION)
        };
    }

    static calculateMapFrame(frame)
    {
        if(frame.games == 0 || frame.games == null) {
            frame.games = null;
            frame.wins = null;
            frame.winRate = null;
        } else {
            frame.winRate = (frame.wins / frame.games) * 100;
        }
        return frame;
    }

    static mapFrameStringConverter(name, value)
    {
        return value == null
            ? null
            : name == "winRate"
                ? (Util.DECIMAL_FORMAT.format(value) + "%")
                : value;
    }

    static loadMapStatsFilmModel(season, regions, queue, teamType, leagueAndTiers, crossTier, races)
    {
        if(queue != TEAM_FORMAT._1V1) return Promise.resolve();

        return Promise.all(leagueAndTiers.map(lt=>
            StatsUtil.getMapStatsFilm(season, regions, queue, teamType, lt[0], lt[1], crossTier, races)))
                .then(films=>{
                    let film = {};
                    films.filter(f=>f.frames.length > 0).forEach(f=>Util.concatObject(f, film));
                    if(!film.frames) film = null;
                    Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).mapFilm = film;
                    return film;
                });
    }

    static updateMapStatsFilmModel(film)
    {
        const statsData = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS);
        if(!film) film = statsData.mapFilm;
        if(!film) return null;

        statsData.mapFilmModel = StatsUtil.transformMapStatsFilm(film);
        return statsData.mapFilmModel;
    }

    static addMapFilmSummaryRaceColors(summary)
    {
        for(const summarySeries of summary) {
            summarySeries.backgroundColors = {
                light: {
                    negative: StatsUtil.MATCH_UP_MATRIX_LIGHT_BACKGROUND_COLORS.get(summarySeries.versusRace),
                    positive: StatsUtil.MATCH_UP_MATRIX_LIGHT_BACKGROUND_COLORS.get(summarySeries.race)
                },
                dark: {
                    negative: StatsUtil.MATCH_UP_MATRIX_DARK_BACKGROUND_COLORS.get(summarySeries.versusRace),
                    positive: StatsUtil.MATCH_UP_MATRIX_DARK_BACKGROUND_COLORS.get(summarySeries.race)
                }
            };
            summarySeries.colors = {
                negative: StatsUtil.MATCH_UP_MATRIX_COLORS.get(summarySeries.versusRace),
                neutral: MatrixUI.HIGHLIGHT_NEUTRAL_COLOR,
                positive: StatsUtil.MATCH_UP_MATRIX_COLORS.get(summarySeries.race)
            }
        }
    }

    static setMapFilmWinRateThreshold(matrix)
    {
        const threshold = parseFloat(localStorage.getItem("stats-match-up-win-rate-highlight-threshold"))
            || StatsUtil.MAP_STATS_FILM_DEFAULT_WIN_RATE_HIGHLIGHT_THRESHOLD;
        matrix.setHighlightRange(50 - threshold, 50, 50 + threshold);
    }

    static setMapSummaryWinRate(matrix, winRate)
    {
        matrix.getSummaryRow().forEach(col=>col.winRate = winRate);
        matrix.getSummaryCell()[0].winRate = winRate;
    }

    static getWinRateDataFoV()
    {
        const durationModel = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).mapFilmModel.duration;
        if(durationModel.winRateDataFoV != null) return durationModel.winRateDataFoV;

        const fov = Math.ceil(Math.max(...durationModel.winRate
            .flatMap(series=>series.data)
            .map(val=>Math.abs(val - 50))))
            + StatsUtil.MAP_STATS_FILM_DEFAULT_WIN_RATE_DURATION_FOV_OFFSET;
        durationModel.winRateDataFoV = fov;
        return fov;
    }

    static getWinRateFoV()
    {
        const fov = parseInt(localStorage.getItem("stats-match-up-win-rate-fov")
            || StatsUtil.MAP_STATS_FILM_DEFAULT_WIN_RATE_DURATION_FOV);
        return fov > 0 ? fov : StatsUtil.getWinRateDataFoV();
    }

    static updateWinRateFoV(chart)
    {
        if(!chart) chart = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).charts[0];
        if(!chart) return;

        const fov = StatsUtil.getWinRateFoV();
        chart.config.options.scales.y.min = 50 - fov;
        chart.config.options.scales.y.max = 50 + fov;
        chart.update();
    }

    static updateMapStatsFilmView()
    {
        const container = document.querySelector("#stats-match-up-container");
        const model = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS);
        if(!model.mapFilmModel) {
            container.appendChild(ElementUtil.createElement("p", null, "text-danger", "Stats not found"));
            return;
        }

        StatsUtil.addMapFilmSummaryRaceColors(model.mapFilmModel.summary);
        const mapSummaryMatrix = new MatrixUI(
            "stats-map-film-summary-table",
            model.mapFilmModel.summary,
            "winRate",
            ["winRate", "games"],
            Session.theme,
            StatsUtil.calculateMapFrame,
            StatsUtil.mapFrameStringConverter);
        mapSummaryMatrix.setAfterDataProcessing(()=>StatsUtil.setMapSummaryWinRate(mapSummaryMatrix, 50));
        StatsUtil.setMapFilmHighlight(mapSummaryMatrix);
        const summaryElement = mapSummaryMatrix.render();
        summaryElement.classList.add("mx-auto", "mb-3");
        container.appendChild(summaryElement);
        model.mapFilmSummaryMatrix = mapSummaryMatrix;

        const durationColors = model.mapFilmModel.duration.winRate
            .map(dataSet=>{
                if(dataSet.race == RACE.RANDOM)
                    return StatsUtil.MATCH_UP_RANDOM_COLORS.get(dataSet.versusRace);
                return dataSet.race.name;
            });
        const chartRow = ElementUtil.createElement("div", null, "row no-gutters");
        const winRateSection = ElementUtil.createElement("section", null, "col-lg-6 mb-3");
        winRateSection.appendChild(ElementUtil.createElement(
            "h4", null, null, "Win rate, distribution by game duration"));
        const gamesSection = ElementUtil.createElement("section", null, "col-lg-6 mb-3");
        gamesSection.appendChild(ElementUtil.createElement(
            "h4", null, null, "Game count, distribution by game duration"));
        chartRow.appendChild(winRateSection);
        chartRow.appendChild(gamesSection);

        model.charts = new Array(2);
        winRateSection.appendChild(ChartUtil.createChartContainer("stats-map-film-duration-win-rate"));
        const winRateConfig = {
            type: "line",
            chartable: "stats-map-film-duration-win-rate",
            ctx: winRateSection.querySelector(":scope #stats-map-film-duration-win-rate").getContext("2d"),
            xTitle: "Match duration, minutes",
            yTitle: "Win rate",
            customAnnotations: "50",
            data: {
                labels: [...Array(model.mapFilmModel.duration.winRate[0].data.length).keys()]
                    .map(e=>(e * model.mapFilmModel.duration.mergeFactor) + ""),
                datasets: model.mapFilmModel.duration.winRate,
                customColors: durationColors,
                customMeta: {type: "line"},
            }
        }
        model.charts[0] = ChartUtil.createGenericChart(winRateConfig);
        StatsUtil.updateWinRateFoV(model.charts[0]);

        gamesSection.appendChild(ChartUtil.createChartContainer("stats-map-film-duration-games"));
        const gamesConfig = {
            type: "line",
            chartable: "stats-map-film-duration-games",
            ctx: gamesSection.querySelector(":scope #stats-map-film-duration-games").getContext("2d"),
            xTitle: "Match duration, minutes",
            yTitle: "Games",
            data: {
                labels: [...Array(model.mapFilmModel.duration.games[0].data.length).keys()]
                    .map(e=>(e * model.mapFilmModel.duration.mergeFactor) + ""),
                datasets: model.mapFilmModel.duration.games,
                customColors: durationColors,
                customMeta: {type: "line"},
            }
        }
        model.charts[1] = ChartUtil.createGenericChart(gamesConfig);

        container.appendChild(chartRow);

        return model;
    }

    static initMapStatsFilm()
    {
        if(StatsUtil.mapStatsFilmInitialized) return;

        BootstrapUtil.appendDefaultInputValueTooltip(
            "stats-match-up-group-duration",
            StatsUtil.MAP_STATS_FILM_DEFAULT_GROUP_DURATION);
        BootstrapUtil.appendDefaultInputValueTooltip(
            "stats-match-up-win-rate-highlight-threshold",
            StatsUtil.MAP_STATS_FILM_DEFAULT_WIN_RATE_HIGHLIGHT_THRESHOLD);
        BootstrapUtil.appendDefaultInputValueTooltip(
            "stats-match-up-win-rate-fov",
            StatsUtil.MAP_STATS_FILM_DEFAULT_WIN_RATE_DURATION_FOV);

        if(!localStorage.getItem("stats-match-up-group-duration"))
            document.querySelector("#stats-match-up-group-duration").value
                = StatsUtil.MAP_STATS_FILM_DEFAULT_GROUP_DURATION;
        if(!localStorage.getItem("stats-match-up-win-rate-highlight-threshold"))
            document.querySelector("#stats-match-up-win-rate-highlight-threshold").value
                = StatsUtil.MAP_STATS_FILM_DEFAULT_WIN_RATE_HIGHLIGHT_THRESHOLD;
        if(!localStorage.getItem("stats-match-up-win-rate-fov"))
            document.querySelector("#stats-match-up-win-rate-fov").value
                = StatsUtil.MAP_STATS_FILM_DEFAULT_WIN_RATE_DURATION_FOV;
        StatsUtil.mapStatsFilmInitialized = true;
    }

    static updateMapStatsFilm()
    {
        StatsUtil.initMapStatsFilm();
        if(!document.querySelector("#stats-match-up-form").reportValidity())
            return Promise.resolve({data: null, status: LOADING_STATUS.ERROR});
        const leagueAndTierText = (localStorage.getItem("stats-match-up-league") || ("5,0" + Session.multiValueInputSeparator + "6,0"));
        const leagueAndTiers = leagueAndTierText.split(Session.multiValueInputSeparator)
            .map(leagueAndTier=>leagueAndTier.split(","))
            .map(leagueAndTier=>[EnumUtil.enumOfId(leagueAndTier[0], LEAGUE), EnumUtil.enumOfId(leagueAndTier[1], LEAGUE_TIER)]);
        const crossTier = leagueAndTiers.length > 1 || (localStorage.getItem("stats-match-up-cross-tier") || "false") === "false"
            ? [false]
            : [false, true];
        const urlParams = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).urlParams;
        const regions = Array.from(Object.values(REGION))
            .map(region=>urlParams.get(region.name) == "true" ? region : null)
            .filter(region=>region != null);
        const races = Array.from(Object.values(RACE))
            .filter(race=>(localStorage.getItem("stats-match-up-race-" + race.fullName) || (race == RACE.RANDOM ? "false" : "true"))
                == "true");
        return StatsUtil.loadMapStatsFilmModel(
            urlParams.get("season"),
            regions,
            EnumUtil.enumOfFullName(urlParams.get("queue"), TEAM_FORMAT),
            EnumUtil.enumOfFullName(urlParams.get("teamType"), TEAM_TYPE),
            leagueAndTiers,
            crossTier,
            races
        )
            .then(StatsUtil.updateMapStatsFilmModel)
            .then(StatsUtil.updateMapStatsFilmView)
            .then(view=>{return {data: view, status: LOADING_STATUS.COMPLETE}})
    }

    static updateMapStatsFilmAsync()
    {
        return Util.load(document.querySelector("#stats-match-up-container"), StatsUtil.updateMapStatsFilm);
    }

    static resetAndUpdateMapStatsFilmAsync()
    {
        return ElementUtil.executeTask("stats-match-up-container", ()=>StatsUtil.resetMapStatsFilm())
            .then(StatsUtil.updateMapStatsFilmAsync);
    }

    static resetMapStatsFilm(fullClear = true)
    {
        const container = document.querySelector("#stats-match-up-container");
        if(fullClear) ElementUtil.setLoadingIndicator(container, LOADING_STATUS.NONE);
        ElementUtil.removeChildren(container);
        const model = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS);
        if(model) {
            if(model.mapFilmSummaryMatrix) model.mapFilmSummaryMatrix.remove();
            model.mapFilmSummaryMatrix = null;
            if(fullClear) model.mapFilmModel = null;
            if(model.charts) {
                for(const chart of model.charts) ChartUtil.CHARTS.delete(chart.config._config.customConfig.chartable);
                model.charts = null;
            }
        }
    }

    static onMapFilmSummaryHighlightChange()
    {
        const model = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS);
        if(model.mapFilmSummaryMatrix && model.mapFilmSummaryMatrix.getNode()) {
            model.mapFilmSummaryMatrix.setUseDataColors((localStorage.getItem("stats-match-up-color") || "race") == "race");
            model.mapFilmSummaryMatrix.highlight();
        }
    }

    static onMapFilmGroupDurationChange()
    {
        const form = document.querySelector("#stats-match-up-form");
        if(!form.reportValidity()) return;

        if(!Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).mapFilmModel)
            return StatsUtil.resetAndUpdateMapStatsFilmAsync();

        StatsUtil.resetMapStatsFilm(false);
        StatsUtil.updateMapStatsFilmModel();
        return StatsUtil.updateMapStatsFilmView();
    }

    static onMapFilmGroupWinRateHighlightThresholdChange()
    {
        const matrix = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).mapFilmSummaryMatrix;
        if(!matrix) return;

        StatsUtil.setMapFilmWinRateThreshold(matrix);
        if(matrix.getNode()) matrix.highlight();
    }

    static setMapFilmHighlight(matrix)
    {
        if(!matrix) matrix = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).mapFilmSummaryMatrix;
        if(!matrix) return;

        const highlight = localStorage.getItem("stats-match-up-highlight") || "win-rate";
        if(highlight == "win-rate") {
            matrix.setMainParameter("winRate");
            StatsUtil.setMapFilmWinRateThreshold(matrix);
            matrix.setUseDataColors((localStorage.getItem("stats-match-up-color") || "race") == "race");
        } else {
            matrix.setMainParameter("games");
            matrix.setHighlightRange(0, 0, null);
            matrix.setUseDataColors(false);
        }
        if(matrix.getNode()) {
            matrix.applyMainParameter();
            matrix.highlight();
        }
    }

    static onUpdateWinRateFoV()
    {
        const form = document.querySelector("#stats-match-up-form");
        if(!form.reportValidity()) return;

        StatsUtil.updateWinRateFoV();
    }

    static enhanceMapStatsFilm()
    {
        ElementUtil.ELEMENT_TASKS.set("stats-match-up-tab", StatsUtil.updateMapStatsFilmAsync);
        document.querySelectorAll(".stats-match-up-reload")
            .forEach(ctl=>ctl.addEventListener("change", e=>window.setTimeout(StatsUtil.resetAndUpdateMapStatsFilmAsync, 1)));
        const colorCtl = document.querySelector("#stats-match-up-color");
        if(colorCtl) colorCtl.addEventListener("change", e=>window.setTimeout(StatsUtil.onMapFilmSummaryHighlightChange, 1));
        const form = document.querySelector("#stats-match-up-form");
        if(form) form.addEventListener("submit", e=>e.preventDefault());
        const groupCtl = document.querySelector("#stats-match-up-group-duration");
        if(groupCtl) groupCtl.addEventListener("input", e=>window.setTimeout(StatsUtil.onMapFilmGroupDurationChange, 1));
        const winRateThresholdCtl = document.querySelector("#stats-match-up-win-rate-highlight-threshold");
        if(winRateThresholdCtl) winRateThresholdCtl
            .addEventListener("input", e=>window.setTimeout(StatsUtil.onMapFilmGroupWinRateHighlightThresholdChange, 1));
        const highlightCtl = document.querySelector("#stats-match-up-highlight");
        if(highlightCtl) highlightCtl
            .addEventListener("change", e=>window.setTimeout(StatsUtil.setMapFilmHighlight, 1));
        const winRateFoVCtl = document.querySelector("#stats-match-up-win-rate-fov");
            if(winRateFoVCtl) winRateFoVCtl
                .addEventListener("input", e=>window.setTimeout(StatsUtil.onUpdateWinRateFoV, 1));
    }

    static enhanceSettings()
    {
        document.querySelector("#settings-games-played-number").addEventListener("change", e=>window.setTimeout(t=>{
            if(Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS)) StatsUtil.updateLadderStatsView();
            StatsUtil.updateGamesStatsVisibility();
        }, 1));
    }

    static updateGamesStatsVisibility()
    {
        const gamesOption = localStorage.getItem("settings-games-played-number");
        if(!gamesOption || gamesOption == "match") {
            document.querySelectorAll(".games-participant").forEach(elem=>elem.classList.add("d-none"));
        } else {
            document.querySelectorAll(".games-participant").forEach(elem=>elem.classList.remove("d-none"));
        }
    }

}

StatsUtil.MAP_STATS_FILM_MAX_FRAME = 29;
StatsUtil.MAP_STATS_FILM_MAIN_FRAME = 8;
StatsUtil.MAP_STATS_FILM_DEFAULT_GROUP_DURATION = 3;
StatsUtil.MAP_STATS_FILM_DEFAULT_WIN_RATE_HIGHLIGHT_THRESHOLD = 5;
StatsUtil.MAP_STATS_FILM_DEFAULT_WIN_RATE_DURATION_FOV = 15;
StatsUtil.MAP_STATS_FILM_DEFAULT_WIN_RATE_DURATION_FOV_OFFSET = 2;
StatsUtil.MATCH_UP_RANDOM_COLORS = new Map([[RACE.TERRAN, "neutral"], [RACE.PROTOSS, "new"], [RACE.ZERG, "old"]]);
StatsUtil.MATCH_UP_MATRIX_COLORS = new Map([
    [RACE.TERRAN, "rgba(53, 123, 167, 1)"],
    [RACE.PROTOSS, "rgba(167, 150, 2, 1)"],
    [RACE.ZERG, "rgba(167, 40, 167, 1)"],
    [RACE.RANDOM, "rgba(128, 128, 128, 1)"]
]);
StatsUtil.MATCH_UP_MATRIX_LIGHT_BACKGROUND_COLORS = new Map([
    [RACE.TERRAN, "rgba(53, 123, 255, 1)"],
    [RACE.PROTOSS, "rgba(255, 192, 0, 1)"],
    [RACE.ZERG, "rgba(255, 40, 255, 1)"],
    [RACE.RANDOM, "rgba(128, 128, 128, 1)"]
]);
StatsUtil.MATCH_UP_MATRIX_DARK_BACKGROUND_COLORS = new Map(
    Array.from(StatsUtil.MATCH_UP_MATRIX_LIGHT_BACKGROUND_COLORS.entries())
        .map(([race, color])=>[race, Util.divideColor(color, race == RACE.RANDOM ? 1.1 : 2)])
);
