// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class StatsUtil
{

    static updateQueueStatsModel(formParams)
    {
        const params = new URLSearchParams(formParams);
        const queueType = EnumUtil.enumOfFullName(params.get("queue"), TEAM_FORMAT);
        const teamType = EnumUtil.enumOfFullName(params.get("team-type"), TEAM_TYPE);
        const request = `${ROOT_CONTEXT_PATH}api/ladder/stats/queue/${queueType.fullName}/${teamType.fullName}`;
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

    static updateLadderStatsModel(formParams)
    {

        const urlParams = new URLSearchParams("?" + formParams);
        return Promise.all
        ([
            StatsUtil.updateLadderStatsGlobalModel(formParams),
            StatsUtil.updateLadderStatsSeasonModel(urlParams)
         ])
        .then(jsons=>Model.DATA.get(VIEW.GLOBAL).set(VIEW_DATA.LADDER_STATS, {all: jsons[0], current: jsons[1], urlParams: urlParams}));
    }

    static updateLadderStatsGlobalModel(formParams)
    {
        const request = ROOT_CONTEXT_PATH + "api/ladder/stats?" + formParams;
        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(Session.verifyJsonResponse)
            .then(json => json);
    }

    static updateLadderStatsSeasonModel(urlParams)
    {
        const regions = [];
        for(const region of Object.values(REGION)) if(urlParams.has(region.name)) regions.push(region.name.toUpperCase());
        const leagues = [];
        for(const league of Object.values(LEAGUE)) if(urlParams.has(league.shortName)) leagues.push(league.name.toUpperCase());

        const request = `${ROOT_CONTEXT_PATH}api/ladder/stats/league/${urlParams.get('season')}/${urlParams.get('queue')}/${urlParams.get('team-type')}/${regions.join(',')}/${leagues.join(',')}`;
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
            Object.values(globalResult.gamesPlayed).forEach(g=>g.global = Math.round(g.global / matchParticipants));
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
        return StatsUtil.updateLadderStatsModel(formParams)
            .then(json => {
                StatsUtil.updateLadderStatsView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
            })
            .catch(error => Session.onPersonalException(error));
    }

    static updateLeagueBoundsModel(formParams)
    {
        const request = ROOT_CONTEXT_PATH + "api/ladder/league/bounds?" + formParams;
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
                dailyStats[seasonId][key] = val / season.days;
            }
        }
        return dailyStats;
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
