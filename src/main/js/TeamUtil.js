// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class TeamUtil
{

    static updateTeamsTable(table, searchResult, statsBundle)
    {
        const fullMode = table.getAttribute("data-ladder-format-show") == "true";
        const ladderBody = table.getElementsByTagName("tbody")[0];
        ElementUtil.removeChildren(ladderBody);

        for(let i = 0; i < searchResult.result.length; i++)
        {
            const team = searchResult.result[i];
            const row = ladderBody.insertRow();
            if(fullMode) row.insertCell().appendChild(TeamUtil.createTeamFormatInfo(team));
            TeamUtil.appendRankInfo(TableUtil.createRowTh(row), searchResult, statsBundle, team, i);
            row.insertCell().appendChild(document.createTextNode(team.rating));
            row.insertCell().appendChild(TeamUtil.createLeagueDiv(team));
            row.insertCell().appendChild(ElementUtil.createImage("flag/", team.region.toLowerCase(), ["table-image-long"]));
            row.appendChild(TeamUtil.createMembersCell(team));
            row.insertCell().appendChild(document.createTextNode(team.wins + team.losses));
            row.insertCell().appendChild(document.createTextNode(Math.round( team.wins / (team.wins + team.losses) * 100) ));
        }
        BootstrapUtil.enhancePopovers("#" + table.id);
    }

    static createTeamFormatInfo(team)
    {
        const teamFormat = EnumUtil.enumOfId(team.league.queueType, TEAM_FORMAT);
        const teamType = EnumUtil.enumOfId(team.league.teamType, TEAM_TYPE);
        return document.createTextNode(teamFormat.name + " " + teamType.name);
    }

    static appendRankInfo(parent, searchResult, statsBundle, team, teamIx)
    {
        const stats = statsBundle[team.league.queueType][team.league.teamType][team.season];
        const rank = searchResult.meta != null
            ? Util.NUMBER_FORMAT.format(Util.calculateRank(searchResult, teamIx))
            : Util.NUMBER_FORMAT.format(team.globalRank);
        const ranksTable = TableUtil.createTable(["Scope", "Rank", "Total", "Top%"], false);
        const tbody = ranksTable.querySelector("tbody");
        tbody.appendChild(TeamUtil.createRankRow(team, "global", Object.values(stats.regionTeamCount).reduce((a, b)=>a+b)));
        tbody.appendChild(TeamUtil.createRankRow(team, "region", stats.regionTeamCount[team.region]));
        tbody.appendChild(TeamUtil.createRankRow(team, "league", stats.leagueTeamCount[team.league.type]));

        parent.setAttribute("data-toggle", "popover");
        parent.setAttribute("data-trigger", "hover");
        parent.setAttribute("data-placement", "right");
        parent.setAttribute("data-html", "true");
        parent.setAttribute("data-content", ranksTable.outerHTML);
        parent.appendChild(document.createTextNode(rank));
    }

    static createRankRow(team, scope, teamCount)
    {
        const row = document.createElement("tr");
        TableUtil.createRowTh(row).appendChild(document.createTextNode(scope));
        row.insertCell().appendChild(document.createTextNode(Util.NUMBER_FORMAT.format(team[scope + "Rank"])));
        row.insertCell().appendChild(document.createTextNode(Util.NUMBER_FORMAT.format(teamCount)));
        row.insertCell().appendChild(document.createTextNode(Util.DECIMAL_FORMAT.format((team[scope + "Rank"] / teamCount) * 100)));
        return row;
    }

    static createMemberInfo(team, member)
    {
        const result = document.createElement("span");
        result.classList.add("team-member-info", "col-lg-" + (team.members.length > 1 ? "6" : "12"), "col-md-12");
        result.appendChild(TeamUtil.createPlayerLink(team, member));
        return result;
    }

    static createPlayerLink(team, member)
    {
        const playerLink = document.createElement("a");
        playerLink.classList.add("player-link", "w-100", "h-100", "d-inline-block");
        if(Session.currentFollowing != null && Object.values(Session.currentFollowing).filter(val=>val.followingAccountId == member.account.id).length > 0)
            playerLink.classList.add("text-success");
        playerLink.setAttribute("href", `${ROOT_CONTEXT_PATH}?type=character&id=${member.character.id}&m=1&t=player-stats-summary`);
        playerLink.setAttribute("data-character-id", member.character.id);
        playerLink.addEventListener("click", CharacterUtil.showCharacterInfo);
        playerLink.appendChild(TeamUtil.createRacesElem(team, member));
        playerLink.appendChild(TeamUtil.createNameElem(team, member));
        return playerLink
    }

    static createNameElem(team, member)
    {
        const nameElem = document.createElement("span");
        nameElem.classList.add("player-name");
        nameElem.textContent = Util.unmaskBarcode(member.character, member.account);
        return nameElem
    }

    static createRacesElem(team, member)
    {
        const games = new Map();
        games.set(RACE.TERRAN, typeof member.terranGamesPlayed === "undefined" ? 0 : member.terranGamesPlayed);
        games.set(RACE.PROTOSS, typeof member.protossGamesPlayed === "undefined" ? 0 : member.protossGamesPlayed);
        games.set(RACE.ZERG, typeof member.zergGamesPlayed === "undefined" ? 0 : member.zergGamesPlayed);
        games.set(RACE.RANDOM, typeof member.randomGamesPlayed === "undefined" ? 0 : member.randomGamesPlayed);
        let gamesTotal = 0;
        for(const val of games.values()) gamesTotal += val;
        const percentage = new Map();
        for(const [key, val] of games.entries())
            if(val != 0) percentage.set(key, Math.round((val / gamesTotal) * 100));
        const percentageSorted = new Map([...percentage.entries()].sort((a, b)=>b[1] - a[1]));

        const racesElem = document.createElement("span");
        racesElem.classList.add("race-percentage-container", "mr-1", "text-nowrap", "d-inline-block");
        if(percentageSorted.size > 0)
        {
            for(const [race, val] of percentageSorted.entries())
            {
                if(val == 0) continue;
                racesElem.appendChild(ElementUtil.createImage("race/", race.name, ["table-image", "table-image-square"]));
                if(val < 100)
                {
                    const racePercent = document.createElement("span");
                    racePercent.classList.add("race-percentage", "race-percentage-" + race.name, "text-secondary");
                    racePercent.textContent = val;
                    racesElem.appendChild(racePercent);
                }
            }
        }
        else
        {
            racesElem.appendChild(ElementUtil.createNoRaceImage());
        }
        return racesElem;
    }

    static createMembersCell(team)
    {
        const membersCell = document.createElement("td");
        membersCell.classList.add("complex", "cell-main");
        const mRow = document.createElement("span");
        mRow.classList.add("row", "no-gutters");
        for(const teamMember of team.members) mRow.appendChild(TeamUtil.createMemberInfo(team, teamMember));
        membersCell.appendChild(mRow);
        return membersCell;
    }

    static createLeagueDiv(team)
    {
        const league = EnumUtil.enumOfId(team.league.type, LEAGUE);
        const leagueDiv = document.createElement("div");
        leagueDiv.classList.add("text-nowrap");
        leagueDiv.appendChild(ElementUtil.createImage("league/", league.name, ["table-image", "table-image-square", "mr-1"]));
        leagueDiv.appendChild(ElementUtil.createImage("league/", "tier-" + (team.tierType + 1), ["table-image-additional"]));
        return leagueDiv;
    }

}