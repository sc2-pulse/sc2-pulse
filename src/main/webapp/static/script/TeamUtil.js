// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class TeamUtil
{

    static updateTeamsTable(table, searchResult, clear = true)
    {
        const fullMode = table.getAttribute("data-ladder-format-show") == "true";
        const ladderBody = table.getElementsByTagName("tbody")[0];
        if(clear) ElementUtil.removeChildren(ladderBody);

        for(let i = 0; i < searchResult.result.length; i++)
        {
            const team = searchResult.result[i];
            const row = ladderBody.insertRow();
            row.setAttribute("data-team-id", team.id);
            if(fullMode) row.insertCell().appendChild(TeamUtil.createTeamFormatInfo(team));
            TeamUtil.appendRankInfo(TableUtil.createRowTh(row), searchResult, team, i);
            row.insertCell().textContent = team.rating;
            row.insertCell().appendChild(TeamUtil.createLeagueDiv(team));
            row.insertCell().appendChild(ElementUtil.createImage("flag/", team.region.toLowerCase(), "table-image-long"));
            row.appendChild(TeamUtil.createMembersCell(team));
            TeamUtil.appendGamesInfo(row.insertCell(), team);
            row.insertCell().textContent = Math.round( team.wins / (team.wins + team.losses) * 100);
            row.appendChild(TeamUtil.createMiscCell(team));
        }

        $(table).popover
        ({
            html: true,
            boundary: "body",
            placement: "auto",
            trigger: "hover",
            selector: '[data-toggle="popover"]',
            content: function(){return TeamUtil.createDynamicPopoverContent($(this)[0]).outerHTML;}
        });
    }

    static createDynamicPopoverContent(parent)
    {
        let content;
        switch(parent.getAttribute("data-ctype"))
        {
            case "rank":
                content = TeamUtil.createDynamicRankTable(parent);
                break;
            case "games":
                content = TeamUtil.createDynamicGamesTable(parent);
                break;
            default:
                throw new Error("invalid popover data type");
        }
        return content;
    }

    static createTeamFormatInfo(team)
    {
        const teamFormat = EnumUtil.enumOfId(team.league.queueType, TEAM_FORMAT);
        const teamType = EnumUtil.enumOfId(team.league.teamType, TEAM_TYPE);
        return document.createTextNode(teamFormat.name + " " + teamType.secondaryName);
    }

    static getRank(team, rankType, teamCountCalculator)
    {
        const statsBundle = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.BUNDLE);
        const stats = statsBundle[team.league.queueType][team.league.teamType][team.season];

        const rank = (!Util.isUndefinedRank(team[rankType]) ? Util.NUMBER_FORMAT.format(team[rankType]) : "-");
        const teamCount = teamCountCalculator(stats);
        const topPercentage = (!Util.isUndefinedRank(team[rankType])
            ? Util.DECIMAL_FORMAT.format( (team[rankType] / teamCount) * 100)
            : "");
        return {rank: rank, teamCount: teamCount, topPercentage: topPercentage}
    }

    static getRankString(rankInfo, header, includeTeamCount = true)
    {
        return header + ": " + rankInfo.rank + (includeTeamCount ? "/" + rankInfo.teamCount : "") + "(" + rankInfo.topPercentage + "%)";
    }

    static appendRankInfo(parent, searchResult, team, teamIx)
    {
        const statsBundle = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.BUNDLE);
        const stats = statsBundle[team.league.queueType][team.league.teamType][team.season];

        const rank = searchResult.meta != null
            ? Util.NUMBER_FORMAT.format(Util.calculateRank(searchResult, teamIx))
            : (!Util.isUndefinedRank(team.globalRank) ? Util.NUMBER_FORMAT.format(team.globalRank) : "-");
        const topPercentage = searchResult.meta != null
            ? Util.DECIMAL_FORMAT.format((Util.calculateRank(searchResult, teamIx) / searchResult.meta.totalCount) * 100)
            : (!Util.isUndefinedRank(team.globalRank)
                ? Util.DECIMAL_FORMAT.format( (team.globalRank / Object.values(stats.regionTeamCount).reduce((a, b)=>a+b)) * 100)
                : "");

        parent.setAttribute("data-toggle", "popover");
        parent.setAttribute("data-ctype", "rank");

        const rankInfo = `<div class="text-nowrap">
            <span>${rank}</span>
            <span class="text-secondary font-weight-lighter">${!isNaN(topPercentage) ? "(" + topPercentage + "%)" : ""}</span>
            </div>`;
        parent.innerHTML = rankInfo;
    }

    static getTeamFromElement(parent)
    {
        const teamId = parent.closest("tr").getAttribute("data-team-id");
        const viewData = Model.DATA.get(ViewUtil.getView(parent));
        const searchResult = viewData.get(VIEW_DATA.TEAMS) || viewData.get(VIEW_DATA.SEARCH);
        return searchResult.result.filter(t=>t.id==teamId)[0];
    }

    static createDynamicRankTable(parent)
    {
        const viewData = Model.DATA.get(ViewUtil.getView(parent));
        const searchResult = viewData.get(VIEW_DATA.TEAMS) || viewData.get(VIEW_DATA.SEARCH);
        const team = TeamUtil.getTeamFromElement(parent);
        const statsBundle = Model.DATA.get(VIEW.GLOBAL).get(VIEW_DATA.BUNDLE);
        const stats = statsBundle[team.league.queueType][team.league.teamType][team.season];

        const ranksTable = TableUtil.createTable(["Scope", "Rank", "Total", "Top%"], false);
        const tbody = ranksTable.querySelector("tbody");
        if(searchResult.meta != null)
        {
            const rank = Util.calculateRank(searchResult, searchResult.result.indexOf(team));
            tbody.innerHTML =
            `<tr>
                <th scope="row">filter</th>
                <td>${Util.NUMBER_FORMAT.format(rank)}</td>
                <td>${Util.NUMBER_FORMAT.format(searchResult.meta.totalCount)}</td>
                <td>${Util.DECIMAL_FORMAT.format((rank / searchResult.meta.totalCount) * 100)}</td>
            </tr>`;
        }
        tbody.appendChild(TeamUtil.createRankRow(team, "global", Object.values(stats.regionTeamCount).reduce((a, b)=>a+b)));
        tbody.appendChild(TeamUtil.createRankRow(team, "region", stats.regionTeamCount[team.region]));
        tbody.appendChild(TeamUtil.createRankRow(team, "league", stats.leagueTeamCount[team.league.type]));

        return ranksTable;
    }

    static createRankRow(team, scope, teamCount)
    {
        const row = document.createElement("tr");
        TableUtil.createRowTh(row).textContent = scope;

        const rank = team[scope + "Rank"];
        if(!Util.isUndefinedRank(rank))
        {
            row.insertCell().textContent = Util.NUMBER_FORMAT.format(rank);
            row.insertCell().textContent = Util.NUMBER_FORMAT.format(teamCount);
            row.insertCell().textContent = Util.DECIMAL_FORMAT.format((rank / teamCount) * 100);
        }
        else
        {
            row.insertCell().textContent = "-";
            row.insertCell().textContent = Util.NUMBER_FORMAT.format(teamCount);
            row.insertCell().textContent = "-";
        }

        return row;
    }

    static appendGamesInfo(parent, team)
    {
        parent.setAttribute("data-toggle", "popover");
        parent.setAttribute("data-ctype", "games");
        parent.appendChild(document.createTextNode(team.wins + team.losses + team.ties));
    }

    static createDynamicGamesTable(parent)
    {
        const team = TeamUtil.getTeamFromElement(parent);

        const gamesTable = TableUtil.createTable(["Type", "Count"], false);
        const tbody = gamesTable.querySelector("tbody");
        tbody.appendChild(TableUtil.createSimpleRow(team, "wins"));
        tbody.appendChild(TableUtil.createSimpleRow(team, "losses"));
        tbody.appendChild(TableUtil.createSimpleRow(team, "ties"));

        return gamesTable;
    }

    static createMemberInfo(team, member, appendRaces = true)
    {
        const result = document.createElement("span");
        result.classList.add("team-member-info", "col-lg-" + (team.members.length > 1 ? "6" : "12"), "col-md-12");
        result.appendChild(TeamUtil.createPlayerLink(team, member, appendRaces));
        return result;
    }

    static createPlayerLink(team, member, appendRaces)
    {
        const playerLink = document.createElement("a");
        playerLink.classList.add("player-link", "w-100", "h-100", "d-inline-block");
        if(Session.currentFollowing != null && Object.values(Session.currentFollowing).filter(val=>val.followingAccountId == member.account.id).length > 0)
            playerLink.classList.add("text-success");
        playerLink.setAttribute("href", `${ROOT_CONTEXT_PATH}?type=character&id=${member.character.id}&m=1`);
        playerLink.setAttribute("data-character-id", member.character.id);
        playerLink.addEventListener("click", CharacterUtil.showCharacterInfo);
        const container = document.createElement("span");
        container.classList.add("player-link-container");
        if(member.proNickname != null) container.classList.add("player-pro");
        if(appendRaces) container.appendChild(TeamUtil.createRacesElem(member));
        container.appendChild(TeamUtil.createNameElem(member));
        playerLink.appendChild(container);
        return playerLink
    }

    static createNameElem(member)
    {
        const unmasked = Util.unmaskName(member);
        const nameContainer = document.createElement("span");
        nameContainer.classList.add("player-name-container");

        if(unmasked.unmaskedTeam != null)
        {
            const teamElem = document.createElement("span");
            teamElem.classList.add("player-team");
            teamElem.textContent = unmasked.unmaskedTeam;
            nameContainer.appendChild(teamElem);
        }

        const nameElem = document.createElement("span");
        nameElem.classList.add("player-name");
        nameElem.textContent = unmasked.unmaskedName;
        nameContainer.appendChild(nameElem);

        if(unmasked.maskedName.toLowerCase() != unmasked.unmaskedName.toLowerCase())
        {
            const maskedNameElem = document.createElement("span");
            maskedNameElem.classList.add("player-name-masked");
            maskedNameElem.textContent = unmasked.maskedName;
            nameContainer.appendChild(maskedNameElem);
        }
        return nameContainer;
    }

    static createRacesElem(member)
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
                const percentageEntry = document.createElement("span");
                percentageEntry.classList.add("race-percentage-entry", "c-divider-slash", "text-secondary");
                percentageEntry.appendChild(ElementUtil.createImage("race/", race.name, "table-image table-image-square"));
                if(val < 100)
                {
                    const racePercent = document.createElement("span");
                    racePercent.classList.add("race-percentage", "race-percentage-" + race.name, "text-secondary");
                    racePercent.textContent = val;
                    percentageEntry.appendChild(racePercent);
                }
                racesElem.appendChild(percentageEntry);
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
        leagueDiv.appendChild(ElementUtil.createImage("league/", league.name, "table-image table-image-square mr-1"));
            leagueDiv.appendChild(ElementUtil.createImage("league/", "tier-" + (team.tierType + 1),
                "table-image-additional" + (TeamUtil.isAlternativelyUpdatedTeam(team) ? " invisible" : "")));
        return leagueDiv;
    }

    static createMiscCell(team)
    {
        const bufCell = document.createElement("td");
        bufCell.classList.add("text-nowrap")
        const remove = TeamUtil.teamBuffer.has(team.id);
        const toggle = ElementUtil.createTagButton("div", "table-image table-image-square background-cover team-buffer-toggle d-inline-block " + (remove ? "remove" : "add"));
        toggle.addEventListener("click", TeamUtil.toggleTeamBuffer);
        const historyLink = ElementUtil.createTagButton("a",  "table-image table-image-square background-cover mr-3 d-inline-block chart-line-img");
        historyLink.setAttribute("href", TeamUtil.getTeamMmrHistoryHref([team]));
        historyLink.setAttribute("target", "_blank");
        historyLink.setAttribute("rel", "noopener");
        bufCell.appendChild(historyLink);
        bufCell.appendChild(toggle);
        return bufCell;
    }

    static isAlternativelyUpdatedTeam(team)
    {
        return ALTERNATIVE_UPDATE_REGIONS.length > 0
            && ALTERNATIVE_UPDATE_REGIONS.includes(team.region)
            && Session.currentSeasonsMap.get(team.season + 1) == null; //is last season
    }

    static getFavoriteRace(member)
    {
        let highestCount = member.terranGamesPlayed || 0;
        let result = RACE.TERRAN;
        for(const race of Object.values(RACE))
        {
            const raceGames = member[race.name + "GamesPlayed"] || 0;
            if(raceGames > highestCount) {result = race; highestCount = raceGames;}
        }
        return result;
    }

    static addTeamToBuffer(team)
    {
        TeamUtil.teamBuffer.set(team.id, team);
        TeamUtil.updateTeamBufferModel();
        TeamUtil.updateTeamBufferView();
    }

    static removeTeamFromBuffer(team)
    {
        TeamUtil.teamBuffer.delete(team.id);
        TeamUtil.updateTeamBufferModel();
        TeamUtil.updateTeamBufferView();
    }

    static clearTeamBuffer()
    {
        TeamUtil.teamBuffer.clear();
        document.querySelectorAll('.team-buffer-toggle.remove').forEach(e=>e.classList.replace("remove", "add"));
        TeamUtil.updateTeamBufferModel();
        TeamUtil.updateTeamBufferView();
    }

    static updateTeamBufferModel()
    {
        Model.DATA.get(VIEW.TEAM_BUFFER).set(VIEW_DATA.SEARCH, {result:Array.from(TeamUtil.teamBuffer.values())});
    }

    static updateTeamBufferView()
    {
        const bufferElem = document.querySelector("#team-buffer");
        document.querySelector("#team-buffer-count").textContent = TeamUtil.teamBuffer.size;
        if(TeamUtil.teamBuffer.size == 0) {
            bufferElem.classList.add("d-none");
        } else {
            bufferElem.classList.remove("d-none");
        }
        TeamUtil.updateTeamMmrLink();
        TeamUtil.updateTeamsTable(document.querySelector("#team-buffer-teams"), {result:Array.from(TeamUtil.teamBuffer.values())});
    }

    static getTeamLegacyUid(team)
    {
        return team.queueType + "-" + EnumUtil.enumOfName(team.region, REGION).code + "-" + team.legacyId;
    }

    static getTeamMmrHistoryParams(teams)
    {
        const searchParams = new URLSearchParams();
        searchParams.append("type", "team-mmr");
        for(const team of teams) searchParams.append("legacyUid", TeamUtil.getTeamLegacyUid(team));

        return searchParams;
    }

    static getTeamMmrHistoryHref(teams)
    {
        return `${ROOT_CONTEXT_PATH}team/history?${TeamUtil.getTeamMmrHistoryParams(teams).toString()}`;
    }

    static updateTeamMmrLink()
    {
        document.querySelector("#team-buffer-mmr").setAttribute("href", TeamUtil.getTeamMmrHistoryHref(TeamUtil.teamBuffer.values()));
    }

    static toggleTeamBuffer(evt)
    {
        const team = TeamUtil.getTeamFromElement(evt.target);
        const remove = evt.target.classList.contains("remove");
        if(remove) {
            TeamUtil.removeTeamFromBuffer(team);
            document.querySelectorAll('[data-team-id="' + team.id + '"] .team-buffer-toggle').forEach(e=>{
                e.classList.remove("remove");
                e.classList.add("add");
            });

        } else {
            TeamUtil.addTeamToBuffer(team);
            document.querySelectorAll('[data-team-id="' + team.id + '"] .team-buffer-toggle').forEach(e=>{
                e.classList.add("remove");
                e.classList.remove("add");
            });
        }
    }

    static enhanceTeamBuffer()
    {
        document.querySelector("#team-buffer-clear").addEventListener("click", TeamUtil.clearTeamBuffer);
    }

    static updateTeamMmr(searchParams = null)
    {
        if(searchParams == null) searchParams = TeamUtil.getTeamMmrHistoryParams(TeamUtil.teamBuffer.values());
        const stringParams = searchParams.toString();
        const params = {params: stringParams};
        Util.setGeneratingStatus(STATUS.BEGIN);

        return TeamUtil.updateTeamMmrModel(searchParams)
            .then(e=>new Promise((res, rej)=>{
                TeamUtil.updateTeamMmrView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
                if(!Session.isHistorical) HistoryUtil.pushState(params, document.title, "?" + stringParams + "#team-mmr");
                Session.currentSearchParams = stringParams;
                if(!Session.isHistorical) HistoryUtil.updateActiveTabs();
                res();
            }))
            .catch(error => Util.setGeneratingStatus(STATUS.ERROR, error.message, error));
    }

    static updateTeamMmrModel(searchParams)
    {
        const reqParams = new URLSearchParams();
        for(const id of searchParams.getAll("legacyUid")) reqParams.append("legacyUid", id);
        const request = `${ROOT_CONTEXT_PATH}api/team/history/common?${reqParams.toString()}`;
        const mmrPromise = fetch(request)
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{
                const teams = [];
                for(const history of Object.values(json)) teams.push(history.teams[history.teams.length - 1]);
                teams.sort((a, b)=>b.rating - a.rating);
                Model.DATA.get(VIEW.TEAM_MMR).set(VIEW_DATA.SEARCH, {result: teams});
                Model.DATA.get(VIEW.TEAM_MMR).set(VIEW_DATA.VAR, json);
                res(json);
            }));
        return Promise.all([mmrPromise, StatsUtil.updateBundleModel()]);
    }

    static updateTeamMmrView()
    {
        const searchResult = Model.DATA.get(VIEW.TEAM_MMR).get(VIEW_DATA.VAR);
        const seasonLastOnly = document.getElementById("team-mmr-season-last").checked;
        const depth = document.getElementById("team-mmr-depth").value;
        const depthDate = depth > 0 ? new Date(Date.now() - (depth * 24 * 60 * 60 * 1000)) : null;
        const excludeStart = document.getElementById("team-mmr-exclude-start").value || 0;
        const excludeEnd = document.getElementById("team-mmr-exclude-end").value || 0;
        TeamUtil.updateTeamsTable(document.querySelector("#team-mmr-teams-table"), Model.DATA.get(VIEW.TEAM_MMR).get(VIEW_DATA.SEARCH));
        let transformedData = [];
        let curEntry = 0;
        for(const [legacyUid, history] of Object.entries(searchResult)) {
            const refTeam = history.teams[history.teams.length - 1];
            const group = TeamUtil.generateTeamName(refTeam);
            if(!seasonLastOnly) {
                for(const state of history.states) {
                    state.group = {name: group, order: curEntry};
                    state.teamState.dateTime = new Date(state.teamState.dateTime);
                    transformedData.push(state);
                }
            }
            for(const team of history.teams) {
                const state = CharacterUtil.createTeamSnapshot(team, Session.currentSeasonsMap.get(team.season)[0].end);
                state.group = {name: group, order: curEntry};
                transformedData.push(state);
            }
            curEntry++;
        }
        transformedData = TeamUtil.filterTeamMmrHistory(transformedData, depthDate, excludeStart, excludeEnd);
        transformedData.sort((a,b)=>a.teamState.dateTime.getTime() - b.teamState.dateTime.getTime());
        const mmrHistoryGrouped = Util.groupBy(transformedData, h=>h.teamState.dateTime.getTime());
        const data = [];
        const rawData = [];
        for(const [dateTime, histories] of mmrHistoryGrouped.entries())
        {
            rawData.push(histories);
            data[dateTime] = {};
            for(const history of histories) data[dateTime][history.group.name] = history.teamState.rating;
        }
        ChartUtil.CHART_RAW_DATA.set("team-mmr-table", {rawData: rawData, additionalDataGetter: TeamUtil.getAdditionalMmrHistoryData});
        TableUtil.updateVirtualColRowTable
        (
            document.getElementById("team-mmr-table"),
            data,
            (tableData=>ChartUtil.CHART_RAW_DATA.get("team-mmr-table").data = tableData),
            null,
            null,
            (dateTime)=>new Date(parseInt(dateTime))
        );
        TeamUtil.updateTeamMmrFilters(transformedData, depthDate, excludeStart, excludeEnd);
    }

    static filterTeamMmrHistory(mmrHistory, depthDate, excludeStart, excludeEnd)
    {
        if(depthDate != null) mmrHistory = mmrHistory.filter(h=>h.teamState.dateTime.getTime() > depthDate.getTime());
        if(excludeEnd > 0)
            mmrHistory = mmrHistory.filter(h=>h.teamState.rating < excludeStart || h.teamState.rating >= excludeEnd);
        return mmrHistory;
    }

    static updateTeamMmrFilters(mmrHistory, depthDate, excludeStart, excludeEnd)
    {
        document.getElementById("team-mmr-filters").textContent =
        "(" + mmrHistory.length  + " entries"
        + (depthDate != null ? ", starting from " + Util.DATE_FORMAT.format(depthDate) : "")
        + (excludeEnd > 0 ? ", excluding range " + excludeStart + "-" + excludeEnd : "")
        + ")";
    }

    static getAdditionalMmrHistoryData(data, dataset, ix1, ix2)
    {
        const races = [];
        dataset.datasets.forEach(d=>races.push(d.label));
        const race = races[ix2];
        const curData = Object.values(data)[ix1].find(d=>d.group.name == race);
        const lines1 = [];
        const lines2 = [];
        const lines3 = [];
        lines1.push("mmr: " + curData.teamState.rating);
        lines1.push("games: " + curData.teamState.games);
        lines2.push("league: " +  EnumUtil.enumOfId(curData.league.type, LEAGUE).name)
            + (ALTERNATIVE_UPDATE_REGIONS.length > 0 ? "" : " " + (curData.tier + 1));
        lines2.push("season: " + curData.season);
        if(curData.team) {
            const globalRank = TeamUtil.getRank(curData.team, "globalRank", stats=>Object.values(stats.regionTeamCount).reduce((a, b)=>a+b));
            const regionRank = TeamUtil.getRank(curData.team, "regionRank", stats=>stats.regionTeamCount[curData.team.region]);
            lines3.push(TeamUtil.getRankString(globalRank, "global", false));
            lines3.push(TeamUtil.getRankString(regionRank, "region", false));
        }
        return lines3.length > 0 ? [lines1.join(", "), lines2.join(", "), lines3.join(", ")] : [lines1.join(", "), lines2.join(", ")];
    }

    static generateTeamMmrTitle(params, hash)
    {
        const teams = Model.DATA.get(VIEW.TEAM_MMR).get(VIEW_DATA.SEARCH).result;
        if(!teams  || teams.length == 0) return "Team MMR history";

        const groups = [];
        for(const team of teams) groups.push(TeamUtil.generateTeamName(team, false));

        return `${groups.join(" | ")} team MMR history`;
    }

    static generateTeamMmrDescription(params, hash)
    {
        const entries = Model.DATA.get(VIEW.TEAM_MMR).get(VIEW_DATA.VAR);
        if(!entries  || entries.length == 0) return "Complete team MMR history";

        let count = 0;
        for(const history of Object.values(entries)) count += history.teams.length + history.states.length;

        return `Complete team MMR history, ${Object.entries(entries).length} teams, ${count} entries.`;
    }

    static enhanceMmrForm()
    {
        document.getElementById("team-mmr-depth").addEventListener("input",  TeamUtil.onMmrInput);
        document.getElementById("team-mmr-exclude-start").addEventListener("input", TeamUtil.onMmrInput);
        document.getElementById("team-mmr-exclude-end").addEventListener("input", TeamUtil.onMmrInput);
        document.getElementById("team-mmr-season-last").addEventListener("change", evt=>TeamUtil.updateTeamMmrView());
    }

    static onMmrInput(evt)
    {
        const prev = ElementUtil.INPUT_TIMEOUTS.get(evt.target.id);
        if(prev != null) window.clearTimeout(prev);
        ElementUtil.INPUT_TIMEOUTS.set(evt.target.id, window.setTimeout(TeamUtil.updateTeamMmrView, ElementUtil.INPUT_TIMEOUT));
    }

    static generateTeamName(team, includeId = true)
    {
        const group = [];
        group.push(EnumUtil.enumOfId(team.queueType, TEAM_FORMAT).name)
        for(const member of team.members) {
            const unmasked = Util.unmaskName(member);
            group.push(unmasked.unmaskedName
                + "(" + (unmasked.maskedName.toLowerCase() != unmasked.unmaskedName.toLowerCase() ? (unmasked.maskedName + ", ") : "")
                + TeamUtil.getFavoriteRace(member).name.substring(0, 1) + ")");
        }
        if(includeId) group.push(team.id);
        return group.join(", ");
    }

}

TeamUtil.teamBuffer = new Map();
