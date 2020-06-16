// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class TeamUtil
{

    static updateTeamsTable(table, searchResult)
    {
        const fullMode = table.getAttribute("data-ladder-format-show") == "true";
        const includeRank = table.getAttribute("data-ladder-rank-show") == "true";
        const ladderBody = table.getElementsByTagName("tbody")[0];
        ElementUtil.removeChildren(ladderBody);

        for(let i = 0; i < searchResult.result.length; i++)
        {
            const team = searchResult.result[i];
            const row = ladderBody.insertRow();
            if(fullMode)
            {
                const teamFormat = EnumUtil.enumOfId(team.league.queueType, TEAM_FORMAT);
                const teamType = EnumUtil.enumOfId(team.league.teamType, TEAM_TYPE);
                row.insertCell().appendChild(document.createTextNode(teamFormat.name + " " + teamType.name));
            }
            if(searchResult.meta != null) TableUtil.createRowTh(row)
                    .appendChild(document.createTextNode(Util.calculateRank(searchResult, i)));
            row.insertCell().appendChild(ElementUtil.createImage("flag/", team.region.toLowerCase(), ["table-image-long"]));
            const league = EnumUtil.enumOfId(team.league.type, LEAGUE);
            const leagueDiv = document.createElement("div");
            leagueDiv.classList.add("text-nowrap");
            leagueDiv.appendChild(ElementUtil.createImage("league/", league.name, ["table-image", "table-image-square", "mr-1"]));
            leagueDiv.appendChild(ElementUtil.createImage("league/", "tier-" + (team.leagueTierType + 1), ["table-image-additional"]));
            row.insertCell().appendChild(leagueDiv);
            const membersCell = row.insertCell();
            membersCell.classList.add("complex", "cell-main");
            const mRow = document.createElement("span");
            mRow.classList.add("row", "no-gutters");
            for(const teamMember of team.members) mRow.appendChild(TeamUtil.createMemberInfo(team, teamMember));
            membersCell.appendChild(mRow);
            row.insertCell().appendChild(document.createTextNode(team.rating));
            row.insertCell().appendChild(document.createTextNode(team.wins + team.losses));
            row.insertCell().appendChild(document.createTextNode(Math.round( team.wins / (team.wins + team.losses) * 100) ));
        }
    }

    static createMemberInfo(team, member)
    {
        const nameElem = document.createElement("span");
        nameElem.classList.add("player-name");
        nameElem.textContent = member.character.name.substring(0, member.character.name.indexOf("#"));

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

        const playerLink = document.createElement("a");
        playerLink.classList.add("player-link", "w-100", "h-100", "d-inline-block");
        if(Session.currentFollowing != null && Object.values(Session.currentFollowing).filter(val=>val.followingAccountId == member.account.id).length > 0)
            playerLink.classList.add("text-success");
        playerLink.setAttribute("href", `${ROOT_CONTEXT_PATH}?type=character&id=${member.character.id}&m=1&t=player-stats-summary`);
        playerLink.setAttribute("data-character-id", member.character.id);
        playerLink.addEventListener("click", CharacterUtil.showCharacterInfo);
        playerLink.appendChild(racesElem);
        playerLink.appendChild(nameElem);
        const result = document.createElement("span");
        result.classList.add("team-member-info", "col-lg-" + (team.members.length > 1 ? "6" : "12"), "col-md-12");
        result.appendChild(playerLink);
        return result;
    }

}