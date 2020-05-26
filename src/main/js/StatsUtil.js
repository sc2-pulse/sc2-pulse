// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class StatsUtil
{

    static getLadderStats(formParams)
    {
        Util.setGeneratingStatus("begin");
        const request = "api/ladder/stats?" + formParams;
        return fetch(request)
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{StatsUtil.updateLadderStats(json); Util.setGeneratingStatus("success"); res();}))
            .catch(e => Util.setGeneratingStatus("error", e.message));
    }

    static updateLadderStats(searchResult)
    {
        const globalResult = {gamesPlayed: {}, teamCount: {}, playerCount: {}};
        const percentageResult = {};
        for(const [seasonId, stats] of Object.entries(searchResult))
        {
            globalResult.gamesPlayed[seasonId] = {global: Object.values(stats.regionGamesPlayed).reduce((a, b) => a + b, 0)};
            globalResult.teamCount[seasonId] = {global: Object.values(stats.regionTeamCount).reduce((a, b) => a + b, 0)};
            globalResult.playerCount[seasonId] = {global: Object.values(stats.regionPlayerCount).reduce((a, b) => a + b, 0)};
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
            (document.getElementById("games-played-global-table"), globalResult.gamesPlayed, null, null, SeasonUtil.seasonIdtranslator);
        TableUtil.updateColRowTable
            (document.getElementById("team-count-global-table"), globalResult.teamCount, null, null, SeasonUtil.seasonIdtranslator);
        TableUtil.updateColRowTable
            (document.getElementById("player-count-global-table"), globalResult.playerCount, null, null, SeasonUtil.seasonIdtranslator);

        TableUtil.updateColRowTable
        (
            document.getElementById("games-played-race-table"), percentageResult.raceGamesPlayed,
            null, function(name){return EnumUtil.enumOfName(name, RACE).name;}, SeasonUtil.seasonIdtranslator
        );
        TableUtil.updateColRowTable
            (document.getElementById("games-played-region-table"), percentageResult.regionGamesPlayed, null, null, SeasonUtil.seasonIdtranslator);
        TableUtil.updateColRowTable
        (
            document.getElementById("games-played-league-table"),
            percentageResult.leagueGamesPlayed,
            (a, b)=>a[0].localeCompare(b[0]),
            function(id){return EnumUtil.enumOfId(id, LEAGUE).name;},
            SeasonUtil.seasonIdtranslator
        );

        TableUtil.updateColRowTable
            (document.getElementById("team-count-region-table"), percentageResult.regionTeamCount, null, null, SeasonUtil.seasonIdtranslator);
        TableUtil.updateColRowTable
        (
            document.getElementById("team-count-league-table"),
            percentageResult.leagueTeamCount,
            (a, b)=>a[0].localeCompare(b[0]),
            function(id){return EnumUtil.enumOfId(id, LEAGUE).name;},
            SeasonUtil.seasonIdtranslator
        );

        TableUtil.updateColRowTable
            (document.getElementById("player-count-region-table"), percentageResult.regionPlayerCount, null, null, SeasonUtil.seasonIdtranslator);
        TableUtil.updateColRowTable
        (
            document.getElementById("player-count-league-table"),
            percentageResult.leaguePlayerCount,
            (a, b)=>a[0].localeCompare(b[0]),
            function(id){return EnumUtil.enumOfId(id, LEAGUE).name;},
            SeasonUtil.seasonIdtranslator
        );
    }

    static getLeagueBounds(formParams)
    {
        Util.setGeneratingStatus("begin");
        const request = "api/ladder/league/bounds?" + formParams;
        return fetch(request)
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{StatsUtil.updateLeagueBounds(json); Util.setGeneratingStatus("success"); res();}))
            .catch(e => Util.setGeneratingStatus("error", e.message));
    }

    static updateLeagueBounds(searchResult)
    {
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
            th.appendChild(ElementUtil.createImage("flag/", region.toLowerCase(), ["table-image", "table-image-long"]));
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
                leagueDiv.appendChild(ElementUtil.createImage("league/", league.name, ["table-image", "table-image-square", "mr-1"]));
                leagueDiv.appendChild(ElementUtil.createImage("league/", "tier-" + (1 + + tierId), ["table-image-additional"]));
                th.appendChild(leagueDiv);
                tr.appendChild(th);
                for(const region of Object.keys(searchResult))
                {
                    const range = searchResult[region][leagueId][tierId];
                    const td = document.createElement("td");
                    td.textContent = league === LEAGUE.GRANDMASTER
                        ? "Top 200"
                        : (range[0] + "-" + range[1]);
                    tr.appendChild(td);
                }
                body.appendChild(tr);
            }
        }
    }

}
