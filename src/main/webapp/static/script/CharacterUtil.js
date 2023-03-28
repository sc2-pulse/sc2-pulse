// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class CharacterUtil
{

    static showCharacterInfo(e = null, explicitId = null)
    {
        if (e != null) e.preventDefault();
        const id = explicitId || e.currentTarget.getAttribute("data-character-id");

        const promises = [];
        const searchParams = new URLSearchParams();
        searchParams.append("type", "character");
        searchParams.append("id", id);
        const stringParams = searchParams.toString();
        searchParams.append("m", "1");
        promises.push(BootstrapUtil.hideActiveModal(["versus-modal", "player-info", "error-generation"]));
        promises.push(CharacterUtil.updateCharacter(id));

        return Promise.all(promises)
            .then(o=>new Promise((res, rej)=>{
                if(!Session.isHistorical) HistoryUtil.pushState({type: "character", id: id}, document.title, "?" + searchParams.toString() + "#player-stats-summary");
                Session.currentSearchParams = stringParams;
                res();
            }))
            .then(e=>BootstrapUtil.showModal("player-info"));
    }

    static updateCharacterModel(id)
    {
        const params = new URLSearchParams();
        params.append("matchType", CharacterUtil.getMatchTypePath(false));
        if(document.getElementById("mmr-depth").value) params.append("mmrHistoryDepth", document.getElementById("mmr-depth").value);
        const request = ROOT_CONTEXT_PATH + "api/character/" + id + "/common?" + params.toString();
        const characterPromise = Session.beforeRequest()
            .then(n=>fetch(request).then(Session.verifyJsonResponse));
        return characterPromise
            .then(json => new Promise((res, rej)=>{
                json.history = CharacterUtil.expandMmrHistory(json.history);
                const searchStd = json;
                searchStd.result = json.teams;
                Model.DATA.get(VIEW.CHARACTER).set(VIEW_DATA.SEARCH, searchStd);
                Model.DATA.get(VIEW.CHARACTER).set(VIEW_DATA.VAR, json.linkedDistinctCharacters.map(c=>c.members.character).find(c=>c.id == id));
                Model.DATA.get(VIEW.CHARACTER).set("reports", json.reports)
                res(json);
             }));
    }

    static expandMmrHistory(history)
    {
        if(!history || !history.season || history.season.length == 0) return [];
        const expanded = new Array(history.season.length);
        for(let i = 0; i < history.season.length; i++)
        {
            expanded[i] =
            {
                teamState:
                {
                    teamId: history.teamId[i],
                    dateTime: history.dateTime[i],
                    games: history.games[i],
                    wins: history.wins[i],
                    rating: history.rating[i],
                    globalRank: history.globalRank[i],
                    globalTeamCount: history.globalTeamCount[i],
                    regionRank: history.regionRank[i],
                    regionTeamCount: history.regionTeamCount[i],
                    leagueRank: history.leagueRank[i],
                    leagueTeamCount: history.leagueTeamCount[i],
                },
                league:
                {
                    type: history.leagueType[i],
                    queueType: history.queueType[i],
                    teamType: history.teamType[i]
                },
                season: history.season[i],
                tier: history.tier[i],
                race: history.race[i]
            };
        }
        return expanded;
    }

    static getMatchTypePath(path = true)
    {
        const type = localStorage.getItem("matches-type");
        if(type == null || type == "all") return "";
        return path ? "/" + type : type;
    }

    static updateCharacterReportsModel()
    {
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/character/report/list/${Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).linkedDistinctCharacters.map(c=>c.members.character.id).join(",")}`))
            .then(Session.verifyJsonResponse)
            .then(json => new Promise((res, rej)=>{
                Model.DATA.get(VIEW.CHARACTER).set("reports", json);
                res();
            }));
    }

    static updateAllCharacterReportsModel()
    {
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/character/report/list`))
            .then(Session.verifyJsonResponse)
            .then(json => new Promise((res, rej)=>{
                Model.DATA.get(VIEW.CHARACTER_REPORTS).set("reports", json);
                res();
            }));
    }

    static updateAllCharacterReports()
    {
        if(!document.querySelector("#all-character-reports")) return Promise.resolve();

        Util.setGeneratingStatus(STATUS.BEGIN);
        return CharacterUtil.updateAllCharacterReportsModel()
            .then(e=>new Promise((res, rej)=>{
                CharacterUtil.updateAllCharacterReportsView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
                res();
            }))
            .catch(error => Session.onPersonalException(error));
    }

    static updateCharacterTeamsView()
    {
        const id = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).id;
        const searchResult = {result: Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).teams};
        CharacterUtil.updateCharacterInfo(Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH), id);
        CharacterUtil.updateCharacterTeamsSection(searchResult);
    }

    static updateCharacter(id)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return CharacterUtil.updateCharacterModel(id)
            .then(o => CharacterUtil.updateCharacterMatchesView())
            .then(jsons => new Promise((res, rej)=>{
                CharacterUtil.resetAdditionalLinks();
                if(document.querySelector("#player-stats-player.active"))
                    CharacterUtil.updateAdditionalCharacterLinks(Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).id);
                CharacterUtil.updateCharacterTeamsView();
                CharacterUtil.updateCharacterStatsView();
                CharacterUtil.updateCharacterLinkedCharactersView(id);
                CharacterUtil.updateCharacterMmrHistoryView();
                CharacterUtil.updateCharacterReportsView();
                for(const link of document.querySelectorAll(".character-link-follow-only[rel~=nofollow]")) link.relList.remove("nofollow");
                Util.setGeneratingStatus(STATUS.SUCCESS);
                res();
            }))
            .catch(error => Session.onPersonalException(error));
    }

    static resetAdditionalLinks()
    {
        document.querySelector("#character-links").setAttribute("data-links-loaded", "false");
        Model.DATA.get(VIEW.CHARACTER).delete("additionalLinks");
    }

    static enhanceDynamicCharacterData()
    {
        $("#player-stats-player-tab").on('shown.bs.tab', e=>CharacterUtil.updateAdditionalCharacterLinks(Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).id));
        document.querySelectorAll(".character-additional-links-reload")
            .forEach(reloadCtl=>reloadCtl.addEventListener("click",e=>{
                CharacterUtil.resetAdditionalLinks();
                CharacterUtil.updateAdditionalCharacterLinks(Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).id);
            }));
    }

    static updateAdditionalCharacterLinks(id)
    {
        document.querySelectorAll("#character-links-section .indicator-loading")
            .forEach(indicator=>indicator.classList.add("d-none"));
        const linksContainer = document.querySelector("#character-links");
        if(linksContainer.getAttribute("data-links-loaded") === "true") return;

        const loadingIndicator = document.querySelector("#character-additional-links-loading-indicator");
        loadingIndicator.classList.remove("d-none");
        return CharacterUtil.updateAdditionalCharacterLinksModel(id)
            .then(e => new Promise((res, rej)=>{
                CharacterUtil.updateAdditionalCharacterLinksView();
                linksContainer.setAttribute("data-links-loaded", "true");
                loadingIndicator.classList.add("d-none");
                res(e);
            }))
            .catch(error => new Promise((res, rej)=>{
                loadingIndicator.classList.add("d-none");
                CharacterUtil.updateAdditionalCharacterLinksView();
                if(!error.message.includes("404")) {
                    document.querySelector("#character-additional-links-loading-failed").classList.remove("d-none");
                } else {
                    linksContainer.setAttribute("data-links-loaded", "true");
                }
                res();
            }));
    }

    static loadAdditionalCharacterLinks(id)
    {
        return Session.beforeRequest()
           .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/character/${encodeURIComponent(id)}/links/additional`))
           .then(Session.verifyJsonResponse);
    }

    static updateAdditionalCharacterLinksModel(id)
    {
        return CharacterUtil.loadAdditionalCharacterLinks(id)
            .then(json => new Promise((res, rej)=>{
                Model.DATA.get(VIEW.CHARACTER).set("additionalLinks", json);
                res(json);
            }));
    }

    static updateAdditionalCharacterLinksView()
    {
        const container = document.querySelector("#additional-links-container");
        const links = Model.DATA.get(VIEW.CHARACTER).get("additionalLinks");
        if(!links) {
            container.classList.add("d-none");
            return;
        }
        container.classList.remove("d-none");
        document.querySelectorAll("#character-links .link-additional").forEach(e=>e.classList.add("d-none"));

        links.forEach(CharacterUtil.updateAdditionalLink);
    }

    static updateAdditionalLink(link)
    {
        switch(link.type) {
            case "BATTLE_NET":
                CharacterUtil.updateBattleNetProfileLink(link);
                break;
        }
    }

    static updateBattleNetProfileLink(link)
    {
        const linkElement = document.querySelector("#link-sc2-battle-net");
        linkElement.classList.remove("d-none");
        linkElement.querySelector(":scope span").textContent = link.absoluteUrl;
    }

    static updateCharacterInfo(commonCharacter, id)
    {
        const searchResult = commonCharacter.teams;
        const member = searchResult[0].members.filter(m=>m.character.id == id)[0];
        const account = member.account;
        const character = member.character;

        const info = document.getElementById("player-info");
        info.setAttribute("data-account-id", account.id);
        if(Session.currentAccount != null && Session.currentFollowing != null)
        {
            if(Object.values(Session.currentFollowing).filter(val=>val.followingAccountId == account.id).length > 0)
            {
                document.querySelector("#follow-button").classList.add("d-none");
                document.querySelector("#unfollow-button").classList.remove("d-none");
            }
            else
            {
                document.querySelector("#follow-button").classList.remove("d-none");
                document.querySelector("#unfollow-button").classList.add("d-none");
            }
        }

        CharacterUtil.updateCharacterInfoName(commonCharacter, member);
        const region = EnumUtil.enumOfName(character.region, REGION);
        const profileLinkElement = document.getElementById("link-sc2");
        const profileSuffix = `/${region.code}/${character.realm}/${character.battlenetId}`;
        document.getElementById("link-sc2arcade").setAttribute("href", "https://sc2arcade.com/profile" + profileSuffix + "/lobbies-history");
        if(region == REGION.CN)
        {
            //the upstream site is not supporting the CN region.
            profileLinkElement.parentElement.classList.add("d-none");
        }
        else
        {
            profileLinkElement.setAttribute("href", "https://starcraft2.com/profile" + profileSuffix);
            profileLinkElement.parentElement.classList.remove("d-none");
        }
        if(Util.isFakeBattleTag(account.battleTag)) {
            document.querySelector("#link-battletag").classList.add("d-none");
        } else {
            document.querySelector("#link-battletag").classList.remove("d-none");
            document.querySelector("#link-battletag span").textContent = account.battleTag;
        }
        CharacterUtil.updateCharacterDiscordConnection(commonCharacter);
        CharacterUtil.updateCharacterProInfo(commonCharacter);
    }

    static updateCharacterDiscordConnection(commonCharacter)
    {
        const discordUser = commonCharacter.discordUser;
        const connectionElem = document.querySelector("#link-discord-connection");
        if(!discordUser) {
            connectionElem.classList.add("d-none");
        } else {
            connectionElem.querySelector(":scope .tag").textContent = discordUser.name + "#" + discordUser.discriminator;
            connectionElem.classList.remove("d-none");
        }
    }

    static updateCharacterProInfo(commonCharacter)
    {
        for(const el of document.querySelectorAll(".pro-player-info")) el.classList.add("d-none");
        if(commonCharacter.proPlayer.proPlayer == null) return;

        for(const link of document.querySelectorAll("#revealed-report [rel~=nofollow]")) link.relList.remove("nofollow");
        const proPlayer = commonCharacter.proPlayer;
        document.querySelector("#pro-player-info").classList.remove("d-none");
        CharacterUtil.setProPlayerField("#pro-player-name", "td", proPlayer.proPlayer.name);
        CharacterUtil.setProPlayerField("#pro-player-birthday", "td", proPlayer.proPlayer.birthday != null
            ? Util.DATE_FORMAT.format(Util.parseIsoDate(proPlayer.proPlayer.birthday)) : null);
        CharacterUtil.setProPlayerField("#pro-player-country", "td", proPlayer.proPlayer.country ? Util.countryCodeToEmoji(proPlayer.proPlayer.country) : null);
        CharacterUtil.setProPlayerField("#pro-player-earnings", "td", proPlayer.proPlayer.earnings && proPlayer.proPlayer.earnings > 0
            ? "$" + Util.NUMBER_FORMAT.format(proPlayer.proPlayer.earnings) : null);
        CharacterUtil.setProPlayerField("#pro-player-team", "td", proPlayer.proTeam ? proPlayer.proTeam.name : null);
        for(const link of proPlayer.links)
        {
            const linkEl = document.querySelector("#link-" + link.type.toLowerCase());
            if(linkEl == null) continue;
            linkEl.setAttribute("href", link.url);
            linkEl.parentElement.classList.remove("d-none");
        }

    }

    static setProPlayerField(selector, sub, val)
    {
        if(val != null)
        {
            const nameEl = document.querySelector(selector);
            nameEl.querySelector(":scope " + sub).textContent = val;
            nameEl.classList.remove("d-none");
        }
    }

    static updateCharacterInfoName(commonCharacter, member)
    {
        let charName;
        let charClan;
        let charTeam;
        let charNameAdditional;
        let charNameAdditionalClan;
        const hashIx = member.character.name.indexOf("#");
        const nameNoHash = member.character.name.substring(0, hashIx);
        const maskedTeam = member.clan ? member.clan.tag : "";
        if(!Util.needToUnmaskName(nameNoHash, member.proNickname, member.account.battleTag))
        {
            charName = nameNoHash;
            charClan = maskedTeam;
            charNameAdditional = member.character.name.substring(hashIx);
        }
        else
        {
            const unmasked = Util.unmaskName(member);
            const unmaskedTeam = unmasked.unmaskedTeam ? unmasked.unmaskedTeam : "";
            charName = unmasked.unmaskedName;
            charTeam = unmaskedTeam;
            charNameAdditional = member.character.name;
            charNameAdditionalClan = maskedTeam;
        }
        document.getElementById("player-info-title-name").textContent = Util.convertFakeName(member, charName);
        const titleElem = document.getElementById("player-info-title");
        const clanElem = document.getElementById("player-info-title-clan");
        const teamElem = document.getElementById("player-info-title-team");
        const additionalNameElem = document.getElementById("player-info-title-name-additional");
        const additionalClanElem = document.getElementById("player-info-title-clan-additional");
        titleElem.querySelectorAll(":scope .player-info-region").forEach(e=>e.remove());
        titleElem.prepend(ElementUtil.createImage("flag/", member.character.region.toLowerCase(), "table-image-long player-info-region"));
        if(charNameAdditionalClan) {
            additionalClanElem.textContent = charNameAdditionalClan;
            additionalClanElem.setAttribute("href", encodeURI(`${ROOT_CONTEXT_PATH}?type=search&name=${"[" + charNameAdditionalClan + "]"}#search`));
            additionalClanElem.classList.remove("d-none");
        } else {
            additionalClanElem.classList.add("d-none");
        }
        if(charClan) {
            clanElem.textContent = charClan;
            clanElem.setAttribute("href", encodeURI(`${ROOT_CONTEXT_PATH}?type=search&name=${"[" + charClan + "]"}#search`));
            clanElem.classList.remove("d-none");
        } else {
            clanElem.classList.add("d-none");
        }
        if(charTeam) {
            teamElem.textContent = charTeam;
            teamElem.classList.remove("d-none");
        } else {
            teamElem.classList.add("d-none");
        }
        additionalNameElem.textContent = charNameAdditional;
        const additionalContainer = document.querySelector("#player-info-additional-container");
        additionalContainer.querySelectorAll(":scope .player-flag").forEach(f=>f.remove());
        const cheaterFlag = commonCharacter.linkedDistinctCharacters
            .flatMap(dc=>dc.members)
            .find(m=>m.restrictions == true)
                ? CHEATER_FLAG.CHEATER
                : commonCharacter.linkedDistinctCharacters
                  .flatMap(dc=>dc.members)
                  .find(m=>m.restrictions == false)
                    ? CHEATER_FLAG.SUSPICIOUS
                    : null;
        if(cheaterFlag) additionalContainer.appendChild(ElementUtil.createCheaterFlag(cheaterFlag, true));
        if(member.proNickname) additionalContainer.appendChild(ElementUtil.createProFlag());
    }

    static updateCharacterTeamsSection(searchResultFull)
    {
        const searchResult = searchResultFull.result;
        const grouped = searchResult.reduce(function(rv, x) {
            (rv[x["season"]] = rv[x["season"]] || []).push(x);
            return rv;
        }, {});

        const navs = document.querySelectorAll("#character-teams-section .nav-item");
        const panes = document.querySelectorAll("#character-teams-section .tab-pane");
        let shown = false;
        let ix = 0;

        for(const nav of navs) nav.classList.add("d-none");
        const groupedEntries = Object.entries(grouped);
        for(const [season, teams] of groupedEntries)
        {
            const nav = navs[ix];
            const link = nav.getElementsByClassName("nav-link")[0];
            const pane = panes[ix];
            const seasonFull = Session.currentSeasons.find(s=>s.battlenetId == season);
            const linkText = seasonFull.descriptiveName;
            link.textContent = linkText;
            if(!shown)
            {
                if(season == Session.currentSeason || ix == groupedEntries.length - 1)
                {
                    $(link).tab("show");
                    shown = true;
                }
            }
            const table = pane.getElementsByClassName("table")[0];
            TeamUtil.updateTeamsTable(table, {result: teams});
            nav.classList.remove("d-none");
            ix++;
        }
        ElementUtil.updateTabSelect(document.getElementById("teams-season-select"), navs);
    }

    static updateCharacterStatsView()
    {
        const searchResult = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).stats;
        const includePrevious = localStorage.getItem("player-search-stats-include-previous") != "false";
        const grayOutPrevious = localStorage.getItem("player-search-stats-gray-out-previous") != "false";
        for(const statsSection of document.getElementsByClassName("player-stats-dynamic")) statsSection.classList.add("d-none");
        for(const ladderStats of searchResult)
        {
            const stats = ladderStats.stats;
            const hasCurrentStats = ladderStats.currentStats.rating;
            const searchStats = includePrevious
                ? (hasCurrentStats ? ladderStats.currentStats :  ladderStats.previousStats)
                :  ladderStats.currentStats;
            const teamFormat = EnumUtil.enumOfId(stats.queueType, TEAM_FORMAT);
            const teamType = EnumUtil.enumOfId(stats.teamType, TEAM_TYPE);
            const raceName = stats.race == null ? "all" : EnumUtil.enumOfName(stats.race, RACE).name;
            const league = EnumUtil.enumOfId(stats.leagueMax, LEAGUE);
            const card = document.getElementById("player-stats-" + teamFormat.name + "-" + teamType.name);
            const raceStats = card.getElementsByClassName("player-stats-" + raceName)[0];
            raceStats.getElementsByClassName("player-stats-" + raceName + "-mmr")[0].textContent = stats.ratingMax;
            raceStats.getElementsByClassName("player-stats-" + raceName + "-games")[0].textContent = stats.gamesPlayed;
            CharacterUtil.insertSearchStatsSummary(raceStats.getElementsByClassName("player-stats-" + raceName + "-mmr-current")[0], searchStats.rating, hasCurrentStats, grayOutPrevious);
            CharacterUtil.insertSearchStatsSummary(raceStats.getElementsByClassName("player-stats-" + raceName + "-games-current")[0], searchStats.gamesPlayed, hasCurrentStats, grayOutPrevious);
            const leagueStats = raceStats.getElementsByClassName("player-stats-" + raceName + "-league")[0];
            ElementUtil.removeChildren(leagueStats);
            leagueStats.appendChild(ElementUtil.createImage("league/", league.name, "table-image table-image-square"));
            raceStats.classList.remove("d-none");
            card.classList.remove("d-none");
        }
        for(const card of document.querySelectorAll(".player-stats-section:not(.d-none)"))
        {
            const table = card.querySelector(".player-stats-table");
            const visibleRows = table.querySelectorAll("tr.player-stats-dynamic:not(.d-none)");
            if
            (
                visibleRows.length === 2
                && visibleRows[0].querySelector(".player-stats-games").textContent
                    == visibleRows[1].querySelector(".player-stats-games").textContent
            )
                table.querySelector(".player-stats-all").classList.add("d-none");
            const gamesCol = table.querySelectorAll("th")[3];
            const mmrCol = table.querySelectorAll("th")[1];
            TableUtil.sortTable(table, [mmrCol, gamesCol]);
        }
    }

    static insertSearchStatsSummary(elem, data, hasCurrentStats, grayOutPreviousSeason)
    {
        if(grayOutPreviousSeason && !hasCurrentStats) {
            elem.classList.add("text-secondary");
        } else {
            elem.classList.remove("text-secondary");
        }
        elem.textContent = data;
    }

    static updateCharacterMmrHistoryView()
    {
        const character = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR);
        const queueFilterSelect = document.getElementById("mmr-queue-filter");
        const queue = EnumUtil.enumOfFullName(queueFilterSelect.options[queueFilterSelect.selectedIndex].value, TEAM_FORMAT);
        const queueFilter = queue.code;
        const teamTypeFilter = queue == TEAM_FORMAT._1V1 ? TEAM_TYPE.ARRANGED.code : TEAM_TYPE.RANDOM.code;
        const depth = document.getElementById("mmr-depth").value || null;
        const depthStartTimestamp = depth ? Date.now() - (depth * 24 * 60 * 60 * 1000) : null;
        const excludeStart = document.getElementById("mmr-exclude-start").value || 0;
        const excludeEnd = document.getElementById("mmr-exclude-end").value || 0;
        const bestRaceOnly = document.getElementById("mmr-best-race").checked;
        const seasonLastOnly = document.getElementById("mmr-season-last").checked;
        const yAxis = document.getElementById("mmr-y-axis").value;
        const mmrYValueGetter = CharacterUtil.mmrYValueGetter(yAxis);
        const xAxisType = document.getElementById("mmr-x-type").checked ? "time" : "category";
        const showLeagues = document.getElementById("mmr-leagues").checked;

        const lastSeasonTeamSnapshotDates = CharacterUtil.getLastSeasonTeamSnapshotDates(Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).history);
        const teams = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).teams
            .filter(t=>t.queueType == queueFilter && t.teamType == teamTypeFilter)
            .map(t=>CharacterUtil.convertTeamToTeamSnapshot(t, lastSeasonTeamSnapshotDates, seasonLastOnly));
        let mmrHistory = teams;
        if(!seasonLastOnly) mmrHistory = mmrHistory
            .concat(Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).history);
        mmrHistory.forEach(CharacterUtil.calculateMmrHistoryTopPercentage);
        mmrHistory = CharacterUtil.filterMmrHistory(mmrHistory, queueFilter, teamTypeFilter, excludeStart, excludeEnd);
        mmrHistory.forEach(h=>h.teamState.dateTime = Util.parseIsoDateTime(h.teamState.dateTime));
        mmrHistory.sort((a, b)=>a.teamState.dateTime.getTime() - b.teamState.dateTime.getTime());
        if(queue !== TEAM_FORMAT._1V1) mmrHistory.forEach(h=>h.race = "ALL");
        if(depth) mmrHistory = mmrHistory.filter(h=>h.teamState.dateTime.getTime() >= depthStartTimestamp);
        const historyByRace = Util.groupBy(mmrHistory, h=>h.race);
        if(bestRaceOnly === true) mmrHistory = CharacterUtil.filterMmrHistoryBestRace(historyByRace);
        mmrHistory = CharacterUtil.filterMmrHistory(mmrHistory, queueFilter, teamTypeFilter, excludeStart, excludeEnd);
        const mmrHistoryGroped = Util.groupBy(mmrHistory, h=>h.teamState.dateTime.getTime());
        const headers = Array.from(historyByRace.keys()).sort((a, b)=>EnumUtil.enumOfName(a, RACE).order - EnumUtil.enumOfName(b, RACE).order);
        const data = [];
        const rawData = [];
        for(const [dateTime, histories] of mmrHistoryGroped.entries())
        {
            rawData.push(histories);
            data[dateTime] = {};
            for(const history of histories) data[dateTime][history.race] = mmrYValueGetter(history);
        }
        ChartUtil.CHART_RAW_DATA.set("mmr-table", {rawData: rawData, additionalDataGetter: CharacterUtil.getAdditionalMmrHistoryData});
        ChartUtil.setCustomConfigOption("mmr-table", "region", character.region);
        TableUtil.updateVirtualColRowTable
        (
            document.getElementById("mmr-table"),
            data,
            (tableData=>{
                CharacterUtil.decorateMmrPoints(tableData, rawData, headers, (raw, header)=>raw.find(e=>e.race == header), showLeagues);
                ChartUtil.CHART_RAW_DATA.get("mmr-table").data = tableData;
            }),
            queue == TEAM_FORMAT._1V1
                ? (a, b)=>EnumUtil.enumOfName(a, RACE).order - EnumUtil.enumOfName(b, RACE).order
                : null,
            queue == TEAM_FORMAT._1V1 ? (name)=>EnumUtil.enumOfName(name, RACE).name : (name)=>name.toLowerCase(),
            xAxisType == "time" ? dt=>parseInt(dt) : dt=>Util.DATE_TIME_FORMAT.format(new Date(parseInt(dt)))
        );
        document.getElementById("mmr-history-filters").textContent =
            "(" + queue.name
            + (depth ? ", starting from " + Util.DATE_TIME_FORMAT.format(new Date(depthStartTimestamp)) : "")
            + (excludeEnd > 0 ? ", excluding range " + excludeStart + "-" + excludeEnd : "") + ", "
              + mmrHistory.length  + " entries)";
        const gamesMmr = CharacterUtil.getGamesAndAverageMmrSortedArray(mmrHistory);
        CharacterUtil.updateTierProgressTable(document.querySelector("#mmr-tier-progress-table"), gamesMmr);
        CharacterUtil.updateGamesAndAverageMmrTable(document.querySelector("#mmr-summary-table"), gamesMmr);
    }
    
    static mmrYValueGetter(mode)
    {
        return CharacterUtil.MMR_Y_VALUE_GETTERS.get(mode ? mode : "default");
    }

    static getLastSeasonTeamSnapshotDates(states)
    {
        const result = new Map();
        let season = 999;
        for(let i = states.length - 1; i > -1; i--)
        {
            const state = states[i];
            if(state.season < season) {
                season = state.season;
                result.set(season, Util.parseIsoDateTime(state.teamState.dateTime));
            }
        }
        return result;
    }

    static convertTeamToTeamSnapshot(t, lastSeasonTeamSnapshotDates, seasonLastOnly)
    {
        const season = Session.currentSeasonsMap.get(t.region).get(t.season)[0];
        if(seasonLastOnly) return CharacterUtil.createTeamSnapshot(t, season.end);

        const date = (lastSeasonTeamSnapshotDates.get(t.season + 1) || Session.currentSeasonsMap.get(t.region).get(t.season + 1))
            ? ((lastSeasonTeamSnapshotDates.get(t.season) ? new Date(lastSeasonTeamSnapshotDates.get(t.season).getTime() + 1000)  : null)
                || new Date(season.end.getTime() - CharacterUtil.TEAM_SNAPSHOT_SEASON_END_OFFSET_MILLIS))
            : new Date();
        return CharacterUtil.createTeamSnapshot(t, date);
    }

    static calculateMmrHistoryTopPercentage(h)
    {
        if(h.globalTopPercent) return;
        h.teamState.globalTopPercent = (h.teamState.globalRank / h.teamState.globalTeamCount) * 100;
        h.teamState.regionTopPercent = (h.teamState.regionRank / h.teamState.regionTeamCount) * 100;
    }

    static decorateMmrPoints(tableData, rawData, headers, getter, injectLeague = true)
    {
        const pointStyles = [];
        for(const header of headers) {
            const curStyles = [];
            pointStyles.push(curStyles);
            let prevLeague = null;
            for(const raw of rawData) {
                const snapshot = getter(raw, header);
                if(!snapshot) {
                    curStyles.push('');
                    continue;
                }
                if(snapshot.league.type != prevLeague && injectLeague) {
                    curStyles.push(SC2Restful.IMAGES.get(EnumUtil.enumOfId(snapshot.league.type, LEAGUE).name.toLowerCase()));
                    prevLeague = snapshot.league.type;
                } else {
                    curStyles.push('');
                }
            }
        }
        tableData.pointStyles = pointStyles;
    }

    static getGamesAndAverageMmrSortedArray(mmrHistory)
    {
        const gamesMmr = CharacterUtil.getGamesAndAverageMmr(mmrHistory);
        const entries = Object.entries(gamesMmr);
        entries.sort((a, b)=>b[1].maximumMmr - a[1].maximumMmr);
        return entries;
    }

    static updateGamesAndAverageMmrTable(table, gamesMmr)
    {
        const tbody = table.querySelector(":scope tbody");
        ElementUtil.removeChildren(tbody);
        for(const [race, stats] of gamesMmr)
        {
            const tr = tbody.insertRow();
            const raceImage = SC2Restful.IMAGES.get(race.toLowerCase());
            const raceCell = tr.insertCell();
            if(raceImage) {
                raceCell.appendChild(raceImage.cloneNode());
            } else {
                raceCell.textContent = race;
            }
            tr.insertCell().textContent = stats.games;
            tr.insertCell().textContent = stats.lastMmr;
            tr.insertCell().textContent = stats.averageMmr;
            tr.insertCell().textContent = stats.maximumMmr;
        }
    }

    static updateTierProgressTable(table, gamesMmr)
    {
        const tbody = table.querySelector(":scope tbody");
        ElementUtil.removeChildren(tbody);
        for(const [race, stats] of gamesMmr)
        {
            const progress = CharacterUtil.createTierProgress(stats.lastTeamState);
            if(!progress) continue;

            const tr = tbody.insertRow();
            const raceImage = SC2Restful.IMAGES.get(race.toLowerCase());
            const raceCell = tr.insertCell();
            if(raceImage) {
                raceCell.appendChild(raceImage.cloneNode());
            } else {
                raceCell.textContent = race;
            }
            TableUtil.insertCell(tr, "cell-main").appendChild(progress);
        }
    }

    static createTierProgress(teamState)
    {
        if(!teamState.teamState.regionTeamCount || !teamState.teamState.regionRank) return null;
        const tierRange = TeamUtil.getRegionLeagueRange(teamState.teamState);
        let min, max, cur, nextTierRange;
        if(tierRange.league == LEAGUE.GRANDMASTER) {
            nextTierRange = {league: LEAGUE.GRANDMASTER, tierType: 0};
            min = SC2Restful.GM_COUNT;
            max = 1;
            cur = teamState.teamState.regionRank;
        } else {
            nextTierRange = tierRange.league == LEAGUE.MASTER && tierRange.tierType == 0
                ? CharacterUtil.getGrandmasterTierRange(teamState.teamState.regionTeamCount)
                : TIER_RANGE[tierRange.order - 1];
            min = tierRange.bottomThreshold;
            max = nextTierRange.bottomThreshold;
            cur = (teamState.teamState.regionRank / teamState.teamState.regionTeamCount) * 100;
        }
        const progressBar = ElementUtil.createProgressBar(cur, min, max);
        progressBar.classList.add("tier-progress", "flex-grow-1");
        progressBar.querySelector(":scope .progress-bar").classList.add("bg-" + tierRange.league.name.toLowerCase());
        const container = document.createElement("div");
        container.classList.add("text-nowrap", "d-flex", "gap-tiny");
        container.appendChild(SC2Restful.IMAGES.get(tierRange.league.name.toLowerCase()).cloneNode());
        container.appendChild(SC2Restful.IMAGES.get("tier-" + (tierRange.tierType + 1)).cloneNode());
        container.appendChild(progressBar);
        container.appendChild(SC2Restful.IMAGES.get(nextTierRange.league.name.toLowerCase()).cloneNode());
        container.appendChild(SC2Restful.IMAGES.get("tier-" + (nextTierRange.tierType + 1)).cloneNode());
        return container;
    }

    static getGrandmasterTierRange(regionTeamCount)
    {
        return {
            league: LEAGUE.GRANDMASTER,
            tierType: 0,
            bottomThreshold: (SC2Restful.GM_COUNT / regionTeamCount) * 100
        };
    }
    
    static getGamesAndAverageMmrString(mmrHistory)
    {
        let result = "games/avg mmr/max mmr";
        const gamesMmr = CharacterUtil.getGamesAndAverageMmr(mmrHistory);
        result += CharacterUtil.getGamesAndAverageMmrEntryString(gamesMmr, "all");
        for(const race of Object.values(RACE))
            result += CharacterUtil.getGamesAndAverageMmrEntryString(gamesMmr, race.name);
        return result;
    }

    static getGamesAndAverageMmrEntryString(gamesMmr, keyName)
    {
        const val = gamesMmr[keyName.toUpperCase()];
        if(!val) return "";
        return `, ${keyName.toLowerCase()}: ${val.games}/${val.averageMmr}/${val.maximumMmr}`;
    }

    static getGamesAndAverageMmr(mmrHistory)
    {
        const result = {};
        const mmrHistoryGrouped = Util.groupBy(mmrHistory, h=>h.race);
        for(const [race, histories] of mmrHistoryGrouped.entries())
        {
            const originalHistories = histories.filter(h=>!h.injected);
            if(originalHistories.length == 0) continue;
            const games = originalHistories.reduce( (acc, history, i, historyArray)=> {
                if(i == 0) return acc;
                if(history.teamState.teamId != historyArray[i - 1].teamState.teamId) return acc + history.teamState.games;

                const diff = history.teamState.games - historyArray[i - 1].teamState.games;
                const cGames = diff > -1 ? diff : history.teamState.games;
                return acc + cGames;
            }, 1);
            const mmr = originalHistories.map(h=>h.teamState.rating);
            const last = mmr[mmr.length - 1];
            const sum = mmr.reduce((a, b) => a + b, 0);
            const avg = (sum / mmr.length) || 0;
            const max = mmr.reduce((a, b) => Math.max(a, b));
            const lastTeamState = histories[histories.length - 1];
            result[race] = {games : games, lastMmr: last, averageMmr: Math.round(avg), maximumMmr: max, lastTeamState: lastTeamState};
        }
        return result;
    }

    static injectMmrFlatLines(history, historyByRace, teams, queueFilter, teamTypeFilter)
    {
        const firstDate = CharacterUtil.calculateFirstMmrDate();
        const injected = [];
        //use the same datetime to correctly group the points by timestamp later
        const now = new Date();
        CharacterUtil.injectLatestTeamMmrSnapshots(historyByRace, teams, queueFilter, teamTypeFilter, injected, firstDate);
        for(const raceHistory of historyByRace.values()) {
            CharacterUtil.injectMmrHistoryHeader(raceHistory, injected, firstDate);
            CharacterUtil.fillMmrGaps(raceHistory, injected, now);
            raceHistory.sort((a, b)=>a.teamState.dateTime.getTime() - b.teamState.dateTime.getTime());
            CharacterUtil.injectMmrHistoryTail(raceHistory, injected, now);
        }
        return history.concat(injected).sort((a, b)=>a.teamState.dateTime.getTime() - b.teamState.dateTime.getTime());
    }

    static injectLatestTeamMmrSnapshots(racialHistory, teams, queueFilter, teamTypeFilter, injectArray, firstDate)
    {
        const teamsFiltered = teams.filter(t=>
            t.league.queueType == queueFilter
            && t.league.teamType == teamTypeFilter
            && Session.currentSeasonsMap.get(t.season)[0].end.getTime() > firstDate.getTime()
        );
        if(teamsFiltered.length == 0) return;

        if(queueFilter == TEAM_FORMAT._1V1.code)
        {
            for(const race of Object.values(RACE))
            {
                const history = racialHistory.get(race.name.toUpperCase());
                const len = history ? history.length : 0;
                //skip if there is an actual history
                if(len > 0) continue;

                let teamsRacial = teamsFiltered
                    .filter(t=>TeamUtil.getFavoriteRace(t.members[0]) == race)
                    //desc
                    .sort((a, b)=>b.season - a.season);
                if(teamsRacial.length == 0) continue;

                const snap = CharacterUtil.createTeamSnapshot(teamsRacial[0], firstDate, true);
                racialHistory.set(race.name, [snap]);
                injectArray.push(snap);
            }
        }
        else
        {
            const history = racialHistory.get("ALL");
            const len = history ? history.length : 0;
            //skip if there is an actual history or there are no teams at all
            if(len > 0) return;

            let teamsRacial = teamsFiltered.sort((a, b)=>b.season - a.season);

            const snap = CharacterUtil.createTeamSnapshot(teamsRacial[0], firstDate, true);
            snap.race = "ALL";
            racialHistory.set("ALL", [snap]);
            injectArray.push(snap);
        }
    }

    static injectMmrHistoryHeader(history, injectArray, firstDate)
    {
        if(history.length == 0
            || Math.abs(history[0].teamState.dateTime.getTime() - firstDate.getTime()) < 2000
            || Session.currentSeasonsMap.get(history[0].season)[0].start.getTime() > firstDate.getTime()) return;

        const snap = CharacterUtil.cloneMmrPoint(history[0], firstDate);
        history.splice(0, 0, snap);
        injectArray.push(snap);
    }

    static fillMmrGaps(history, injected, now)
    {
        const curInjected = [];
        for(let i = 0; i < history.length; i++)
        {
            const cur = history[i];
            const prev = history[i == 0 ? 0 : i - 1];
            const toInject = Math.floor((cur.teamState.dateTime.getTime() - prev.teamState.dateTime.getTime()) / Util.DAY_MILLIS);
            CharacterUtil.injectMmrPoints(history, curInjected, prev, toInject);
        }
        Array.prototype.push.apply(injected, curInjected);
        Array.prototype.push.apply(history, curInjected);
    }

    static injectMmrHistoryTail(history, injected, now)
    {
        const curInjected = [];
        CharacterUtil.injectMmrPoints(history, curInjected, history[history.length - 1],
            Math.floor((now.getTime() - history[history.length - 1].teamState.dateTime.getTime()) / Util.DAY_MILLIS));
        const lastPoint = curInjected.length > 0 ? curInjected[curInjected.length - 1] : history[history.length - 1];
        const lastPointMaxDateTime = Session.currentSeasonsMap.get(lastPoint.season)[0].end;
        if(lastPoint.teamState.dateTime.getTime() < lastPointMaxDateTime.getTime())
             curInjected.push(CharacterUtil.cloneMmrPoint(lastPoint, lastPointMaxDateTime));
        Array.prototype.push.apply(injected, curInjected);
        Array.prototype.push.apply(history, curInjected);
    }

    static injectMmrPoints(history, injectArray, refPoint, toInject)
    {
        const maxDate = Session.currentSeasonsMap.get(refPoint.season)[0].end;
        for(let ii = 0; ii < toInject; ii++)
        {
            let date = new Date(refPoint.teamState.dateTime.getTime() + (Util.DAY_MILLIS * (ii + 1)) );
            if(date.getTime() > maxDate.getTime()) {
                date = maxDate;
                ii = toInject;
            }
            date.setHours(0);
            date.setMinutes(0);
            date.setSeconds(0, 0);
            const point = CharacterUtil.cloneMmrPoint(refPoint, date);
            injectArray.push(point);
        }
    }

    static createTeamSnapshot(team, dateTime, injected = false)
    {
        const teamState = 
        {
            teamId: team.id,
            dateTime: dateTime,
            divisionId: team.divisionId,
            wins: team.wins,
            games: team.wins + team.losses + team.ties,
            rating: team.rating,
        };
        if(!TeamUtil.isCheaterTeam(team) && !Util.isUndefinedRank(team.globalRank) )
        {
            teamState.globalRank = team.globalRank;
            teamState.globalTeamCount = team.globalTeamCount;
            teamState.globalTopPercent = (team.globalRank / team.globalTeamCount) * 100;
            teamState.regionRank = team.regionRank;
            teamState.regionTeamCount = team.regionTeamCount;
            teamState.regionTopPercent = (team.regionRank / team.regionTeamCount) * 100;
            teamState.leagueRank = team.leagueRank;
            teamState.leagueTeamCount = team.leagueTeamCount;
        }
        return {
            team: team,
            teamState: teamState,
            race: TeamUtil.getFavoriteRace(team.members[0]).name.toUpperCase(),
            league: {
                type: team.league.type,
                teamType: team.league.teamType,
                queueType: team.league.queueType
            },
            tier: team.tierType,
            season: team.season,
            generated: true,
            injected: injected
        };
    }

    static cloneMmrPoint(refPoint, dateTime)
    {
        const copy = Object.assign({}, refPoint);
        copy.teamState = Object.assign({}, copy.teamState,  {dateTime: dateTime});
        copy.generated = true;
        return copy;
    }

    static calculateFirstMmrDate()
    {
        const firstDateMax = new Date(Date.now() - Util.DAY_MILLIS * SC2Restful.MMR_HISTORY_DAYS_MAX);
        return SC2Restful.MMR_HISTORY_START_DATE.getTime() - firstDateMax.getTime() > 0
            ? SC2Restful.MMR_HISTORY_START_DATE : firstDateMax;
    }

    static getAdditionalMmrHistoryData(data, dataset, ix1, ix2)
    {
        const races = [];
        dataset.datasets.forEach(d=>races.push(d.label.toUpperCase()));
        races.sort((a, b)=>EnumUtil.enumOfName(a, RACE).order - EnumUtil.enumOfName(b, RACE).order);
        const race = races[ix2];
        const curData = Object.values(data)[ix1].find(d=>d.race == race);
        const lines = [];
        lines.push(curData.season);
        curData.tierType = curData.tier;
        lines.push(TeamUtil.createLeagueDiv(curData));
        lines.push(curData.teamState.rating);
        lines.push(CharacterUtil.createMmrHistoryGames(curData));
        CharacterUtil.appendAdditionalMmrHistoryRanks(curData, lines);
        return lines;
    }

    static createMmrHistoryGames(curData)
    {
        const container = document.createElement("span");
        container.innerHTML = curData.teamState.wins
            ? `${curData.teamState.games}<br/>(${Math.round((curData.teamState.wins / curData.teamState.games) * 100)}%)`
            : `${curData.teamState.games}<br/>-`;
        return container;
    }

    static appendAdditionalMmrHistoryRanks(curData, lines)
    {
        const globalRank = document.createElement("span");
        globalRank.classList.add("tooltip-mmr-rank");
        globalRank.innerHTML = curData.teamState.globalRank
            ? `${Util.NUMBER_FORMAT.format(curData.teamState.globalRank)}/${Util.NUMBER_FORMAT.format(curData.teamState.globalTeamCount)}<br/>
                (${Util.DECIMAL_FORMAT.format(curData.teamState.globalTopPercent)}%)`
            : "-"
        lines.push(globalRank);
        const regionRank = document.createElement("span");
        regionRank.classList.add("tooltip-mmr-rank");
        regionRank.innerHTML = curData.teamState.regionRank
            ? `${Util.NUMBER_FORMAT.format(curData.teamState.regionRank)}/${Util.NUMBER_FORMAT.format(curData.teamState.regionTeamCount)}<br/>
                (${Util.DECIMAL_FORMAT.format(curData.teamState.regionTopPercent)}%)`
            : "-"
        lines.push(regionRank);
        lines.push(CharacterUtil.createMMRHistoryLeagueRank(curData, lines));
    }

    static createMMRHistoryLeagueRank(curData, lines)
    {
        const elem = document.createElement("span");
        elem.classList.add("tooltip-mmr-rank");
        elem.innerHTML = curData.teamState.leagueRank
            ? `${Util.NUMBER_FORMAT.format(curData.teamState.leagueRank)}/${Util.NUMBER_FORMAT.format(curData.teamState.leagueTeamCount)}<br/>
                (${Util.DECIMAL_FORMAT.format((curData.teamState.leagueRank / curData.teamState.leagueTeamCount) * 100)}%)`
            : "-"
        return elem;
    }

    static filterMmrHistory(history, queueFilter, teamTypeFilter, excludeStart, excludeEnd)
    {
        let filtered = history.filter(h=>h.league.queueType == queueFilter && h.league.teamType == teamTypeFilter);
        if(excludeEnd > 0)
            filtered = filtered.filter(h=>h.teamState.rating < excludeStart || h.teamState.rating >= excludeEnd);
        return filtered;
    }

    static filterMmrHistoryBestRace(racialHistory)
    {
        if(racialHistory.length == 0) return [];
        let top = -1;
        let result = null;
        for(const [race, vals] of racialHistory.entries())
        {
            const curTop = vals.map(v=>v.teamState.rating).reduce((a, b) => Math.max(a, b));
            if(curTop > top)
            {
                top = curTop;
                result = race;
            }
        }
        for(const race of Object.values(RACE)) {
            const raceStr = race.name.toUpperCase();
            if(raceStr != result) racialHistory.delete(raceStr);
        }

        return racialHistory.get(result) ? racialHistory.get(result) : [];
    }

    static updateCharacterLinkedCharactersView(id)
    {
        const table = document.getElementById("linked-characters-table");
        for(const tr of table.querySelectorAll(":scope tr.active")) tr.classList.remove("active");
        const commonCharacter = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
        CharacterUtil.updateCharacters(table, commonCharacter.linkedDistinctCharacters);
        const activeCharAnchor = table.querySelector(':scope a[data-character-id="' + id + '"]');
        if(activeCharAnchor != null) activeCharAnchor.closest("tr").classList.add("active");
    }

    static updateCharacterMatchesView()
    {
        const tab = document.querySelector("#player-stats-matches-tab");
        const tabNav = tab.closest(".nav-item");
        const pane = document.querySelector("#player-stats-matches");
        const commonCharacter = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
        const characterId = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).id;
        const matches = commonCharacter.matches;

        tabNav.classList.remove("d-none");
        pane.classList.remove("d-none");
        const result = MatchUtil.updateMatchTable(document.querySelector("#matches"), matches,
            (data)=>Number.isInteger(data) ? data == characterId : data.member.character.id == characterId,
            localStorage.getItem("matches-historical-mmr") != "false"
        );
        Model.DATA.get(VIEW.CHARACTER).set(VIEW_DATA.TEAMS, {result: commonCharacter.teams ? commonCharacter.teams.concat(result.teams) : result.teams});
        if(result.validMatches.length >= MATCH_BATCH_SIZE) {
            document.querySelector("#load-more-matches").classList.remove("d-none");
        }
        else {
            document.querySelector("#load-more-matches").classList.add("d-none");
        }

        return Promise.resolve();
    }

    static findCharactersByName()
    {
        return CharacterUtil.updateCharacterSearch(document.getElementById("search-player-name").value);
    }

    static updateCharacterSearchModel(name)
    {
        const request = ROOT_CONTEXT_PATH + "api/character/search?term=" + encodeURIComponent(name);
        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(Session.verifyJsonResponse)
            .then(json => new Promise((res, rej)=>{
                Model.DATA.get(VIEW.CHARACTER_SEARCH).set(VIEW_DATA.SEARCH, json);
                Model.DATA.get(VIEW.CHARACTER_SEARCH).set(VIEW_DATA.VAR, name);
                res(json);
            }));
    }

    static updateCharacterSearchView()
    {
        CharacterUtil.updateCharacters(document.getElementById("search-table"),  Model.DATA.get(VIEW.CHARACTER_SEARCH).get(VIEW_DATA.SEARCH));
        document.getElementById("search-result-all").classList.remove("d-none");
        Util.scrollIntoViewById("search-result-all");
    }

    static updateCharacters(table, searchResult)
    {
        const tbody = table.getElementsByTagName("tbody")[0];
        ElementUtil.removeChildren(tbody);

        const includePrevious = localStorage.getItem("player-search-stats-include-previous") != "false";
        const grayOutPrevious = localStorage.getItem("player-search-stats-gray-out-previous") != "false";
        if(!includePrevious) searchResult.sort((a, b)=>{
            const ratingDiff = b.currentStats.rating - a.currentStats.rating;
            if(ratingDiff != 0) return ratingDiff;
            return b.ratingMax - a.ratingMax;
        })
        for(let i = 0; i < searchResult.length; i++)
        {
            const character = searchResult[i];
            const hasCurrentStats = character.currentStats.rating;
            const stats = includePrevious
                ? (hasCurrentStats ? character.currentStats : character.previousStats)
                : character.currentStats;
            const row = tbody.insertRow();
            row.insertCell().appendChild(ElementUtil.createImage("flag/", character.members.character.region.toLowerCase(), "table-image-long"));
            row.insertCell().appendChild(ElementUtil.createImage("league/", EnumUtil.enumOfId(character.leagueMax, LEAGUE).name, "table-image table-image-square mr-1"));
            row.insertCell().textContent = character.ratingMax;
            row.insertCell().textContent = character.totalGamesPlayed;
            CharacterUtil.insertSearchStats(row, stats, "rating", hasCurrentStats, grayOutPrevious);
            CharacterUtil.insertSearchStats(row, stats, "gamesPlayed", hasCurrentStats, grayOutPrevious);
            const membersCell = row.insertCell();
            membersCell.classList.add("complex", "cell-main");
            const mRow = document.createElement("span");
            mRow.classList.add("row", "no-gutters");
            const mInfo = TeamUtil.createMemberInfo(character, character.members);
            mInfo.getElementsByClassName("player-name-container")[0].classList.add("c-divider");
            const bTag = document.createElement("span");
            bTag.classList.add("c-divider", "battle-tag");
            bTag.textContent = character.members.account.battleTag;
            if(Util.isFakeBattleTag(character.members.account.battleTag)) bTag.classList.add("d-none");
            mInfo.getElementsByClassName("player-link-container")[0].appendChild(bTag);
            mRow.appendChild(mInfo);
            membersCell.appendChild(mRow);
            tbody.appendChild(row);
        }
    }

    static insertSearchStats(row, stats, key,  hasCurrentStats, grayOutPreviousSeason)
    {
        const cell = row.insertCell();
        if(grayOutPreviousSeason && !hasCurrentStats) cell.classList.add("text-secondary");
        cell.textContent = stats[key];
    }

    static loadNextMatches(evt)
    {
        evt.preventDefault();
        Util.setGeneratingStatus(STATUS.BEGIN);
        const commonCharacter = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
        const lastMatch = commonCharacter.matches[commonCharacter.matches.length - 1];
        CharacterUtil.loadNextMatchesModel(
            Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).id,
            lastMatch.match.date, lastMatch.match.type, lastMatch.map.id
        ).then(json => new Promise((res, rej)=>{
            if(json.result.length > 0) CharacterUtil.updateCharacterMatchesView();
            if(json.result.length < MATCH_BATCH_SIZE) document.querySelector("#load-more-matches").classList.add("d-none");
            Util.setGeneratingStatus(STATUS.SUCCESS);
            res();
         }))
         .catch(error => Session.onPersonalException(error));
    }

    static loadNextMatchesModel(id, dateAnchor, typeAnchor, mapAnchor)
    {
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/character/${id}/matches/${dateAnchor}/${typeAnchor}/${mapAnchor}/1/1${CharacterUtil.getMatchTypePath()}`))
            .then(Session.verifyJsonResponse)
            .then(json => new Promise((res, rej)=>{
                const commonCharacter = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
                commonCharacter.matches = commonCharacter.matches.concat(json.result);
                res(json);
            }));
    }

    static updateCharacterSearch(name)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        name = CharacterUtil.autoCompleteIfClanSearch(name);
        const searchParams = new URLSearchParams();
        searchParams.append("type", "search");
        searchParams.append("name", name);
        const stringParams = searchParams.toString();
        return CharacterUtil.updateCharacterSearchModel(name)
            .then(json => new Promise((res, rej)=>{
                CharacterUtil.updateCharacterSearchView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
                if(!Session.isHistorical) HistoryUtil.pushState({type: "search", name: name}, document.title, "?" + searchParams.toString() + "#search");
                Session.currentSearchParams = stringParams;
                res();
            }))
            .catch(error => Session.onPersonalException(error));
    }

    static autoCompleteIfClanSearch(name)
    {
        return name && name.startsWith("[") && !name.endsWith("]") ? name + "]" : name;
    }

    static updatePersonalCharactersModel()
    {
        return Session.beforeRequest()
            .then(n=>fetch(ROOT_CONTEXT_PATH + "api/my/characters"))
            .then(Session.verifyJsonResponse)
            .then(json => new Promise((res, rej)=>{
                Model.DATA.get(VIEW.PERSONAL_CHARACTERS).set(VIEW_DATA.SEARCH, json);
                res(json);
            }));
    }

    static updatePersonalCharactersView()
    {
        const personalCharTable = document.querySelector("#personal-characters-table");
        if(!personalCharTable) return;

        CharacterUtil.updateCharacters(personalCharTable, Model.DATA.get(VIEW.PERSONAL_CHARACTERS).get(VIEW_DATA.SEARCH));
    }

    static updatePersonalCharacters()
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return CharacterUtil.updatePersonalCharactersModel()
            .then(json => new Promise((res, rej)=>{
                CharacterUtil.updatePersonalCharactersView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
                res();
            }))
            .catch(error => Session.onPersonalException(error));
    }

    static updateFollowingCharactersView()
    {
        const table = document.querySelector("#following-characters-table");
        if(!table) return;

        CharacterUtil.updateCharacters(table, Model.DATA.get(VIEW.FOLLOWING_CHARACTERS).get(VIEW_DATA.SEARCH));
    }

    static enhanceSearchForm()
    {
        const form = document.getElementById("form-search");
        form.addEventListener("submit", function(evt)
            {
                evt.preventDefault();
                CharacterUtil.findCharactersByName();
            }
        );
        const searchInput = document.querySelector("#search-player-name");
        searchInput.addEventListener("input", CharacterUtil.onSearchInput);
        searchInput.addEventListener("keydown", (e)=>{
            if(!e.key) {
                const form = e.target.closest("form");
                window.setTimeout(t=>form.requestSubmit(form.querySelector(':scope [type="submit]"')), 1);
            }
        });

    }

    static enhanceMmrForm()
    {
        document.getElementById("mmr-queue-filter").addEventListener("change", evt=>CharacterUtil.updateCharacterMmrHistoryView());
        document.getElementById("mmr-depth").addEventListener("input",  CharacterUtil.onMmrInput);
        document.getElementById("mmr-exclude-start").addEventListener("input", CharacterUtil.onMmrInput);
        document.getElementById("mmr-exclude-end").addEventListener("input", CharacterUtil.onMmrInput);
        document.getElementById("mmr-best-race").addEventListener("change", evt=>CharacterUtil.updateCharacterMmrHistoryView());
        document.getElementById("mmr-season-last").addEventListener("change", evt=>CharacterUtil.updateCharacterMmrHistoryView());
        document.getElementById("mmr-y-axis").addEventListener("change", e=>{
            CharacterUtil.setMmrYAxis(e.target.value, e.target.getAttribute("data-chartable"));
            CharacterUtil.updateCharacterMmrHistoryView();
        });
        document.getElementById("mmr-x-type").addEventListener("change", e=>window.setTimeout(CharacterUtil.updateCharacterMmrHistoryView, 1));
        document.getElementById("mmr-leagues").addEventListener("change", e=>CharacterUtil.updateCharacterMmrHistoryView());
    }

    static setMmrYAxis(mode, chartable)
    {
        if(mode == "mmr" || mode == "win-rate-season") {
            ChartUtil.setNormalYAxis(chartable);
        } else {
            ChartUtil.setTopPercentYAxis(chartable);
        }
    }

    static onMmrInput(evt)
    {
        const prev = ElementUtil.INPUT_TIMEOUTS.get(evt.target.id);
        if(prev != null)  window.clearTimeout(prev);
        ElementUtil.INPUT_TIMEOUTS.set(evt.target.id, window.setTimeout(e=>CharacterUtil.updateCharacter(Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).id), ElementUtil.INPUT_TIMEOUT));
    }

    static enhanceMatchTypeInput()
    {
        const ctl = document.querySelector("#matches-type");
        if(!ctl) return;
        ctl.addEventListener("change", e=>window.setTimeout(e=>{
            const data = Model.DATA.get(VIEW.CHARACTER);
            if(!data || !data.get(VIEW_DATA.VAR)) return;
            CharacterUtil.updateCharacter(data.get(VIEW_DATA.VAR).id);
        }, 1));
    }

    static enhanceLoadMoreMatchesInput()
    {
        document.querySelector("#load-more-matches").addEventListener("click", CharacterUtil.loadNextMatches);
    }

    static enhanceAutoClanSearch()
    {
        for(const e of document.querySelectorAll(".clan-auto-search")) e.addEventListener("click", CharacterUtil.autoClanSearch);
    }

    static afterEnhance()
    {
        CharacterUtil.setMmrYAxis(document.getElementById("mmr-y-axis").value,
            document.getElementById("mmr-y-axis").getAttribute("data-chartable"));
    }

    static autoClanSearch(evt)
    {
        evt.preventDefault();
        const params = new URLSearchParams(evt.target.getAttribute("href").substring(0, evt.target.getAttribute("href").indexOf("#")));
        document.querySelector("#search-player-name").value = params.get("name");
        Session.isHistorical = true;
        return BootstrapUtil.hideActiveModal("error-generation")
            .then(r=>{Session.isHistorical = false; return CharacterUtil.findCharactersByName();})
            .then(r=>HistoryUtil.showAnchoredTabs())
            .then(r=>new Promise((res, rej)=>{window.scrollTo(0, 0); res();}));
    }

    static updateCharacterReportsView()
    {
        const reportsContainer = document.querySelector("#character-reports");
        const tbody = reportsContainer.querySelector(":scope #character-reports-table tbody");
        const reports = Model.DATA.get(VIEW.CHARACTER).get("reports");
        if(!reports || reports.length == 0) {
            reportsContainer.classList.add("d-none");
            return;
        }
        reportsContainer.classList.remove("d-none");
        CharacterUtil.updateCharacterReportsTable(tbody, reports);
        if(!reports.some(r=>r.report.status) && !document.querySelector("#player-info-additional-container .player-flag-reported"))
            document.querySelector("#player-info-additional-container").appendChild(ElementUtil.createCheaterFlag(CHEATER_FLAG.REPORTED, true));
    }

    static updateAllCharacterReportsView()
    {
        const reportsContainer = document.querySelector("#all-character-reports");
        const tbody = reportsContainer.querySelector(":scope #all-character-reports-table tbody");
        const reports = Model.DATA.get(VIEW.CHARACTER_REPORTS).get("reports");
        if(!reports || reports.length == 0) return;
        CharacterUtil.updateCharacterReportsTable(tbody, reports);
        Session.updateReportsNotifications();
    }

    static updateCharacterReportsTable(tbody, reports, removeChildren = true)
    {
        if(removeChildren) ElementUtil.removeChildren(tbody);
        for(const report of reports)
        {
            let tr = tbody.insertRow();

            const rowSpan = report.evidence ? report.evidence.length : 1;

            CharacterUtil.appendStatusCell(tr, report.report.status).setAttribute("rowspan", rowSpan);

            const playerCell = tr.insertCell();
            playerCell.setAttribute("rowspan", rowSpan);
            playerCell.appendChild(TeamUtil.createPlayerLink(null, report.member, false));
            const typeCell = tr.insertCell();
            typeCell.setAttribute("rowspan", rowSpan);
            typeCell.textContent = report.report.type;

            if(report.additionalMember)
            {
                const additionalPlayerCell = tr.insertCell();
                additionalPlayerCell.setAttribute("rowspan", rowSpan);
                additionalPlayerCell.appendChild(TeamUtil.createPlayerLink(null, report.additionalMember, false));
            } else
            {
                tr.insertCell().setAttribute("rowspan", rowSpan);
            }

            if(!report.evidence) continue;

            for(let i = 0; i < report.evidence.length; i++)
            {
                const evidence = report.evidence[i];
                if(i > 0) tr = tbody.insertRow();
                tr.setAttribute("data-report-id", report.report.id);
                tr.setAttribute("data-evidence-id", evidence.evidence.id);
                CharacterUtil.appendStatusCell(tr, evidence.evidence.status);

                const evidenceDescription = tr.insertCell();
                evidenceDescription.classList.add("cell-main", "text-break", "readable-text-block", "text-left");
                evidenceDescription.textContent = evidence.evidence.description;

                const reporter = tr.insertCell()
                reporter.textContent = evidence.reporterAccount ? evidence.reporterAccount.battleTag : "";
                reporter.classList.add("text-break");
                tr.insertCell().textContent = Util.DATE_TIME_FORMAT.format(Util.parseIsoDateTime(evidence.evidence.created));

                CharacterUtil.appendVotes(tr, evidence.votes);
            }
        }

        $(tbody.closest("table")).popover
        ({
            html: true,
            boundary: "body",
            placement: "auto",
            trigger: "hover",
            selector: '[data-toggle="popover"]',
            content: function(){return CharacterUtil.createDynamicVotersTable($(this)[0]).outerHTML;}
        });
    }
    
    static appendStatusCell(tr, status)
    {
        const statusCell = tr.insertCell();
        statusCell.classList.add("text-white", "font-weight-bold");
        if(status == true) {
            statusCell.classList.add("bg-success");
            statusCell.textContent = "Confirmed";
        } else if (status == false) {
            statusCell.classList.add("bg-danger");
            statusCell.textContent = "Denied";
        } else {
            statusCell.classList.add("bg-secondary");
            statusCell.textContent = "Undecided";
        }
        return statusCell;
    }

    static appendVotes(tr, votes)
    {
        CharacterUtil.appendVotesCell(tr, votes.filter(v=>v.vote.vote == true), "text-success", "bg-success", "true");
        CharacterUtil.appendVotesCell(tr, votes.filter(v=>v.vote.vote == false), "text-danger", "bg-danger", "false");
    }

    static appendVotesCell(tr, votes, textClass, bgClass, vote)
    {
        const votesCell = tr.insertCell();
        votesCell.setAttribute("data-toggle", "popover");
        votesCell.setAttribute("data-vote", vote);
        if(Session.currentAccount && votes.find(v=>v.vote.voterAccountId == Session.currentAccount.id)) {
            votesCell.classList.add("text-white", "font-weight-bold", bgClass);
        } else {
            votesCell.classList.add(textClass);
        }

        if(Session.currentRoles && Session.currentRoles.find(r=>r == "MODERATOR")) {
            votesCell.addEventListener("click", CharacterUtil.onEvidenceVote);
            votesCell.setAttribute("role", "button");
        }

        votesCell.textContent = votes.length;
    }

    static voteOnEvidence(id, vote)
    {
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/character/report/vote/${id}/${vote}`, Util.addCsrfHeader({method: "POST"})))
            .then(Session.verifyJsonResponse)
    }

    static onEvidenceVote(evt)
    {
        const td = evt.target;
        Util.setGeneratingStatus(STATUS.BEGIN);
        //remove popovers to avoid the popover bug on td removal
        document.querySelectorAll(".popover").forEach(e=>e.remove());
        CharacterUtil.voteOnEvidence(td.closest("tr").getAttribute("data-evidence-id"), td.getAttribute("data-vote"))
            .then(updatedVotes=>
            {
                const rows = document.querySelectorAll('[data-evidence-id="' + updatedVotes[0].vote.evidenceId + '"]');
                for(const row of rows)
                {
                    const evidence = Model.DATA.get(ViewUtil.getView(row)).get("reports")
                        .flatMap(r=>r.evidence)
                        .find(e=>e.evidence.id == updatedVotes[0].vote.evidenceId);
                    evidence.votes = updatedVotes;
                    row.children[row.children.length - 1].remove();
                    row.children[row.children.length - 1].remove();
                    CharacterUtil.appendVotes(row, evidence.votes);
                }
                Session.updateReportsNotifications();
                Util.setGeneratingStatus(STATUS.SUCCESS);
            })
            .catch(error => Session.onPersonalException(error));
    }

    static createDynamicVotersTable(parent)
    {
        const votersTable = TableUtil.createTable(["Date", "Moderator"], false);
        const tbody = votersTable.querySelector(":scope tbody");
        const row = parent.closest("tr");
        const reportId = row.getAttribute("data-report-id");
        const evidenceId = row.getAttribute("data-evidence-id");
        const vote = parent.getAttribute("data-vote") == "true" ? true : false;
        Model.DATA.get(ViewUtil.getView(parent)).get("reports")
            .find(r=>r.report.id == reportId).evidence
            .find(e=>e.evidence.id == evidenceId).votes
            .filter(v=>v.vote.vote == vote)
            .forEach(v=>{
                const tr = tbody.insertRow();
                tr.insertCell().textContent = Util.DATE_TIME_FORMAT.format(Util.parseIsoDateTime(v.vote.updated));
                tr.insertCell().textContent = v.voterAccount ? v.voterAccount.battleTag : "Anonymous";
            });
        return votersTable;
    }

    static enhanceReportForm()
    {
        document.querySelector("#report-character-type").addEventListener("change", e=>CharacterUtil.updateReportForm());
        $(document.querySelector("#report-character-modal"))
            .on("show.bs.modal", CharacterUtil.updateReportAlternativeCharacterList);
        document.querySelector("#report-character-form").addEventListener("submit", e=>{
            e.preventDefault();
            const fd = new FormData(document.querySelector("#report-character-form"));
            fd.set("playerCharacterId", Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).id);
            Util.setGeneratingStatus(STATUS.BEGIN);
            CharacterUtil.reportCharacter(fd)
                .then(e => Util.setGeneratingStatus(STATUS.SUCCESS))
                .catch(error => Session.onPersonalException(error));
        })
    }

    static updateReportAlternativeCharacterList()
    {
        const select = document.querySelector("#report-character-additional");
        select.querySelectorAll("option").forEach(o=>o.remove());
        for(const team of BufferUtil.teamBuffer.buffer.values()) {
            team.members.forEach(m=>
            {
                if(m.character.id == Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).id) return;

                const unmasked = Util.unmaskName(m);
                const option = document.createElement("option");
                option.textContent = (unmasked.unmaskedTeam ? "[" + unmasked.unmaskedTeam + "]" : "")
                    + unmasked.unmaskedName;
                option.value = m.character.id;
                select.appendChild(option);
            })
        }
    }

    static reportCharacter(fd)
    {
        return Session.beforeRequest()
           .then(n=>fetch(ROOT_CONTEXT_PATH + "api/character/report/new", Util.addCsrfHeader({method: "POST", body: fd})))
           .then(resp => {
                if (!resp.ok) {
                    let desc;
                    switch(resp.status)
                    {
                        case 429:
                            desc = "Daily report cap reached";
                            break;
                        case 409:
                            desc= "Confirmed evidence per report cap reached"
                            break
                        default:
                            desc = "";
                            break;
                    }
                    throw new Error(resp.status + " " + resp.statusText + " " + desc);
                }
                return Session.verifyJsonResponse(resp);
            })
            .then(e=>CharacterUtil.updateCharacterReportsModel())
            .then(e=>new Promise((res, rej)=>{
                $("#report-character-modal").modal('hide');
                CharacterUtil.updateCharacterReportsView();
                $("#character-reports").collapse('show');
                window.setTimeout(e=>Util.scrollIntoViewById("character-reports"), 500);
                res();
            }));
    }

    static updateReportForm()
    {
        const select = document.querySelector("#report-character-type");
        const additionalGroup = document.querySelector("#report-character-additional-group");
        const additionalInput = additionalGroup.querySelector(":scope #report-character-additional");
        if(select.value == "CHEATER") {
           additionalGroup.classList.add("d-none");
           additionalInput.setAttribute("disabled", "disabled");
        } else {
            additionalGroup.classList.remove("d-none");
            additionalInput.removeAttribute("disabled");
        }
    }

    static enhanceLoadAllCharacterReportsButton()
    {
        const button = document.querySelector("#load-all-character-reports");
        if(button) button.addEventListener("click", e=>CharacterUtil.updateAllCharacterReports());
    }

    static enhanceMatchesHistoricalMmrInput()
    {
        document.querySelector("#matches-historical-mmr").addEventListener("change",
            e=>window.setTimeout(CharacterUtil.updateCharacterMatchesView, 1));
    }

    static loadSearchSuggestions(term)
    {
        const reqTimestamp = Date.now();
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/character/search/suggestions?term=${encodeURIComponent(term)}`))
            .then(Session.verifyResponse)
            .then(resp=>Promise.all([resp.json(), Promise.resolve(reqTimestamp)]));
    }

    static updateSearchSuggestions(term)
    {
        if(!term) {
            document.querySelector("#search-player-suggestions").innerHTML = '';
            return Promise.resolve();
        } else
        {
            return CharacterUtil.loadSearchSuggestions(term)
                .then(resp=>{
                    const lastTs = ElementUtil.INPUT_TIMESTAMPS.get("search-player-suggestions");
                    if(!lastTs || lastTs < resp[1]) {
                        ElementUtil.INPUT_TIMESTAMPS.set("search-player-suggestions", resp[1]);
                        const dataList = ElementUtil.createDataList(resp[0]);
                        document.querySelector("#search-player-suggestions").innerHTML = dataList.innerHTML;
                    }
                });
        }
    }

    static onSearchInput(evt)
    {
        CharacterUtil.updateSearchSuggestions(CharacterUtil.shouldLoadSearchSuggestions(evt.target.value) ? evt.target.value : null);
    }

    static shouldLoadSearchSuggestions(term)
    {
        return term
            && ((term.startsWith("[") && term.length >= 2) || term.includes("#") || term.length >= 4);
    }

}
CharacterUtil.TEAM_SNAPSHOT_SEASON_END_OFFSET_MILLIS = 2 * 24 * 60 * 60 * 1000;
CharacterUtil.MMR_Y_VALUE_GETTERS = new Map([
    ["mmr", (history)=>history.teamState.rating],
    ["percent-global", (history)=>history.teamState.globalTopPercent],
    ["percent-region", (history)=>history.teamState.regionTopPercent],
    ["percent-league", (history)=>(history.teamState.leagueRank / history.teamState.leagueTeamCount) * 100],
    ["win-rate-season", (history)=>history.teamState.wins
        ? (history.teamState.wins / history.teamState.games) * 100
        : null
    ],
    ["default", (history)=>history.teamState.rating],
]);