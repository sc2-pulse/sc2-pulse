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
            .then(json => new Promise((res, rej)=>{Model.DATA.get(VIEW.GLOBAL).set(VIEW_DATA.QUEUE_STATS, json); res(json);}));
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
            .then(json => new Promise((res, rej)=>{StatsUtil.updateQueueStatsView(); Util.setGeneratingStatus(STATUS.SUCCESS); res();}))
            .catch(error => Session.onPersonalException(error));
    }

    static updateLadderStatsModel(formParams)
    {

        const urlParams = new URLSearchParams("?" + formParams);
        return Promise.all
        ([
            StatsUtil.updateLadderStatsGlobalModel(formParams),
            StatsUtil.updateLadderStatsSeasonModel(urlParams),
            StatsUtil.updateMapStatsSeasonModel(urlParams)
         ])
        .then(jsons=>Model.DATA.get(VIEW.GLOBAL).set(VIEW_DATA.LADDER_STATS, {all: jsons[0], current: jsons[1], map: jsons[2], urlParams: urlParams}));
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
        StatsUtil.updateMapStatsSeasonView();
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

        TableUtil.updateColRowTable
        (
            document.getElementById("games-played-race-table"), percentageResult.raceGamesPlayed,
            (a, b)=>EnumUtil.enumOfName(a, RACE).order - EnumUtil.enumOfName(b, RACE).order,
            (name)=>EnumUtil.enumOfName(name, RACE).name,
            SeasonUtil.seasonIdTranslator
        );
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

    static updateLadderStatsCurrentView()
    {
        const stats = Util.groupBy(Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).current, s=>s.season.region);
        StatsUtil.updateLadderStatsCurrentRaceView(stats);
        StatsUtil.updateLadderStatsCurrentLeagueView(stats);
    }

    static updateLadderStatsCurrentRaceView(stats)
    {
        document.querySelectorAll("#stats-race .table-race-league-region").forEach(t=>t.closest("section").classList.add("d-none"));
        const formattedLeagueStats = {};
        const formattedStatsPercentage = {};
        for(const [region, regionStats] of stats)
        {
            const regionStatsPercentage = {};
            formattedStatsPercentage[region] = regionStatsPercentage;
            for(const leagueStats of regionStats)
            {
                let totalGamesPlayed = 0;
                Object.values(RACE).forEach(race=>totalGamesPlayed += leagueStats.leagueStats[race.name + "GamesPlayed"]);
                const league = EnumUtil.enumOfId(leagueStats.league.type, LEAGUE).name;
                if(!formattedLeagueStats[league]) formattedLeagueStats[league] = {};
                regionStatsPercentage[league] = {};
                for(const race of Object.values(RACE))
                {
                    const raceGamesStr = race.name + "GamesPlayed";
                    if(leagueStats.leagueStats[raceGamesStr]) {
                        formattedLeagueStats[league][race.name] = formattedLeagueStats[league][race.name] == null
                            ? leagueStats.leagueStats[raceGamesStr]
                            : formattedLeagueStats[league][race.name] + leagueStats.leagueStats[raceGamesStr];
                        regionStatsPercentage[league][race.name] =
                            (leagueStats.leagueStats[raceGamesStr] / totalGamesPlayed) * 100;
                    } 
                }
            }
        }
        for(const [league, lStats] of Object.entries(formattedLeagueStats)) {
            const totalGamesPlayed = Object.values(lStats).reduce((a, b)=>a+b);
            for(const [race, games] of Object.entries(lStats)) lStats[race] = (games / totalGamesPlayed) * 100;
        }

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
            const teamsTotal = Object.values(regionStats).reduce((a, b)=>a+b);
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
            .then(json => new Promise((res, rej)=>{StatsUtil.updateLadderStatsView(); Util.setGeneratingStatus(STATUS.SUCCESS); res();}))
            .catch(error => Session.onPersonalException(error));
    }

    static updateLeagueBoundsModel(formParams)
    {
        const request = ROOT_CONTEXT_PATH + "api/ladder/league/bounds?" + formParams;
        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(Session.verifyJsonResponse)
            .then(json => new Promise((res, rej)=>{Model.DATA.get(VIEW.GLOBAL).set(VIEW_DATA.LEAGUE_BOUNDS, json); res(json);}));
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
                        td.textContent = "Top 200";
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
            .then(json => new Promise((res, rej)=>{StatsUtil.updateLeagueBoundsView(); Util.setGeneratingStatus(STATUS.SUCCESS); res();}))
            .catch(error => Session.onPersonalException(error));
    }

    static updateBundleModel()
    {
        return Session.beforeRequest()
            .then(n=>fetch(ROOT_CONTEXT_PATH + "api/ladder/stats/bundle"))
            .then(Session.verifyJsonResponse)
            .then(json => new Promise((res, rej)=>{Model.DATA.get(VIEW.GLOBAL).set(VIEW_DATA.BUNDLE, json); res(json);}));
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

    static updateMapStatsSeasonModel(urlParams, map)
    {
        const regions = [];
        for(const region of Object.values(REGION)) if(urlParams.has(region.name)) regions.push(region.name.toUpperCase());
        const leagues = [];
        for(const league of Object.values(LEAGUE)) if(urlParams.has(league.shortName)) leagues.push(league.name.toUpperCase());
        map = map ? "/" + map : "";

        const request = `${ROOT_CONTEXT_PATH}api/ladder/stats/map/${urlParams.get('season')}/${urlParams.get('queue')}/${urlParams.get('team-type')}/${regions.join(',')}/${leagues.join(',')}${map}`;
        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(resp => {if (!resp.ok) throw new Error(resp.status + " " + resp.statusText); return resp.json();})
            .then(json => json);
    }

    static updateMapStatsSeasonView()
    {
        document.querySelectorAll("#stats-match-up .table-race-league-region").forEach(t=>t.closest("section").classList.add("d-none"));
        const stats = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).map;
        StatsUtil.updateMatchUpMaps(stats);
        const statsRegion = Util.groupBy(stats.stats, s=>stats.seasons[stats.leagues[s.leagueId].seasonId].region);
        const statsLeague = {};
        for(const [region, rStats] of statsRegion) statsLeague[region] = Util.groupBy(rStats, s=>stats.leagues[s.leagueId].type);
        const formattedStats = {};
        for(const [region, leagueMap] of Object.entries(statsLeague)
            .sort((a, b)=>EnumUtil.enumOfName(a[0], REGION).order - EnumUtil.enumOfName(b[0], REGION).order))
        {
            formattedStats[region] = {};
            for(const [league, lStats] of Array.from(leagueMap.entries())
                .sort((a, b)=>EnumUtil.enumOfId(a[0], LEAGUE).order - EnumUtil.enumOfId(b[0], LEAGUE).order))
            {
                const formattedMatchUpStats = formattedStats[region][EnumUtil.enumOfId(league, LEAGUE).name] = {};
                for(const cStats of lStats)
                {
                    const matchUp = cStats.race.substring(0, 1) + "v" + cStats.versusRace.substring(0, 1);
                    formattedMatchUpStats[matchUp] = cStats;
                }

            }
        }

        const sumCalculator = (a, b)=>
        {
            a.wins += b.wins;
            a.gamesTotal += b.gamesTotal;
            a.gamesWithDuration += b.gamesWithDuration;
            a.duration += b.duration;
        }
        const activeCalculator = StatsUtil.getMatchUpCalculator();
        const formattedGlobal = {};
        for(const [region, leagueArray] of Object.entries(formattedStats))
            StatsUtil.calculateLeagueMapStats(leagueArray, Util.cloneObject, sumCalculator, formattedGlobal);
        const formattedGlobalCalculated = {};
        StatsUtil.calculateLeagueMapStats(formattedGlobal, activeCalculator, null, formattedGlobalCalculated);
        const formattedMatchUp = {all: {}};
        StatsUtil.calculateLeagueMapStats(formattedGlobal, Util.cloneObject, sumCalculator, formattedMatchUp.all, false);
        const formattedMatchUpCalculated = {all: {}};
        Object.entries(formattedMatchUp.all).forEach((e)=>formattedMatchUpCalculated.all[e[0]] = activeCalculator(e[1]));
        const formattedRegionalCalculated = {};
        for(const [region, leagueArray] of Object.entries(formattedStats))
        {
            formattedRegionalCalculated[region] = {};
            StatsUtil.calculateLeagueMapStats(leagueArray, activeCalculator, null, formattedRegionalCalculated[region]);
        }

        TableUtil.updateColRowTable
        (
            document.getElementById("stats-match-up-total-table"), formattedMatchUpCalculated,
            Util.matchUpComparator,
            null,
            null
        );
        TableUtil.updateColRowTable
        (
            document.getElementById("stats-match-up-global-table"), formattedGlobalCalculated,
            Util.matchUpComparator,
            null,
            (league)=>EnumUtil.enumOfName(league, LEAGUE).name,
            (a, b)=>EnumUtil.enumOfName(a, LEAGUE).order - EnumUtil.enumOfName(b, LEAGUE).order
        );
        for(const [region, rStats] of Object.entries(formattedRegionalCalculated))
        {
            const table = document.getElementById("stats-match-up-" + region.toLowerCase() + "-table");
            table.closest("section").classList.remove("d-none");
            TableUtil.updateColRowTable
            (
                table, rStats,
                Util.matchUpComparator,
                null,
                (league)=>EnumUtil.enumOfName(league, LEAGUE).name
            );
        }
        const seasons = Object.values(Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).map.seasons);
        if(seasons.length > 0)
        {
            const season = seasons[0].battlenetId;
            document.querySelectorAll("#stats-match-up .season-current").forEach(s=>s.textContent = "s" + season)
        }
        const type = localStorage.getItem("stats-match-up-type") || "win-rate";
        const beginAtZero = type != "win-rate";
        ChartUtil.updateBeginAtZero(Array.from(document.querySelectorAll("#stats-match-up .chartable"))
            .map(c=>[c.id, ChartUtil.CHARTS.get(c.id)]), beginAtZero);
    }

    static calculateLeagueMapStats(formattedStats, calculator, sumCalculator, dest, includeLeague = true)
    {
        for(const [league, matchUpArray] of Object.entries(formattedStats))
        {
            if(includeLeague && !dest[league]) dest[league] = {};
            for(const [matchUp, stats] of Object.entries(matchUpArray))
            {
                const destTarget = includeLeague ? dest[league] : dest;
                if(!destTarget[matchUp])
                {
                    destTarget[matchUp] = calculator(stats);
                }
                else
                {
                    sumCalculator(destTarget[matchUp], stats);
                }
            }
        }
        return dest;
    }

    static getMatchUpCalculator()
    {
        switch(localStorage.getItem("stats-match-up-type"))
        {
            case "duration-avg":
                return (s)=>s.duration / s.gamesWithDuration / 60;
                break;
            case "games":
                return (s)=>s.gamesTotal;
                break;
            default :
                return (s)=>(s.wins / s.gamesTotal) * 100;
        };
    }

    static enhanceMatchUpControls()
    {
        const durationCtl = document.querySelector("#stats-match-up-type");
        if(durationCtl) durationCtl.addEventListener("change", e=>window.setTimeout(StatsUtil.updateMapStatsSeasonView, 1));
        const mapCtl = document.querySelector("#stats-match-up-map");
        if(mapCtl) mapCtl.addEventListener("change", StatsUtil.onMapChange);
    }

    static updateMatchUpMaps(stats)
    {
        const options = document.querySelectorAll("#stats-match-up-map option");
        if(options.length > 1) return; //already updated

        const mapSelect = document.querySelector("#stats-match-up-map");
        stats.maps.map(m=>
        {
            const option = document.createElement("option");
            option.value = m.id;
            option.textContent = m.name;
            return option;
        }).forEach(o=>mapSelect.appendChild(o));
    }

    static onMapChange(e)
    {
        const map = e.target.value == "all" ? null : e.target.value;
        const urlParams = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).urlParams;
        Util.setGeneratingStatus(STATUS.BEGIN);
        return StatsUtil.updateMapStatsSeasonModel(urlParams, map)
            .then(json=>new Promise((res, rej)=>{
                Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS).map = json
                StatsUtil.updateMapStatsSeasonView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
                res();
            }))
            .catch(e => Util.setGeneratingStatus(STATUS.ERROR, e.message, e));
    }

    static addMatchUpLegend()
    {
        const legend = document.querySelector("#match-up-legend");
        if(!legend) return;
        Object.values(RACE)
            .map(r=>{
                const name = document.createElement("span");
                name.textContent = r.name;
                const color = document.createElement("span");
                color.style = `background-color: ${SC2Restful.COLORS.get(r.name)}; border-radius: 1em; height: 1em; width: 1em`;
                color.classList.add("d-inline-block", "align-middle", "mr-1");
                const container = document.createElement("span");
                container.classList.add("d-inline-block", "text-nowrap", "mr-3", "mb-3");
                container.appendChild(color);
                container.appendChild(name);
                return container;
            })
            .forEach(l=>legend.appendChild(l));
    }



}
