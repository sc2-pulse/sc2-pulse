// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class TeamUtil
{

    static updateTeamsTable(table, searchResult, clear = true, multiRow = "lg")
    {
        const fullMode = table.getAttribute("data-ladder-format-show") == "true";
        const showLastPlayed = table.getAttribute("data-ladder-last-played-show") !== "false";
        const ladderBody = table.getElementsByTagName("tbody")[0];
        if(clear) ElementUtil.removeChildren(ladderBody);

        let nonCheaterIx = 0;
        let lastQueue = null;
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
            if(fullMode) {
                row.insertCell().appendChild(TeamUtil.createTeamFormatInfo(team));
                if(lastQueue != null && team.league.queueType != lastQueue) row.classList.add("section-splitter");
            }
            TeamUtil.appendRankInfo(TableUtil.createRowTh(row), searchResult, team, isCheater ? -1 : nonCheaterIx);
            TableUtil.insertCell(row, "rating").textContent = team.rating;
            TeamUtil.appendLeagueDiv(row.insertCell(), team);
            row.insertCell().appendChild(ElementUtil.createImage("flag/", team.region.toLowerCase(), "table-image-long"));
            row.appendChild(TeamUtil.createMembersCell(team, multiRow));
            TeamUtil.appendGamesInfo(row.insertCell(), team);
            row.insertCell().textContent = Math.round( team.wins / (team.wins + team.losses) * 100);
            if(showLastPlayed) row.appendChild(TeamUtil.createLastPlayedCell(team));
            row.appendChild(TeamUtil.createMiscCell(team));
            if(isCheater) {
                row.classList.add("team-cheater");
            } else {
                nonCheaterIx++;
            }
            lastQueue = team.league.queueType;
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
        return team.members.find(m=>m.restrictions == true);
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
            case "last-played":
                content = TeamUtil.createDynamicLastPlayedContent(parent);
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
        const regionRange = TeamUtil.getTeamRegionLeagueRange(team);

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

    static createDynamicLastPlayedContent(parent)
    {
        const team = TeamUtil.getTeamFromElement(parent);
        return ElementUtil.createElement(
            "span",
            null,
            "last-played-details",
            team.lastPlayed
                ? Util.DATE_TIME_FORMAT.format(Util.parseIsoDateTime(team.lastPlayed))
                : "unknown");
    }

    static getTeamRegionLeagueRange(team)
    {
        return Util.getLeagueRange(team.regionRank, team.regionTeamCount);
    }

    static getGlobalLeagueRange(team)
    {
        if(team.globalRank <= Object.values(REGION).length * SC2Restful.GM_COUNT) return {league: LEAGUE.GRANDMASTER, tierType: 0};

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
        if(member.restrictions != null) container.appendChild(ElementUtil.createCheaterFlag(member.restrictions ? CHEATER_FLAG.CHEATER : CHEATER_FLAG.SUSPICIOUS, false));
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

    static createLastPlayedCell(team)
    {
        const cell = document.createElement("td");
        if(!team.lastPlayed) return cell;

        cell.classList.add("last-played", "text-truncate");
        const sinceLastPlayed = luxon.Duration.fromMillis(Date.now() - Util.parseIsoDateTime(team.lastPlayed));
        cell.textContent = sinceLastPlayed.toLargestUnitString();
        cell.setAttribute("data-ctype", "last-played");
        cell.setAttribute("data-toggle", "popover");
        if(sinceLastPlayed.milliseconds <= TeamUtil.TEAM_ONLINE_DURATION) {
            cell.classList.add("text-success");
        } else if(sinceLastPlayed.milliseconds >= TeamUtil.TEAM_OLD_DURATION) {
            cell.classList.add("text-secondary");
        }
        return cell;
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
        return team.queueType
            + "-" + EnumUtil.enumOfId(team.teamType, TEAM_TYPE).code
            + "-" + EnumUtil.enumOfName(team.region, REGION).code
            + "-" + team.legacyId;
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
        if(searchParams == null) searchParams = TeamUtil.getTeamMmrHistoryParams(Array.from(BufferUtil.teamBuffer.buffer.values()));
        const stringParams = searchParams.toString();
        const params = {params: stringParams};
        Util.setGeneratingStatus(STATUS.BEGIN);

        return TeamUtil.updateTeamMmrModel(searchParams)
            .then(e=>{
                TeamUtil.updateTeamMmrView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
                if(!Session.isHistorical) HistoryUtil.pushState(params, document.title, "?" + stringParams + "#team-mmr");
                Session.currentSearchParams = stringParams;
                if(!Session.isHistorical) HistoryUtil.updateActiveTabs();
            })
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
            .then(json => {
                const teams = [];
                for(const history of Object.values(json)) {
                    teams.push(history.teams[history.teams.length - 1]);
                    history.states = CharacterUtil.expandMmrHistory(history.states);
                }
                teams.sort((a, b)=>b.rating - a.rating);
                Model.DATA.get(VIEW.TEAM_MMR).set(VIEW_DATA.SEARCH, {result: teams});
                Model.DATA.get(VIEW.TEAM_MMR).set(VIEW_DATA.VAR, json);
                return json;
            });
    }

    static updateTeamMmrView()
    {
        const searchResult = Model.DATA.get(VIEW.TEAM_MMR).get(VIEW_DATA.VAR);
        const seasonLastOnly = document.getElementById("team-mmr-season-last").checked;
        const depth = document.getElementById("team-mmr-depth").value;
        const depthDate = depth > 0 ? new Date(Date.now() - (depth * 24 * 60 * 60 * 1000)) : null;
        const yAxis = document.getElementById("team-mmr-y-axis").value;
        const mmrYValueGetter = CharacterUtil.mmrYValueGetter(yAxis);
        const xAxisType = document.getElementById("team-mmr-x-type").checked ? "time" : "category";
        const showLeagues = document.getElementById("team-mmr-leagues").checked;
        const teams = Model.DATA.get(VIEW.TEAM_MMR).get(VIEW_DATA.SEARCH);
        const region = teams.result.length > 0
            ? teams.result[0].members[0].character.region
            : "EU";
        TeamUtil.updateTeamsTable(document.querySelector("#team-mmr-teams-table"), teams);
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
        transformedData = TeamUtil.filterTeamMmrHistory(transformedData, depthDate);
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
        ChartUtil.setCustomConfigOption("team-mmr-table", "region", region);
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
        TeamUtil.updateTeamMmrFilters(transformedData, depthDate);
    }

    static filterTeamMmrHistory(mmrHistory, depthDate)
    {
        if(depthDate != null) mmrHistory = mmrHistory.filter(h=>h.teamState.dateTime.getTime() > depthDate.getTime());
        return mmrHistory;
    }

    static updateTeamMmrFilters(mmrHistory, depthDate)
    {
        document.getElementById("team-mmr-filters").textContent =
        "(" + mmrHistory.length  + " entries"
        + (depthDate != null ? ", starting from " + Util.DATE_FORMAT.format(depthDate) : "")
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
        lines.push(CharacterUtil.createMmrHistoryGamesFromTeamState(curData));
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
        teamClone.lastPlayed = snapshot.teamState.dateTime;
        if(snapshot.teamState.wins) {
            teamClone.losses = snapshot.teamState.games - snapshot.teamState.wins;
        } else {
            teamClone.wins = team.wins;
        }
        return teamClone;
    }

    static getTeams(params)
    {
        return Session.beforeRequest()
           .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/team?${params.toString()}`))
           .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static fuzzyTeamSearchParams(params)
    {
        const rating = parseInt(params.get("rating"));
        if(rating) {
            params.delete("rating");
            params.append("ratingMin", Math.max(rating - TeamUtil.TEAM_SEARCH_MMR_OFFSET, 1));
            params.append("ratingMax", rating + TeamUtil.TEAM_SEARCH_MMR_OFFSET);
        }
        const wins = parseInt(params.get("wins"));
        if(wins) {
            params.delete("wins");
            params.append("winsMin", Math.max(wins - TeamUtil.TEAM_SEARCH_GAMES_OFFSET, 0));
            params.append("winsMax", wins);
        }
        return params;
    }

    static loadTeamSearchModel(params)
    {
        return TeamUtil.getTeams(TeamUtil.fuzzyTeamSearchParams(params))
            .then(teams=>{
                if(!teams) teams = [];
                const model = Model.DATA.get(VIEW.TEAM_SEARCH);
                model.set(VIEW_DATA.SEARCH, {result: teams});
                model.set(VIEW_DATA.VAR, teams);
            });
    }

    static updateTeamSearchModel()
    {
        const model = Model.DATA.get(VIEW.TEAM_SEARCH);
        if(!model) return;

        TeamUtil.sortTeamSearchModel(model);
    }

    static sortTeamSearchModel(model)
    {
        const teams = model.get(VIEW_DATA.VAR);
        if(teams.length == 0) return;

        const sorting = localStorage.getItem("search-team-sort") || "lastPlayedTimestamp";
        if(sorting == "lastPlayedTimestamp" && !teams[0].lastPlayedTimestamp)
            for(const team of teams)
                team.lastPlayedTimestamp = Util.parseIsoDateTime(team.lastPlayed).getTime();
        teams.sort((a, b)=>b[sorting] - a[sorting]);
    }

    static updateTeamSearchView()
    {
        const searchResult = Model.DATA.get(VIEW.TEAM_SEARCH).get(VIEW_DATA.SEARCH);
        const table = document.querySelector("#team-search-teams");
        TeamUtil.updateTeamsTable(table, searchResult);
        table.classList.remove("d-none");
    }

    static updateTeams(params)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return TeamUtil.loadTeamSearchModel(params)
            .then(e=>{
                TeamUtil.updateTeamSearchModel();
                TeamUtil.updateTeamSearchView();
                Util.setGeneratingStatus(STATUS.SUCCESS);

                const fullParams = new URLSearchParams(params);
                fullParams.append("type", "team-search");
                const stringParams = fullParams.toString();
                const paramsObj = {params: stringParams};
                if(!Session.isHistorical) HistoryUtil.pushState(paramsObj, document.title, "?" + stringParams + "#search-team");
                Session.currentSearchParams = stringParams;
                if(!Session.isHistorical) HistoryUtil.updateActiveTabs();
            })
            .catch(error => Session.onPersonalException(error));
    }

    static onTeamSearch(evt)
    {
        evt.preventDefault();
        const params = new URLSearchParams(new FormData(evt.target));
        return TeamUtil.updateTeams(params);
    }

    static onTeamSort(evt)
    {
        if(!Model.DATA.get(VIEW.TEAM_SEARCH).get(VIEW_DATA.VAR)) return;

        TeamUtil.updateTeamSearchModel();
        TeamUtil.updateTeamSearchView();
    }

    static enhanceTeamSearch()
    {
        const form = document.querySelector("#form-search-team");
        if(form) form.addEventListener("submit", TeamUtil.onTeamSearch);
        const sortCtl = document.querySelector("#search-team-sort");
        if(sortCtl) sortCtl.addEventListener("change", ()=>window.setTimeout(TeamUtil.onTeamSort, 1));
    }

    static createTeamGroupBaseParams(ids, legacyUids, fromSeason, toSeason)
    {
        const params = new URLSearchParams();
        if(ids) ids.forEach(id=>params.append("id", id));
        if(legacyUids) legacyUids.forEach(l=>params.append("legacyUid", l));
        if(fromSeason != null) params.append("fromSeason", fromSeason);
        if(toSeason != null) params.append("toSeason", toSeason);
        return params;
    }

    static createHistoryParams(ids, legacyUids, groupBy, from, to)
    {
        const params = TeamUtil.createTeamGroupBaseParams(ids, legacyUids);
        if(groupBy) params.append("groupBy", groupBy.fullName);
        if(from) params.append("from", from.toISOString());
        if(to) params.append("to", to.toISOString());

        return params;
    }

    static getHistory(ids, legacyUids, groupBy, from, to, staticColumns, historyColumns)
    {
        const params = TeamUtil.createHistoryParams(ids, legacyUids, groupBy, from, to);
        if(staticColumns) staticColumns.forEach(c=>params.append("static", c.fullName));
        if(historyColumns) historyColumns.forEach(h=>params.append("history", h.fullName));
        const request = ROOT_CONTEXT_PATH + "api/team/group/history?" + params.toString();

        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static getHistorySummary(ids, legacyUids, groupBy, from, to, staticColumns, summaryColumns)
    {
        const params = TeamUtil.createHistoryParams(ids, legacyUids, groupBy, from, to);
        if(staticColumns) staticColumns.forEach(c=>params.append("static", c.fullName));
        if(summaryColumns) summaryColumns.forEach(s=>params.append("summary", s.fullName));
        const request = ROOT_CONTEXT_PATH + "api/team/group/history/summary?" + params.toString();

        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static getTeamGroup(ids, legacyUids, fromSeason, toSeason)
    {
        const params = TeamUtil.createTeamGroupBaseParams(ids, legacyUids, fromSeason, toSeason);
        const request = ROOT_CONTEXT_PATH + "api/team/group/team/full?" + params.toString();

        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static createLegacyUid(queue, teamType, region, legacyId)
    {
        return queue.code + "-" + teamType.code + "-" + region.code + "-" + legacyId;
    }

    static createLegacyIdSection(member)
    {
        return member.realm + "." + member.id + "." + (member.race || "");
    }

    static createLegacyId(members)
    {
        return members.map(TeamUtil.createLegacyIdSection).join("~");
    }

    static parseLegacyId(legacyId)
    {
        const split = legacyId.split(".");
        return {
            realm: parseInt(split[0]),
            id: parseInt(split[1]),
            race: split[2] !=='' ? EnumUtil.enumOfId(parseInt(split[2]), RACE) : null
        };
    }

    static createLegacyIdsForAllRaces(member)
    {
        const memberClone = structuredClone(member);
        return Object.values(RACE).map(race=>{
            memberClone.race = race.code;
            return TeamUtil.createLegacyIdSection(memberClone);
        });
    }
    
    static createLegacyUidsForAllRaces(queue, teamType, region, member)
    {
        return TeamUtil.createLegacyIdsForAllRaces(member)
            .map(legacyId=>TeamUtil.createLegacyUid(queue, teamType, region, legacyId));
    }

    static createLegacyUidFromHistoryStaticData(staticData)
    {
        return TeamUtil.createLegacyUid(
            EnumUtil.enumOfId(staticData[TEAM_HISTORY_STATIC_COLUMN.QUEUE_TYPE.fullName], TEAM_FORMAT),
            EnumUtil.enumOfId(staticData[TEAM_HISTORY_STATIC_COLUMN.TEAM_TYPE.fullName], TEAM_TYPE),
            EnumUtil.enumOfId(staticData[TEAM_HISTORY_STATIC_COLUMN.REGION.fullName], REGION),
            staticData[TEAM_HISTORY_STATIC_COLUMN.LEGACY_ID.fullName]
        );
    }

}

TeamUtil.TEAM_SEARCH_MMR_OFFSET = 50;
TeamUtil.TEAM_SEARCH_GAMES_OFFSET = 2;
TeamUtil.TEAM_ONLINE_DURATION = 60 * 40 * 1000;
TeamUtil.TEAM_OLD_DURATION = 60 * 60 * 24 * 14 * 1000;