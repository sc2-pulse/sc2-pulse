// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class StatsUtil
{

    static updateQueueStatsModel(formParams)
    {
        const params = new URLSearchParams(formParams);
        const queueType = EnumUtil.enumOfFullName(params.get("queue"), TEAM_FORMAT);
        const teamType = EnumUtil.enumOfFullName(params.get("team-type"), TEAM_TYPE);
        const request = `api/ladder/stats/queue/${queueType.fullName}/${teamType.fullName}`;
        return fetch(request)
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
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
            .catch(e => Util.setGeneratingStatus(STATUS.ERROR, e.message, e));
    }

    static updateLadderStatsModel(formParams)
    {
        const request = "api/ladder/stats?" + formParams;
        return fetch(request)
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{Model.DATA.get(VIEW.GLOBAL).set(VIEW_DATA.LADDER_STATS, json); res(json);}));
    }

    static updateLadderStatsView()
    {
        const searchResult = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.LADDER_STATS);
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

    static updateLadderStats(formParams)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return StatsUtil.updateLadderStatsModel(formParams)
            .then(json => new Promise((res, rej)=>{StatsUtil.updateLadderStatsView(); Util.setGeneratingStatus(STATUS.SUCCESS); res();}))
            .catch(e => Util.setGeneratingStatus(STATUS.ERROR, e.message, e));
    }

    static updateLeagueBoundsModel(formParams)
    {
        const request = "api/ladder/league/bounds?" + formParams;
        return fetch(request)
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
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
        for(const [leagueId, leagueObj] of Object.entries(searchResult[Object.keys(searchResult)[0]]).sort((a, b)=>b[0] - a[0]))
        {
            const league = EnumUtil.enumOfId(leagueId, LEAGUE);
            for(const tierId of Object.keys(leagueObj))
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
                    if
                    (
                        searchResult[region] == null
                        || searchResult[region][leagueId] == null
                        || searchResult[region][leagueId][tierId] == null
                    )
                    {
                        tr.appendChild(document.createElement("td"));
                    }
                    else
                    {
                        const range = searchResult[region][leagueId][tierId];
                        const td = document.createElement("td");
                        td.textContent = league === LEAGUE.GRANDMASTER
                            ? "Top 200"
                            : (range[0] + "-" + range[1]);
                        tr.appendChild(td);
                    }
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
            .catch(e => Util.setGeneratingStatus(STATUS.ERROR, e.message, e));
    }

    static updateBundleModel()
    {
        return fetch(ROOT_CONTEXT_PATH + "api/ladder/stats/bundle")
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
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


}
