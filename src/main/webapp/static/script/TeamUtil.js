// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class TeamUtil
{

    static updateTeamsTable(table, searchResult, clear = true, multiRow = "lg")
    {
        const fullMode = table.getAttribute("data-ladder-format-show") == "true";
        const ladderBody = table.getElementsByTagName("tbody")[0];
        if(clear) ElementUtil.removeChildren(ladderBody);

        let nonCheaterIx = 0;
        for(let i = 0; i < searchResult.result.length; i++)
        {
            const team = searchResult.result[i];
            const row = ladderBody.insertRow();
            if(team.id == -1) {
                row.setAttribute("data-team-alternative-data", team.alternativeData);
                nonCheaterIx++;
                continue;
            }

            row.setAttribute("data-team-id", team.id);
            if(team.dateTime) row.setAttribute("data-team-date-time", team.dateTime);
            const isCheater = TeamUtil.isCheaterTeam(team);
            if(fullMode) row.insertCell().appendChild(TeamUtil.createTeamFormatInfo(team));
            TeamUtil.appendRankInfo(TableUtil.createRowTh(row), searchResult, team, isCheater ? -1 : nonCheaterIx);
            TableUtil.insertCell(row, "rating").textContent = team.rating;
            TeamUtil.appendLeagueDiv(row.insertCell(), team);
            row.insertCell().appendChild(ElementUtil.createImage("flag/", team.region.toLowerCase(), "table-image-long"));
            row.appendChild(TeamUtil.createMembersCell(team, multiRow));
            TeamUtil.appendGamesInfo(row.insertCell(), team);
            row.insertCell().textContent = Math.round( team.wins / (team.wins + team.losses) * 100);
            row.appendChild(TeamUtil.createMiscCell(team));
            if(isCheater) {
                row.classList.add("team-cheater");
            } else {
                nonCheaterIx++;
            }
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

    static isCheaterTeam(team)
    {
        return team.members.find(m=>m.confirmedCheaterReportId);
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
            case "league":
                content = TeamUtil.createDynamicLeagueTable(parent);
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
        return document.createTextNode(Util.getTeamFormatAndTeamTypeString(teamFormat, teamType));
    }

    static appendRankInfo(parent, searchResult, team, teamIx)
    {
        const rank = teamIx == -1
            ? "-"
            : searchResult.meta != null
                ? Util.NUMBER_FORMAT.format(Util.calculateRank(searchResult, teamIx))
                : (!Util.isUndefinedRank(team.globalRank) ? Util.NUMBER_FORMAT.format(team.globalRank) : "-");
        const topPercentage = teamIx == -1
            ? "-"
            : searchResult.meta != null
                ? Util.DECIMAL_FORMAT.format((Util.calculateRank(searchResult, teamIx) / searchResult.meta.totalCount) * 100)
                : (!Util.isUndefinedRank(team.globalRank)
                    ? Util.DECIMAL_FORMAT.format( (team.globalRank / team.globalTeamCount) * 100)
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
        const tr = parent.closest("tr");
        const teamId = tr.getAttribute("data-team-id");
        const teamDateTime = tr.getAttribute("data-team-date-time");
        const viewData = Model.DATA.get(ViewUtil.getView(parent));
        return (viewData.get(VIEW_DATA.TEAMS) ? viewData.get(VIEW_DATA.TEAMS).result.find(t=>t.id==teamId && (teamDateTime ? t.dateTime==teamDateTime : true)) : null)
            || viewData.get(VIEW_DATA.SEARCH).result.find(t=>t.id==teamId);
    }

    static createDynamicRankTable(parent)
    {
        const viewData = Model.DATA.get(ViewUtil.getView(parent));
        const searchResult = viewData.get(VIEW_DATA.TEAMS) || viewData.get(VIEW_DATA.SEARCH);
        const team = TeamUtil.getTeamFromElement(parent);
        if(TeamUtil.isCheaterTeam(team))
        {
            const span = document.createElement("span");
            span.textContent = "No rank info available";
            return span;
        }

        const ranksTable = TableUtil.createTable(["Scope", "Rank", "Total", "Top%"], false);
        const tbody = ranksTable.querySelector("tbody");
        tbody.appendChild(TeamUtil.createRankRow(team, "global", team.globalTeamCount));
        tbody.appendChild(TeamUtil.createRankRow(team, "region", team.regionTeamCount));
        tbody.appendChild(TeamUtil.createRankRow(team, "league", team.leagueTeamCount));

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

    static createDynamicLeagueTable(parent)
    {
        const team = TeamUtil.getTeamFromElement(parent);

        const globalRange = TeamUtil.getGlobalLeagueRange(team);
        const regionRange = TeamUtil.getRegionLeagueRange(team);

        const leagueTable = TableUtil.createTable(["Type", "League"], false);
        const tbody = leagueTable.querySelector("tbody");

        let tr = tbody.insertRow();
        TableUtil.createRowTh(tr).textContent = "Top% Global"
        tr.insertCell().appendChild(TeamUtil.createLeagueDivFromEnum(globalRange.league, globalRange.tierType));
        tr = tbody.insertRow();
        TableUtil.createRowTh(tr).textContent = "Top% Region"
        tr.insertCell().appendChild(TeamUtil.createLeagueDivFromEnum(regionRange.league, regionRange.tierType));

        return leagueTable;
    }

    static getRegionLeagueRange(team)
    {
        if(team.regionRank <= 200) return {league: LEAGUE.GRANDMASTER, tierType: 0};

        const regionTopPercent = (team.regionRank / team.regionTeamCount) * 100;
        return Object.values(TIER_RANGE).find(r=>regionTopPercent <= r.bottomThreshold);
    }

    static getGlobalLeagueRange(team)
    {
        if(team.globalRank <= Object.values(REGION).length * 200) return {league: LEAGUE.GRANDMASTER, tierType: 0};

        const globalTopPercent = (team.globalRank / team.globalTeamCount) * 100;
        return Object.values(TIER_RANGE).find(r=>globalTopPercent <= r.bottomThreshold);
    }


    static createMemberInfo(team, member, appendRaces = true, multiRow = "lg")
    {
        const result = document.createElement("span");
        result.classList.add("team-member-info", "col-" + multiRow + (team.members.length > 1 ? "-6" : "-12"), "col-md-12");
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
        if(appendRaces) container.appendChild(TeamUtil.createRacesElem(member));
        container.appendChild(TeamUtil.createNameElem(member));
        if(member.confirmedCheaterReportId) container.appendChild(ElementUtil.createCheaterFlag());
        if(member.proNickname) container.appendChild(ElementUtil.createProFlag());
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
        nameElem.textContent = Util.convertFakeName(member, unmasked.unmaskedName);
        nameContainer.appendChild(nameElem);

        if(unmasked.maskedName.toLowerCase() != unmasked.unmaskedName.toLowerCase()
            || (unmasked.maskedTeam && unmasked.maskedTeam != unmasked.unmaskedTeam))
        {
            const maskedContainer = document.createElement("span");
            maskedContainer.classList.add("player-name-masked-container");
            if(unmasked.maskedTeam != null)
            {
                const teamElem = document.createElement("span");
                teamElem.classList.add("player-team-masked");
                teamElem.textContent = unmasked.maskedTeam;
                maskedContainer.appendChild(teamElem);
            }
            const maskedNameElem = document.createElement("span");
            maskedNameElem.classList.add("player-name-masked");
            maskedNameElem.textContent = Util.convertFakeName(member, unmasked.maskedName);
            maskedContainer.appendChild(maskedNameElem);
            nameContainer.appendChild(maskedContainer);
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
        const racesElem = document.createElement("span");
        racesElem.classList.add("race-percentage-container", "mr-1", "text-nowrap", "d-inline-block");

        //no favorite race
        if(gamesTotal == 0)
        {
            const percentageEntry = document.createElement("span");
            percentageEntry.classList.add("race-percentage-entry", "c-divider-slash", "text-secondary");
            percentageEntry.appendChild(ElementUtil.createNoRaceImage());
            racesElem.appendChild(percentageEntry);
            return racesElem;
        }

        //races
        const percentage = new Map();
        for(const [key, val] of games.entries())
            if(val != 0) percentage.set(key, Math.round((val / gamesTotal) * 100));
        const percentageSorted = new Map([...percentage.entries()].sort((a, b)=>b[1] - a[1]));
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

    static createMembersCell(team, multiRow = "lg")
    {
        const membersCell = document.createElement("td");
        membersCell.classList.add("complex", "cell-main", "team");
        const mRow = document.createElement("span");
        mRow.classList.add("row", "no-gutters");
        for(const teamMember of team.members) mRow.appendChild(TeamUtil.createMemberInfo(team, teamMember, true, multiRow));
        membersCell.appendChild(mRow);
        return membersCell;
    }

    static appendLeagueDiv(parent, team)
    {
        parent.setAttribute("data-toggle", "popover");
        parent.setAttribute("data-ctype", "league");
        parent.appendChild(TeamUtil.createLeagueDiv(team));
    }

    static createLeagueDiv(team)
    {
        const league = EnumUtil.enumOfId(team.league.type, LEAGUE);
        const div = TeamUtil.createLeagueDivFromEnum(league, team.tierType);
        return div;
    }

    static createLeagueDivFromEnum(league, tierType)
    {
        const leagueDiv = document.createElement("div");
        leagueDiv.classList.add("text-nowrap");
        leagueDiv.appendChild(ElementUtil.createImage("league/", league.name, "table-image table-image-square mr-1"));
            leagueDiv.appendChild(ElementUtil.createImage("league/", "tier-" + (tierType != null ? tierType + 1 : 1),
                "table-image-additional" + (tierType == null ? " invisible" : "")));
        return leagueDiv;
    }

    static createMiscCell(team)
    {
        const bufCell = document.createElement("td");
        bufCell.classList.add("text-nowrap", "misc", "text-right")
        const historyLink = ElementUtil.createTagButton("a",  "table-image table-image-square background-cover mr-3 d-inline-block chart-line-img");
        historyLink.setAttribute("href", TeamUtil.getTeamMmrHistoryHref([team]));
        historyLink.setAttribute("target", "_blank");
        historyLink.setAttribute("rel", "noopener");
        bufCell.appendChild(historyLink);
        bufCell.appendChild(BufferUtil.createToggleElement(BufferUtil.teamBuffer, team));
        return bufCell;
    }

    static isAlternativelyUpdatedTeam(team)
    {
        return ALTERNATIVE_UPDATE_REGIONS.length > 0
            && ALTERNATIVE_UPDATE_REGIONS.includes(team.region)
            && Session.currentSeasonsMap.get(team.region).get(team.season + 1) == null //is last season
            || team.season == 46 || team.season == 47;
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

    static updateTeamMmr(searchParams = null)
    {
        if(searchParams == null) searchParams = TeamUtil.getTeamMmrHistoryParams(BufferUtil.teamBuffer.buffer.values());
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
            .catch(error => Session.onPersonalException(error));
    }

    static updateTeamMmrModel(searchParams)
    {
        const reqParams = new URLSearchParams();
        for(const id of searchParams.getAll("legacyUid")) reqParams.append("legacyUid", id);
        const request = `${ROOT_CONTEXT_PATH}api/team/history/common?${reqParams.toString()}`;
        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(Session.verifyJsonResponse)
            .then(json => new Promise((res, rej)=>{
                const teams = [];
                for(const history of Object.values(json)) {
                    teams.push(history.teams[history.teams.length - 1]);
                    history.states = CharacterUtil.expandMmrHistory(history.states);
                }
                teams.sort((a, b)=>b.rating - a.rating);
                Model.DATA.get(VIEW.TEAM_MMR).set(VIEW_DATA.SEARCH, {result: teams});
                Model.DATA.get(VIEW.TEAM_MMR).set(VIEW_DATA.VAR, json);
                res(json);
            }));
    }

    static updateTeamMmrView()
    {
        const searchResult = Model.DATA.get(VIEW.TEAM_MMR).get(VIEW_DATA.VAR);
        const seasonLastOnly = document.getElementById("team-mmr-season-last").checked;
        const depth = document.getElementById("team-mmr-depth").value;
        const depthDate = depth > 0 ? new Date(Date.now() - (depth * 24 * 60 * 60 * 1000)) : null;
        const excludeStart = document.getElementById("team-mmr-exclude-start").value || 0;
        const excludeEnd = document.getElementById("team-mmr-exclude-end").value || 0;
        const yAxis = document.getElementById("team-mmr-y-axis").value;
        const mmrYValueGetter = CharacterUtil.mmrYValueGetter(yAxis);
        const xAxisType = document.getElementById("team-mmr-x-type").checked ? "time" : "category";
        const showLeagues = document.getElementById("team-mmr-leagues").checked;
        TeamUtil.updateTeamsTable(document.querySelector("#team-mmr-teams-table"), Model.DATA.get(VIEW.TEAM_MMR).get(VIEW_DATA.SEARCH));
        let transformedData = [];
        let curEntry = 0;
        const headers = [];
        for(const [legacyUid, history] of Object.entries(searchResult)) {
            const refTeam = history.teams[history.teams.length - 1];
            const group = TeamUtil.generateTeamName(refTeam);
            headers.push(group);
            const lastSeasonTeamSnapshotDates = CharacterUtil.getLastSeasonTeamSnapshotDates(history.states);
            if(!seasonLastOnly) {
                for(const state of history.states) {
                    state.group = {name: group, order: curEntry};
                    state.teamState.dateTime = new Date(state.teamState.dateTime);
                    transformedData.push(state);
                }
            }
            for(const team of history.teams) {
                const state = CharacterUtil.convertTeamToTeamSnapshot(team, lastSeasonTeamSnapshotDates, seasonLastOnly);
                state.group = {name: group, order: curEntry};
                transformedData.push(state);
            }
            curEntry++;
        }
        transformedData = TeamUtil.filterTeamMmrHistory(transformedData, depthDate, excludeStart, excludeEnd);
        transformedData.sort((a,b)=>a.teamState.dateTime.getTime() - b.teamState.dateTime.getTime());
        transformedData.forEach(CharacterUtil.calculateMmrHistoryTopPercentage);
        const mmrHistoryGrouped = Util.groupBy(transformedData, h=>h.teamState.dateTime.getTime());
        const data = [];
        const rawData = [];
        for(const [dateTime, histories] of mmrHistoryGrouped.entries())
        {
            rawData.push(histories);
            data[dateTime] = {};
            for(const history of histories) data[dateTime][history.group.name] = mmrYValueGetter(history);
        }
        ChartUtil.CHART_RAW_DATA.set("team-mmr-table", {rawData: rawData, additionalDataGetter: TeamUtil.getAdditionalMmrHistoryData});
        TableUtil.updateVirtualColRowTable
        (
            document.getElementById("team-mmr-table"),
            data,
            (tableData=>{
                CharacterUtil.decorateMmrPoints(tableData, rawData, headers, (raw, header)=>raw.find(e=>e.group.name == header), showLeagues);
                ChartUtil.CHART_RAW_DATA.get("team-mmr-table").data = tableData;
            }),
            null,
            null,
            xAxisType == "time" ? dt=>parseInt(dt) : dt=>Util.DATE_TIME_FORMAT.format(new Date(parseInt(dt)))
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
        const lines = [];
        lines.push(curData.season);
        curData.tierType = curData.tier;
        lines.push(TeamUtil.createLeagueDiv(curData));
        lines.push(curData.teamState.rating);
        lines.push(CharacterUtil.createMmrHistoryGames(curData));
        CharacterUtil.appendAdditionalMmrHistoryRanks(curData, lines);
        return lines;
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
        document.getElementById("team-mmr-y-axis").addEventListener("change", e=>{
            CharacterUtil.setMmrYAxis(e.target.value, e.target.getAttribute("data-chartable"));
            TeamUtil.updateTeamMmrView()
        });
        document.getElementById("team-mmr-x-type").addEventListener("change", e=>window.setTimeout(TeamUtil.updateTeamMmrView, 1));
        document.getElementById("team-mmr-leagues").addEventListener("change", e=>TeamUtil.updateTeamMmrView());
    }

    static afterEnhance()
    {
        const el = document.getElementById("team-mmr-y-axis");
        if(!el) return;
        CharacterUtil.setMmrYAxis(el.value, el.getAttribute("data-chartable"));
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
    
    static createTeamFromSnapshot(team, snapshot)
    {
        let teamClone = {};
        Object.assign(teamClone, team);
        Object.assign(teamClone, snapshot.teamState);
        teamClone.league = snapshot.league;
        teamClone.leagueType = snapshot.league.type;
        teamClone.tierType = snapshot.tier;
        if(snapshot.teamState.wins) {
            teamClone.losses = snapshot.teamState.games - snapshot.teamState.wins;
        } else {
            teamClone.wins = team.wins;
        }
        return teamClone;
    }

}
