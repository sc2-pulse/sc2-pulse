// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class CharacterUtil
{

    static setCharacterViewTasks()
    {
        ElementUtil.ELEMENT_TASKS.set("player-stats-characters-tab", e=>CharacterUtil.enqueueUpdateCharacterLinkedCharacters());
        ElementUtil.ELEMENT_TASKS.set("player-stats-summary-tab", e=>CharacterUtil.enqueueUpdateCharacterStats());
        ElementUtil.ELEMENT_TASKS.set("player-stats-player-tab", e=>CharacterUtil.enqueueUpdateCharacterLinks());
        ElementUtil.ELEMENT_TASKS.set("player-stats-matches-tab", e=>CharacterUtil.enqueueResetNextMatchesView());
        ElementUtil.infiniteScroll(document.querySelector("#player-stats-matches .container-indicator-loading-default"),
            e=>CharacterUtil.enqueueUpdateNextMatches());
        ElementUtil.ELEMENT_TASKS.set("player-stats-history-tab", e=>CharacterUtil.enqueueUpdateCharacterTeams());
        ElementUtil.ELEMENT_TASKS.set("player-stats-mmr-tab", e=>CharacterUtil.enqueueUpdateCharacterMmrHistoryAll());
    }

    static showCharacterInfo(e = null, explicitId = null, fullReload = true)
    {
        Util.resetLoadingIndicatorTree(document.querySelector("#player-info"));
        if (e != null) e.preventDefault();
        const id = explicitId || e.currentTarget.getAttribute("data-character-id");

        const promises = [];
        const searchParams = new URLSearchParams();
        searchParams.append("type", "character");
        searchParams.append("id", id);
        const stringParams = searchParams.toString();
        searchParams.append("m", "1");
        if(fullReload === true)
            promises.push(BootstrapUtil.hideActiveModal(["versus-modal", "player-info", "error-generation"]));
        promises.push(CharacterUtil.updateCharacter(id));

        return Promise.all(promises)
            .then(o=>{
                if(!Session.isHistorical && fullReload === true)
                    HistoryUtil.pushState({type: "character", id: id}, document.title,
                        "?" + searchParams.toString()
                        + "#" + document.querySelector("#player-stats-tabs .nav-link.active").id);
                Session.currentSearchParams = stringParams;
            })
            .then(e=>BootstrapUtil.showModal("player-info"));
    }

    static updateCharacterModel(id)
    {
        const params = new URLSearchParams();
        params.append("characterId", id);
        return GroupUtil.getCharacters(params)
            .then(characters => {
                if(!characters) throw Error("Character not found");

                Model.DATA.get(VIEW.CHARACTER).set(VIEW_DATA.VAR, characters[0]);
                Model.DATA.get(VIEW.CHARACTER).set(VIEW_DATA.SEARCH, {});
                return characters;
             });
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

    static resetCharacterReportsModel()
    {
        Model.DATA.get(VIEW.CHARACTER).delete("reports");
    }

    static resetCharacterReportsView()
    {
        ElementUtil.removeChildren(document.querySelector("#character-reports .character-reports"));
    }

    static resetCharacterReportsLoading()
    {
        Util.resetLoadingIndicator(document.querySelector("#character-reports"));
    }

    static resetCharacterReports(resetLoading = false)
    {
        CharacterUtil.resetCharacterReportsModel();
        CharacterUtil.resetCharacterReportsView();
        if(resetLoading) CharacterUtil.resetCharacterReportsLoading();
    }

    static getCharacterReports(ids)
    {
        const request = `${ROOT_CONTEXT_PATH}api/character/report/list/${ids.map(id=>encodeURIComponent(id)).join(",")}`;
        return Session.beforeRequest()
           .then(n=>fetch(request))
           .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static updateCharacterReportsModel()
    {
        return CharacterUtil.getCharacterReports(Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).linkedDistinctCharacters.map(c=>c.members.character.id))
            .then(reports => Model.DATA.get(VIEW.CHARACTER).set("reports", reports));
    }

    static updateCharacterReports()
    {
        CharacterUtil.resetCharacterReports();
        return CharacterUtil.enqueueUpdateCharacterLinkedCharacters()
            .then(result=>CharacterUtil.updateCharacterReportsModel())
            .then(reports=>{
                CharacterUtil.updateCharacterReportsView();
                return {data: reports, status: LOADING_STATUS.COMPLETE};
            });
    }

    static enqueueUpdateCharacterReports()
    {
        return Util.load(document.querySelector("#character-reports"), n=>CharacterUtil.updateCharacterReports());
    }

    static updateAllCharacterReportsModel(onlyUnreviewed = false)
    {
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/character/report/list`))
            .then(Session.verifyJsonResponse)
            .then(json => Model.DATA.get(VIEW.CHARACTER_REPORTS).set("reports", CharacterUtil.filterCharacterReports(json, onlyUnreviewed)));
    }

    static filterCharacterReports(reports, onlyUnreviewed = false)
    {
        if(!onlyUnreviewed || !Session.currentAccount) return reports;

        for(const report of reports) report.evidence = report.evidence
            .filter(evidence=>!evidence.votes.find(v=>v.vote.voterAccountId == Session.currentAccount.id));
        reports = reports.filter(r=>r.evidence.length > 0);

        return reports;
    }

    static updateAllCharacterReports(onlyUnreviewed = false)
    {
        if(!document.querySelector("#all-character-reports")) return Promise.resolve();

        Util.setGeneratingStatus(STATUS.BEGIN);
        return CharacterUtil.updateAllCharacterReportsModel(onlyUnreviewed)
            .then(e=>{
                CharacterUtil.updateAllCharacterReportsView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
            })
            .catch(error => Session.onPersonalException(error));
    }

    static resetCharacterTeamsModel()
    {
        const viewData = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
        delete viewData.teams;
        delete viewData.result;
    }

    static resetCharacterTeamsView()
    {
        ElementUtil.removeChildren(document.querySelector("#character-teams-table tbody"));
    }

    static resetCharacterTeamsLoading()
    {
        Util.resetLoadingIndicator(document.querySelector("#player-stats-history"));
    }

    static resetCharacterTeams(resetLoading = false)
    {
        CharacterUtil.resetCharacterTeamsModel();
        CharacterUtil.resetCharacterTeamsView();
        if(resetLoading) CharacterUtil.resetCharacterTeamsLoading();
    }

    static updateCharacterTeamsModel(characterId, season)
    {
        const params = new URLSearchParams();
        params.append("characterId", characterId);
        params.append("season", season);
        return GroupUtil.getTeams(params)
            .then(teams=>{
                const viewData = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
                viewData.teams = teams;
                viewData.result = teams;
                return teams;
            });
    }

    static updateCharacterTeams()
    {
        CharacterUtil.resetCharacterTeams();
        return CharacterUtil.updateCharacterTeamsModel(
            Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).members.character.id,
            document.querySelector("#teams-season-select").value
        )
            .then(teams=>{
                CharacterUtil.updateCharacterTeamsView()
                return {data: teams, status: LOADING_STATUS.COMPLETE};
            });
    }

    static enqueueUpdateCharacterTeams()
    {
         return Util.load(document.querySelector("#player-stats-history"), n=>CharacterUtil.updateCharacterTeams());
    }

    static updateCharacterTeamsView()
    {
        const searchData = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
        if(!searchData.teams) return;

        TeamUtil.updateTeamsTable(document.querySelector("#character-teams-table"), searchData);
    }
    
    static onCharacterTeamsSeasonChange(evt)
    {
        CharacterUtil.resetCharacterTeams(true);
        return CharacterUtil.enqueueUpdateCharacterTeams();
    }

    static enhanceCharacterTeamsSeasonCtl()
    {
        document.querySelector("#teams-season-select")
            .addEventListener("change", CharacterUtil.onCharacterTeamsSeasonChange);
    }

    static getCharacterUpdateTasks()
    {
        return CharacterUtil.CHARACTER_UPDATE_IDS.map(id=>ElementUtil.ELEMENT_TASK_QUEUE.get(id) || Promise.resolve());
    }

    static updateCharacter(id)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return Promise.allSettled(CharacterUtil.getCharacterUpdateTasks())
            .then(r=>CharacterUtil.updateCharacterModel(id))
            .then(jsons => {
                const fullChar = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR);
                CharacterUtil.updateCharacterInfoName(fullChar.members, id);
                CharacterUtil.updateFollowAccountCtl();
                CharacterUtil.updateCharacterGroupLink(
                    document.querySelector("#player-info .group-link"),
                    fullChar.members
                );
                for(const link of document.querySelectorAll(".character-link-follow-only[rel~=nofollow]")) link.relList.remove("nofollow");
                CharacterUtil.enqueueUpdateCharacterReports();
                Util.setGeneratingStatus(STATUS.SUCCESS);
            })
            .catch(error => Session.onPersonalException(error));
    }

    static resetAdditionalLinks()
    {
        Util.resetLoadingIndicator(document.querySelector("#character-links-section"));
        ElementUtil.executeTask("character-links-section", ()=>Model.DATA.get(VIEW.CHARACTER).delete("additionalLinks"));
    }

    static enqueueUpdateAdditionalCharacterLinks()
    {
        return Util.load(document.querySelector("#additional-link-loading"),
            n=>CharacterUtil.updateAdditionalCharacterLinks(Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).members.character.id));
    }

    static updateAdditionalCharacterLinks(id)
    {
        CharacterUtil.resetAdditionalLinks();
        document.querySelectorAll(".additional-link-container").forEach(e=>e.classList.add("d-none"));
        return CharacterUtil.updateAdditionalCharacterLinksModel(id)
            .then(linkResult => {
                CharacterUtil.updateAdditionalCharacterLinksView();
                const status = linkResult.failedTypes.length == 0 ? LOADING_STATUS.COMPLETE : LOADING_STATUS.ERROR;
                return {data: linkResult, status: status};
            })
            .catch(error => {
                CharacterUtil.updateAdditionalCharacterLinksView();
                throw error;
            });
    }

    static loadAdditionalCharacterLinks(id)
    {
        return Session.beforeRequest()
           .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/character/${encodeURIComponent(id)}/links/additional`))
           .then(resp=>Session.verifyJsonResponse(resp, [200, 404, 500]));
    }

    static updateAdditionalCharacterLinksModel(id)
    {
        return CharacterUtil.loadAdditionalCharacterLinks(id)
            .then(json => {
                Model.DATA.get(VIEW.CHARACTER).set("additionalLinks", json);
                return json;
            });
    }

    static updateAdditionalCharacterLinksView()
    {
        const result = Model.DATA.get(VIEW.CHARACTER).get("additionalLinks");
        document.querySelectorAll(".additional-link-container").forEach(e=>e.classList.add("d-none"));
        if(!result || !result.links || result.links.length == 0) return;
        document.querySelectorAll("#character-links .link-additional").forEach(e=>e.classList.add("d-none"));

        result.links.forEach(CharacterUtil.updateAdditionalLink);
    }

    static updateAdditionalLink(link)
    {
        switch(link.type) {
            case "BATTLE_NET":
                CharacterUtil.updateBattleNetProfileLink(link);
                break;
            case "REPLAY_STATS":
                CharacterUtil.updateReplayStatsProfileLink(link);
                break;
        }
    }

    static updateBattleNetProfileLink(link)
    {
        const linkElement = document.querySelector("#link-sc2-battle-net");
        linkElement.classList.remove("d-none");
        linkElement.querySelector(":scope span").textContent = link.absoluteUrl;
        linkElement.closest(".additional-link-container").classList.remove("d-none");
    }

    static updateReplayStatsProfileLink(link)
    {
        const linkElement = document.querySelector("#link-sc2-replay-stats");
        linkElement.classList.remove("d-none");
        linkElement.setAttribute("href", link.absoluteUrl + "?tab=replays");
        linkElement.closest(".additional-link-container").classList.remove("d-none");
    }

    static updateFollowAccountCtl()
    {
        if(Session.currentAccount != null && Session.currentFollowing != null)
        {
            const account = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).members.account;
            document.getElementById("player-info").setAttribute("data-account-id", account.id);
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
    }

    static enqueueUpdateCharacterLinks()
    {
        return Util.load(document.querySelector("#player-stats-player-loading"), n=>CharacterUtil.updateCharacterLinks());
    }

    static updateCharacterLinks()
    {
        return new Promise((res, rej)=>{
            CharacterUtil.updateCharacterMemberLinksView();
            res();
        })
            .then(e=>Promise.allSettled([
                CharacterUtil.enqueueUpdateAdditionalCharacterLinks(),
                CharacterUtil.enqueueUpdateCharacterProInfo(),
                CharacterUtil.enqueueUpdateCharacterLinkedExternalAccounts()])
            ).then(results=>{return {data: results, status: Util.getAllSettledLoadingStatus(results)};});
    }

    static updateCharacterMemberLinksView()
    {

        const fullChar = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR);
        const region = EnumUtil.enumOfName(fullChar.members.character.region, REGION);
        const profileLinkElement = document.getElementById("link-sc2");
        const profileSuffix = `/${region.code}/${fullChar.members.character.realm}/${fullChar.members.character.battlenetId}`;
        document.getElementById("link-sc2arcade").setAttribute("href", "https://sc2arcade.com/profile" + profileSuffix + "/lobbies-history");
        if(region == REGION.CN)
        {
            //the upstream site is not supporting the CN region.
            profileLinkElement.parentElement.classList.add("d-none");
        }
        else
        {
            profileLinkElement.setAttribute("href", "https://starcraft2.blizzard.com/profile" + profileSuffix);
            profileLinkElement.parentElement.classList.remove("d-none");
        }
        if(Util.isFakeBattleTag(fullChar.members.account.battleTag)) {
            document.querySelector("#link-battletag").classList.add("d-none");
        } else {
            document.querySelector("#link-battletag").classList.remove("d-none");
            document.querySelector("#link-battletag span").textContent = fullChar.members.account.battleTag;
        }
    }

    static resetCharacterLinkedExternalAccountsModel()
    {
        delete Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).linkedExternalAccounts;
    }

    static resetCharacterLinkedExternalAccountsView()
    {
        document.querySelector("#player-info .account.external").classList.add("d-none");
    }

    static resetCharacterLinkedExternalAccounts()
    {
        CharacterUtil.resetCharacterLinkedExternalAccountsModel();
        CharacterUtil.resetCharacterLinkedExternalAccountsView();
    }

    static getLinkedExternalAccounts(accountId)
    {
        const request = ROOT_CONTEXT_PATH + "api/account/" + encodeURIComponent(accountId) + "/linked/external/account";
        return Session.beforeRequest()
           .then(n=>fetch(request))
           .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static updateCharacterLinkedExternalAccountsModel(accountId)
    {
        return CharacterUtil.getLinkedExternalAccounts(accountId)
            .then(accounts=>{
                Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).linkedExternalAccounts = accounts;
                return accounts;
            });
    }

    static updateCharacterLinkedExternalAccounts()
    {
        CharacterUtil.resetCharacterLinkedExternalAccounts();
        const fullChar = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR);
        return CharacterUtil.updateCharacterLinkedExternalAccountsModel(fullChar.members.account.id)
            .then(accounts=>{
                CharacterUtil.updateCharacterLinkedExternalAccountsView();
                return {data: accounts, status: LOADING_STATUS.COMPLETE};
            });
    }

    static enqueueUpdateCharacterLinkedExternalAccounts()
    {
        return Util.load(document.querySelector("#linked-external-accounts"), n=>CharacterUtil.updateCharacterLinkedExternalAccounts());
    }

    static updateCharacterLinkedExternalAccountsView()
    {
        const accounts = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).linkedExternalAccounts;
        if(!accounts) return;

        if(!CharacterUtil.LINKED_EXTERNAL_ACCOUNT_UPDATERS)
            CharacterUtil.LINKED_EXTERNAL_ACCOUNT_UPDATERS = CharacterUtil.createCharacterLinkedExternalAccountUpdaters();
        for(const [accType, acc] of Object.entries(accounts))
        {
            const updater = CharacterUtil.LINKED_EXTERNAL_ACCOUNT_UPDATERS.get(accType);
            if(!updater) throw new Error("Updated for " + accType + " type not found");

            updater(acc);
        }
    }

    static createCharacterLinkedExternalAccountUpdaters()
    {
        const updaters = new Map();
        updaters.set("DISCORD", CharacterUtil.updateCharacterDiscordConnection);
        return updaters;
    }

    static updateCharacterDiscordConnection(discordUser)
    {
        const connectionElem = document.querySelector("#link-discord-connection");
        if(!discordUser) {
            connectionElem.classList.add("d-none");
        } else {
            connectionElem.querySelector(":scope .tag").textContent = discordUser.name
                + (discordUser.discriminator ? "#" + discordUser.discriminator : "");
            connectionElem.classList.remove("d-none");
        }
    }

    static resetCharacterProInfoModel()
    {
        delete Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).proPlayer;
    }

    static resetCharacterProInfoView()
    {
        for(const el of document.querySelectorAll(".pro-player-info")) el.classList.add("d-none");
    }

    static resetCharacterProInfo()
    {
        CharacterUtil.resetCharacterProInfoModel();
        CharacterUtil.resetCharacterProInfoView();
    }

    static updateCharacterProInfoModel(proPlayerId)
    {
        return RevealUtil.getPlayer(proPlayerId)
            .then(proPlayers=>{
                Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).proPlayer = proPlayers[0];
                return proPlayers[0];
            });
    }

    static updateCharacterProInfo()
    {
        CharacterUtil.resetCharacterProInfo();
        const fullMember = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR);
        if(!fullMember.members.proId) return Promise.resolve({data: null, status: LOADING_STATUS.COMPLETE});

        return CharacterUtil.updateCharacterProInfoModel(fullMember.members.proId)
            .then(proPlayer=>{
                CharacterUtil.doUpdateCharacterProInfo(proPlayer);
                return {data: proPlayer, status: LOADING_STATUS.COMPLETE};
            });
    }

    static enqueueUpdateCharacterProInfo()
    {
        return Util.load(document.querySelector("#pro-player-info"), n=>CharacterUtil.updateCharacterProInfo());
    }

    static doUpdateCharacterProInfo(proPlayer)
    {
        CharacterUtil.resetCharacterProInfoView();
        if(proPlayer == null) return;

        for(const link of document.querySelectorAll("#revealed-report [rel~=nofollow]")) link.relList.remove("nofollow");
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

    static updateCharacterInfoName(member)
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
            additionalClanElem.setAttribute("href", encodeURI(`${ROOT_CONTEXT_PATH}?type=group&clanId=${member.clan.id}#group-group`));
            additionalClanElem.classList.remove("d-none");
        } else {
            additionalClanElem.classList.add("d-none");
        }
        if(charClan) {
            clanElem.textContent = charClan;
            clanElem.setAttribute("href", encodeURI(`${ROOT_CONTEXT_PATH}?type=group&clanId=${member.clan.id}#group-group`));
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
        if(member.proNickname) additionalContainer.appendChild(ElementUtil.createProFlag());
    }

    static resetCharacterStats()
    {
        delete Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).stats;
        for(const statsSection of document.getElementsByClassName("player-stats-dynamic"))
            statsSection.classList.add("d-none");
    }

    static enqueueUpdateCharacterStats()
    {
        return Util.load(document.querySelector("#player-stats"), n=>CharacterUtil.updateCharacterStats());
    }

    static updateCharacterStats()
    {
        CharacterUtil.resetCharacterStats();
        const id = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).members.character.id;
        return CharacterUtil.updateCharacterStatsModel(id)
            .then(stats=>{
                CharacterUtil.updateCharacterStatsView();
                return {data: stats, status: LOADING_STATUS.COMPLETE};
            });
    }

    static getCharacterStats(id)
    {
        const request = ROOT_CONTEXT_PATH + "api/character/" + encodeURIComponent(id) + "/stats/full";
        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static updateCharacterStatsModel(id)
    {
        return CharacterUtil.getCharacterStats(id)
            .then(stats=>{
                Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).stats = stats;
                return stats;
            });
    }

    static updateCharacterStatsView()
    {
        for(const statsSection of document.getElementsByClassName("player-stats-dynamic")) statsSection.classList.add("d-none");
        const searchResult = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).stats;
        if(!searchResult) return;

        const includePrevious = localStorage.getItem("player-search-stats-include-previous") != "false";
        const grayOutPrevious = localStorage.getItem("player-search-stats-gray-out-previous") != "false";
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

    static createMmrHistoryIndex(history, historyColumn)
    {
        const historyLength = Object.values(history)[0].length;
        const index = {};
        for(let i = 0; i < historyLength; i++)
            index[history[historyColumn.fullName][i]] = i;
        return index;
    }

    static calculateMmrHistoryTimestampIndex(historyData)
    {
        historyData.forEach(d=>{
            d.timestampIndex = CharacterUtil.createMmrHistoryIndex(d.history, TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP);
        });
    }

    static calculateMmrHistoryStats(history)
    {
        return {
            length: history.map(h=>Object.values(h.history)[0].length).reduce((a, b) => a + b, 0)
        };
    }

    static recalculateCharacterMmrHistoryStats()
    {
        const mmrHistory = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory;
        if(!mmrHistory.history?.data) return;

        mmrHistory.history.stats = CharacterUtil.calculateMmrHistoryStats(mmrHistory.history.data);
    }

    static resetCharacterMmrHistoryFilteredData()
    {
        const mmrHistory = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory;
        mmrHistory.history.data = structuredClone(mmrHistory.history.originalData);
        CharacterUtil.recalculateCharacterMmrHistoryStats();
    }

    static resetCharacterMmrParameters()
    {
        delete Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH)?.mmrHistory?.parameters;
    }

    static resetCharacterMmrHistoryModel()
    {
        delete Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH)?.mmrHistory?.history;
    }

    static resetCharacterMmrHistoryView()
    {
        document.querySelector("#mmr-chart-container").classList.add("d-none");
    }

    static resetCharacterMmrHistory(resetLoading = false)
    {
        CharacterUtil.resetCharacterMmrHistoryModel();
        CharacterUtil.resetCharacterMmrHistoryView();
        if(resetLoading) Util.resetLoadingIndicator(document.querySelector("#mmr-history-loading"));
    }

    static resetCharacterMmrHistoryAll(resetLoading = false)
    {
        CharacterUtil.resetCharacterMmrHistory(resetLoading);
        CharacterUtil.resetCharacterMmrHistorySummary(resetLoading);
    }

    static updateCharacterMmrHistoryModel(ids, legacyUids, groupBy, from, to, staticColumns, historyColumns)
    {
        return TeamUtil.getHistory(ids, legacyUids, groupBy, from, to, staticColumns, historyColumns)
            .then(history=>{
                const dataHistory = {};
                Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory.history = dataHistory;
                dataHistory.data = history;
                if(history) {
                    CharacterUtil.calculateMmrHistoryTimestampIndex(history);
                    dataHistory.originalData = structuredClone(history);
                    CharacterUtil.recalculateCharacterMmrHistoryStats();
                    CharacterUtil.filterCharacterMmrHistory();
                }
                return dataHistory;
            });
    }

    static updateCharacterMmrHistoryView()
    {
        const mmrHistoryAll = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory;
        if(!mmrHistoryAll?.history?.data) return;

        const parameters = mmrHistoryAll.parameters;
        const mmrHistory = mmrHistoryAll.history;
        const fullChar = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR);
        const xAxisType = (localStorage.getItem("mmr-x-type") || "true") === "true" ? "time" : "category";

        const data = [];
        const rawData = {index: {}, history: {}};
        ChartUtil.CHART_RAW_DATA.set("mmr-table", {rawData: rawData, additionalDataGetter: CharacterUtil.getAdditionalMmrHistoryData});
        ChartUtil.batchExecute(
            "mmr-table",
            ()=>ChartUtil.setCustomConfigOption("mmr-table", "region", fullChar.members.character.region),
            false
        );
        const mmrYValueGetter = CharacterUtil.MMR_Y_VALUE_OPERATIONS.get(parameters.yAxis).get;
        for(const curHistory of mmrHistory.data) {
            const curHistoryLength = Object.values(curHistory.history)[0].length;
            const legacyIdData = TeamUtil.parseLegacyId(curHistory.staticData[TEAM_HISTORY_STATIC_COLUMN.LEGACY_ID.fullName]);
            const curHistoryRace = legacyIdData.race || CharacterUtil.ALL_RACE;
            rawData.history[curHistoryRace.name] = curHistory;
            for(let i = 0; i < curHistoryLength; i++) {
                const curTimestamp = curHistory.history[TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP.fullName][i];
                let curRow = data[curTimestamp];
                if(!curRow) {
                    curRow = {};
                    data[curTimestamp] = curRow;
                    rawData.index[curTimestamp] = {};
                }
                curRow[curHistoryRace.fullName] = mmrYValueGetter(curHistory, i);
                rawData.index[curTimestamp][curHistoryRace.name] = i;
            }
        }
        TableUtil.updateVirtualColRowTable
        (
            document.getElementById("mmr-table"),
            data,
            (tableData=>{
                if(parameters.showLeagues) CharacterUtil.decorateMmrPointsIndex(tableData, rawData);
                ChartUtil.CHART_RAW_DATA.get("mmr-table").data = tableData;
            }),
            parameters.queueData.queue == TEAM_FORMAT._1V1
                ? (a, b)=>EnumUtil.enumOfName(a, RACE).order - EnumUtil.enumOfName(b, RACE).order
                : null,
            parameters.queueData.queue == TEAM_FORMAT._1V1 ? (name)=>EnumUtil.enumOfName(name, RACE).name : (name)=>name.toLowerCase(),
            xAxisType == "time" ? dt=>parseInt(dt) * 1000 : dt=>Util.DATE_TIME_FORMAT.format(new Date(parseInt(dt) * 1000))
        );
        document.getElementById("mmr-history-filters").textContent =
            "(" + parameters.queueData.queue.name
            + (parameters.from ? ", " + Util.DATE_TIME_FORMAT.format(parameters.from)
                + " - " + Util.DATE_TIME_FORMAT.format(parameters.to) : "")
            + ", " + mmrHistory.stats.length  + " entries)";
        document.querySelector("#mmr-chart-container").classList.remove("d-none");
    }

    static filterMmrHistoryLastSeason(history)
    {
        if(!history[TEAM_HISTORY_HISTORY_COLUMN.SEASON.fullName]) return history;

        const names = Object.keys(history);
        const filtered = {};
        for(const name of names) filtered[name] = [];
        for(let i = 0; i < history[TEAM_HISTORY_HISTORY_COLUMN.SEASON.fullName].length; i++) {
            const nextSeason = i + 1 == history[TEAM_HISTORY_HISTORY_COLUMN.SEASON.fullName].length
                ? null
                : history[TEAM_HISTORY_HISTORY_COLUMN.SEASON.fullName][i + 1];
            const curSeason = history[TEAM_HISTORY_HISTORY_COLUMN.SEASON.fullName][i];
            if(curSeason != nextSeason) {
                for(const name of names) {
                    filtered[name].push(history[name][i]);
                }
            }
        }
        return filtered;
    }

    static calculateMmrHistoryMax(history, valueGetter, maxGetter)
    {
        const length = Object.values(history.history)[0].length;
        return maxGetter(Array.from(Array(length).keys()).map(i=>valueGetter(history, i)));
    }

    static filterMmrHistoryBestRace(histories, valueGetter, maxGetter, comparator)
    {
        if(histories.length == 1) return histories[0];

        return histories.map(h=>[h, CharacterUtil.calculateMmrHistoryMax(h, valueGetter, maxGetter)])
            .sort((a, b)=>comparator(a[1], b[1]))[0][0];
    }

    static filterCharacterMmrHistory()
    {
        const mmrHistory = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory;
        if(mmrHistory.parameters.endOfSeason)
            for(let i = 0; i < mmrHistory.history.data.length; i++)
                mmrHistory.history.data[i].history
                    = CharacterUtil.filterMmrHistoryLastSeason(mmrHistory.history.data[i].history);
        if(mmrHistory.parameters.bestRaceOnly) {
            const operations = CharacterUtil.MMR_Y_VALUE_OPERATIONS.get(mmrHistory.parameters.yAxis);
            mmrHistory.history.data = [CharacterUtil.filterMmrHistoryBestRace(
                mmrHistory.history.data,
                operations.get, operations.max, operations.compare)];
        }
        if(mmrHistory.parameters.endOfSeason || mmrHistory.parameters.bestRaceOnly)
            CharacterUtil.recalculateCharacterMmrHistoryStats();
        if(mmrHistory.parameters.endOfSeason && mmrHistory.history.data != null)
            CharacterUtil.calculateMmrHistoryTimestampIndex(mmrHistory.history.data);
    }

    static updateCharacterMmrHistory()
    {
        CharacterUtil.resetCharacterMmrHistory();
        CharacterUtil.setCharacterMmrParameters();
        const params = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory.parameters;

        const historyColumns = new Set(CharacterUtil.MMR_Y_REQUIRED_HISTORY_COLUMNS.get(params.yAxis));
        if(params.showLeagues) historyColumns.add(TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_TYPE);
        if(params.endOfSeason) historyColumns.add(TEAM_HISTORY_HISTORY_COLUMN.SEASON);
        params.historyColumns = historyColumns;
        return CharacterUtil.updateCharacterMmrHistoryModel(
            null,
            params.queueData.legacyUids,
            TEAM_HISTORY_GROUP_MODE.LEGACY_UID,
            params.from,
            params.to,
            [TEAM_HISTORY_STATIC_COLUMN.LEGACY_ID],
            historyColumns
        )
            .then(history=>{
                CharacterUtil.updateCharacterMmrHistoryView();
                return {data: history, status: LOADING_STATUS.COMPLETE};
            });
    }

    static setCharacterMmrParameters(rewrite = false)
    {
        const searchData = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
        if(!rewrite && searchData.mmrHistory.parameters != null) return false;

        searchData.mmrHistory.parameters = CharacterUtil.createCharacterMmrParameters();
        return true;
    }

    static getCharacterMmrQueueData()
    {
        const character = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).members.character;
        const region = EnumUtil.enumOfFullName(character.region, REGION);
        const member = {realm: character.realm, id: character.battlenetId};
        const queueFilterSelect = document.getElementById("mmr-queue-filter");
        const queue = EnumUtil.enumOfFullName(queueFilterSelect.options[queueFilterSelect.selectedIndex].value, TEAM_FORMAT);
        const teamType = queue == TEAM_FORMAT._1V1 ? TEAM_TYPE.ARRANGED : TEAM_TYPE.RANDOM;
        const legacyUids = queue == TEAM_FORMAT._1V1
            ? TeamUtil.createLegacyUidsForAllRaces(queue, teamType, region, member)
            : [TeamUtil.createLegacyUid(queue, teamType, region, TeamUtil.createLegacyIdSection(member))];
        return {
            queue: queue,
            teamType: teamType,
            legacyUids: legacyUids
        };
    }

    static createCharacterMmrParameters()
    {
        const depth = document.getElementById("mmr-depth").value || null;
        const to = depth ? new Date() : null;
        const from = depth ? (new Date(to.valueOf() - (depth * 24 * 60 * 60 * 1000))) : null;
        const yAxis = localStorage.getItem("mmr-y-axis") || "mmr";
        return {
            from: from,
            to: to,
            queueData: CharacterUtil.getCharacterMmrQueueData(),
            yAxis: yAxis,
            endOfSeason: (localStorage.getItem("mmr-season-last") || "false") === "true",
            showLeagues: (localStorage.getItem("mmr-leagues") || "false") === "true",
            bestRaceOnly: (localStorage.getItem("mmr-best-race") || "false") === "true"
        };
    }

    static enqueueUpdateCharacterMmrHistory()
    {
        return Util.load(document.querySelector("#mmr-history-loading"), n=>CharacterUtil.updateCharacterMmrHistory());
    }

    static resetUpdateCharacterMmrHistoryAllLoading()
    {
        Util.resetLoadingIndicator(document.querySelector("#mmr-history-all-loading"));
    }

    static updateCharacterMmrHistoryAll(resetParameters = true)
    {
        const searchData = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
        const oldParameters = searchData.mmrHistory?.parameters;
        searchData.mmrHistory = {};
        if(!resetParameters) searchData.mmrHistory.parameters = oldParameters;
        return Promise.allSettled([CharacterUtil.enqueueUpdateCharacterMmrHistory(),
            CharacterUtil.enqueueUpdateCharacterMmrHistorySummary()
        ])
            .then(results=>{
                Util.throwFirstSettledError(results);
                return {data: results, status: LOADING_STATUS.COMPLETE};
            });
    }

    static enqueueUpdateCharacterMmrHistoryAll(resetParameters = true)
    {
        return Util.load(document.querySelector("#mmr-history-all-loading"), n=>CharacterUtil.updateCharacterMmrHistoryAll(resetParameters));
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
        if(seasonLastOnly) return CharacterUtil.createTeamSnapshot(t, season.nowOrEnd);

        const date = (lastSeasonTeamSnapshotDates.get(t.season + 1) || Session.currentSeasonsMap.get(t.region).get(t.season + 1))
            ? ((lastSeasonTeamSnapshotDates.get(t.season) ? new Date(lastSeasonTeamSnapshotDates.get(t.season).getTime() + 1000)  : null)
                || new Date(season.nowOrEnd.getTime() - CharacterUtil.TEAM_SNAPSHOT_SEASON_END_OFFSET_MILLIS))
            : new Date();
        return CharacterUtil.createTeamSnapshot(t, date);
    }

    static calculateMmrHistoryTopPercentage(h)
    {
        if(h.globalTopPercent) return;
        h.teamState.globalTopPercent = (h.teamState.globalRank / h.teamState.globalTeamCount) * 100;
        h.teamState.regionTopPercent = (h.teamState.regionRank / h.teamState.regionTeamCount) * 100;
    }

    static decorateMmrPointsIndex(tableData, rawData)
    {
        const annotations = [];
        tableData.headers.forEach((header, datasetIx)=>{
            const rawHistory = rawData.history[header];
            const curAnnotations = [];
            annotations.push(curAnnotations);
            let prevLeague = null;
            Array.from(Object.values(rawData.index)).forEach((raw, valIx)=>{
                const rawIndex = raw[header];
                if(rawIndex == null) return;

                const leagueType = rawHistory.history[TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_TYPE.fullName][rawIndex];
                if(leagueType != prevLeague && Number.isFinite(tableData.values[datasetIx][valIx])) {
                    const annotation = {
                        name: "league-" + header + "-" + rawIndex,
                        type: "point",
                        pointStyle: SC2Restful.IMAGES.get(EnumUtil.enumOfId(leagueType, LEAGUE).name.toLowerCase()),
                        xValue: tableData.rowHeaders[valIx],
                        yValue: tableData.values[datasetIx][valIx]
                    }
                    curAnnotations.push(annotation);
                    prevLeague = leagueType;
                }
            });
        });
        tableData.dataAnnotations = annotations;
    }

    static decorateMmrPoints(tableData, rawData, headers, getter, injectLeague = true)
    {
        const annotations = [];
        tableData.headers.forEach((header, datasetIx)=>{
            const curAnnotations = [];
            annotations.push(curAnnotations);
            let prevLeague = null;
            rawData.forEach((raw, valIx)=>{
                const snapshot = getter(raw, header);
                if(!snapshot) return;

                const xValue = tableData.rowHeaders[valIx];
                if(snapshot.league.type != prevLeague && injectLeague
                    && Number.isFinite(tableData.values[datasetIx][valIx])) {
                        const annotation = {
                            name: "league-" + header + "-" + xValue,
                            type: "point",
                            pointStyle: SC2Restful.IMAGES.get(EnumUtil.enumOfId(snapshot.league.type, LEAGUE).name.toLowerCase()),
                            xValue: xValue,
                            yValue: tableData.values[datasetIx][valIx]
                        }
                        curAnnotations.push(annotation);
                        prevLeague = snapshot.league.type;
                }
            });
        });
        tableData.dataAnnotations = annotations;
    }

    static getGamesAndAverageMmrSortedArray(mmrHistory)
    {
        const gamesMmr = CharacterUtil.getGamesAndAverageMmr(mmrHistory);
        const entries = Object.entries(gamesMmr);
        entries.sort((a, b)=>b[1].maximumMmr - a[1].maximumMmr);
        return entries;
    }

    static addLegacyIdData(history)
    {
        history.legacyIdData = TeamUtil.parseLegacyId(history.staticData[TEAM_HISTORY_STATIC_COLUMN.LEGACY_ID.fullName]);
    }

    static resetCharacterMmrHistorySummaryNumericView()
    {
        ElementUtil.removeChildren(document.querySelector("#mmr-summary-table tbody"));
    }

    static resetCharacterMmrHistorySummaryProgressView()
    {
        ElementUtil.removeChildren(document.querySelector("#mmr-tier-progress-table tbody"));
    }

    static resetCharacterMmrHistorySummaryView()
    {
        CharacterUtil.resetCharacterMmrHistorySummaryNumericView();
        CharacterUtil.resetCharacterMmrHistorySummaryProgressView();
    }

    static resetCharacterMmrHistorySummaryModel()
    {
        delete Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory?.summary;
    }

    static resetCharacterMmrHistorySummaryLoading()
    {
        Util.resetLoadingIndicator(document.querySelector("#mmr-summary-table-container"));
    }

    static resetCharacterMmrHistorySummary(resetLoading = false)
    {
        CharacterUtil.resetCharacterMmrHistorySummaryModel();
        CharacterUtil.resetCharacterMmrHistorySummaryView();
        if(resetLoading) CharacterUtil.resetCharacterMmrHistorySummaryLoading();
    }

    static updateCharacterMmrHistorySummaryModel(ids, legacyUids, groupBy, from, to, staticColumns, summaryColumns)
    {
        return TeamUtil.getHistorySummary(ids, legacyUids, groupBy, from, to, staticColumns, summaryColumns)
            .then(summary=>{
                if(summary != null) {
                    summary.forEach(CharacterUtil.addLegacyIdData);
                    summary.sort((a, b)=>b.summary[TEAM_HISTORY_SUMMARY_COLUMN.RATING_MAX.fullName] -
                        a.summary[TEAM_HISTORY_SUMMARY_COLUMN.RATING_MAX.fullName]);
                }
                Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory.summary = summary;
                return summary;
            });
    }

    static updateCharacterMmrHistorySummaryNumericView()
    {
        const mmrHistory = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory;
        if(!mmrHistory.summary) return;

        CharacterUtil.updateGamesAndAverageMmrTable(
            document.querySelector("#mmr-summary-table"),
            mmrHistory.summary,
            mmrHistory.parameters.numericSummaryColumns
        );
    }

    static updateCharacterMmrHistorySummaryProgressView()
    {
        const mmrHistory = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory;
        if(!mmrHistory.summary) return;

        CharacterUtil.updateTierProgressTable(document.querySelector("#mmr-tier-progress-table"), mmrHistory.summary);
    }

    static updateCharacterMmrHistorySummaryView()
    {
        CharacterUtil.updateCharacterMmrHistorySummaryNumericView();
        CharacterUtil.updateCharacterMmrHistorySummaryProgressView();
    }

    static updateCharacterMmrHistorySummary()
    {
        CharacterUtil.resetCharacterMmrHistorySummary();
        CharacterUtil.setCharacterMmrParameters();
        const params = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory.parameters;

        params.numericSummaryColumns = new Set(CharacterUtil.MMR_Y_REQUIRED_NUMERIC_SUMMARY_COLUMNS.get(params.yAxis));
        params.progressSummaryColumns = new Set(CharacterUtil.MMR_REQUIRED_PROGRESS_SUMMARY_COLUMNS);
        params.summaryColumns = new Set([...params.numericSummaryColumns, ...params.progressSummaryColumns]);
        return CharacterUtil.updateCharacterMmrHistorySummaryModel(
            null,
            params.queueData.legacyUids,
            TEAM_HISTORY_GROUP_MODE.LEGACY_UID,
            params.from,
            params.to,
            [TEAM_HISTORY_STATIC_COLUMN.LEGACY_ID],
            params.summaryColumns
        )
            .then(history=>{
                CharacterUtil.updateCharacterMmrHistorySummaryView();
                return {data: history, status: LOADING_STATUS.COMPLETE};
            });
    }

    static enqueueUpdateCharacterMmrHistorySummary()
    {
        return Util.load(document.querySelector("#mmr-summary-table-container"), n=>CharacterUtil.updateCharacterMmrHistorySummary());
    }

    static updateMmrHistorySummaryTableBody(tbody, summaries, summaryColumns)
    {
        for(const summary of summaries) {
            const tr = tbody.insertRow();
            tr.insertCell().appendChild(ElementUtil.createRaceImage(summary.legacyIdData.race));
            for(const column of summaryColumns) {
                const val = summary.summary[column.fullName];
                const td = tr.insertCell();
                if(val != null) td.textContent = CharacterUtil.MMR_Y_SUMMARY_COLUMN_FORMATTERS.get(column)?.(val) || val;
            }
        }
    }

    static updateMmrHistorySummaryTableHeaders(thead, summaryColumns, removeChildren = true)
    {
        if(removeChildren) ElementUtil.removeChildren(thead);
        const thRow = thead.insertRow();
        TableUtil.createTh(thRow).textContent = "Race";
        for(const column of summaryColumns) TableUtil.createTh(thRow).textContent = column.textContent;
    }

    static updateGamesAndAverageMmrTable(table, summaries, columns)
    {
        const thead = table.querySelector(":scope thead");
        CharacterUtil.updateMmrHistorySummaryTableHeaders(thead, columns);
        if(!summaries) return;

        const tbody = table.querySelector(":scope tbody");
        CharacterUtil.updateMmrHistorySummaryTableBody(tbody, summaries, columns);
    }

    static updateTierProgressTable(table, summaries)
    {
        const tbody = table.querySelector(":scope tbody");
        ElementUtil.removeChildren(tbody);
        for(const summary of summaries)
        {
            const progress = CharacterUtil.createTierProgress(
                summary.summary[TEAM_HISTORY_SUMMARY_COLUMN.REGION_RANK_LAST.fullName],
                summary.summary[TEAM_HISTORY_SUMMARY_COLUMN.REGION_TEAM_COUNT_LAST.fullName],
            );
            if(!progress) continue;

            const tr = tbody.insertRow();
            tr.insertCell().appendChild(ElementUtil.createRaceImage(summary.legacyIdData.race));
            TableUtil.insertCell(tr, "cell-main").appendChild(progress);
        }
    }

    static createTierProgress(rank, teamCount)
    {
        if(teamCount == null || rank == null) return null;
        const tierRange = Util.getLeagueRange(rank, teamCount);
        let min, max, cur, nextTierRange;
        if(tierRange.league == LEAGUE.GRANDMASTER) {
            nextTierRange = {league: LEAGUE.GRANDMASTER, tierType: 0};
            min = SC2Restful.GM_COUNT;
            max = 1;
            cur = rank;
        } else {
            nextTierRange = tierRange.league == LEAGUE.MASTER && tierRange.tierType == 0
                ? CharacterUtil.getGrandmasterTierRange(teamCount)
                : TIER_RANGE[tierRange.order - 1];
            min = tierRange.bottomThreshold;
            max = nextTierRange.bottomThreshold;
            cur = (rank / teamCount) * 100;
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
            && Session.currentSeasonsMap.get(t.season)[0].nowOrEnd.getTime() > firstDate.getTime()
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
        const lastPointMaxDateTime = Session.currentSeasonsMap.get(lastPoint.season)[0].nowOrEnd;
        if(lastPoint.teamState.dateTime.getTime() < lastPointMaxDateTime.getTime())
             curInjected.push(CharacterUtil.cloneMmrPoint(lastPoint, lastPointMaxDateTime));
        Array.prototype.push.apply(injected, curInjected);
        Array.prototype.push.apply(history, curInjected);
    }

    static injectMmrPoints(history, injectArray, refPoint, toInject)
    {
        const maxDate = Session.currentSeasonsMap.get(refPoint.season)[0].nowOrEnd;
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

    static copyMmrHistory(src, dest)
    {
        const srcKeys = Object.keys(src.history);
        const srcLength = Object.values(src.history)[0].length;
        const copiedTimestamps = [];
        for(let srcIx = 0; srcIx < srcLength; srcIx++) {
            const srcTimestamp = src.history[TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP.fullName][srcIx];
            const destIx = dest.timestampIndex[srcTimestamp];
            if(destIx == null) continue;

            for(const srcKey of srcKeys){
                if(!dest.history[srcKey]) dest.history[srcKey] = new Array(srcLength);
                dest.history[srcKey][destIx] = src.history[srcKey][srcIx];
            }
            copiedTimestamps.push(srcTimestamp);
        }
        return copiedTimestamps;
    }

    static isMmrHistoryEntryComplete(historyData, index)
    {
        return historyData.history[TEAM_HISTORY_HISTORY_COLUMN.GLOBAL_TEAM_COUNT.fullName]?.[index] != null
            || historyData?.completeTimestamps?.has(historyData.history[TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP.fullName][index]);
    }

    static updateCharacterMmrHistoryWithCompleteData(from)
    {
        const mmrHistory = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory;
        const to = new Date(from.valueOf() + 1000);

        return TeamUtil.getHistory(null,
            mmrHistory.parameters.queueData.legacyUids,
            TEAM_HISTORY_GROUP_MODE.LEGACY_UID,
            from, to,
            [TEAM_HISTORY_STATIC_COLUMN.LEGACY_ID],
            Object.values(TEAM_HISTORY_HISTORY_COLUMN)
        )
            .then(historyArray=>{
                historyArray.forEach(history=>{
                    const existingHistory = mmrHistory.history.data.find(h=>h.staticData[TEAM_HISTORY_STATIC_COLUMN.LEGACY_ID.fullName]
                        === history.staticData[TEAM_HISTORY_STATIC_COLUMN.LEGACY_ID.fullName]);
                    if(existingHistory != null) {
                        if(existingHistory.completeTimestamps == null) existingHistory.completeTimestamps = new Set();
                        CharacterUtil.copyMmrHistory(history, existingHistory).forEach(ts=>existingHistory.completeTimestamps.add(ts));
                    }
                });
            });
    }

    static requeueUpdateCharacterMmrHistoryWithCompleteData(from, then)
    {
        return ElementUtil.clearAndSetInputTimeout(CharacterUtil.MMR_HISTORY_COMPLETE_POINT_TASK_NAME,
            then != null
                ? ()=>CharacterUtil.updateCharacterMmrHistoryWithCompleteData(from).then(then)
                : ()=>CharacterUtil.updateCharacterMmrHistoryWithCompleteData(from),
            CharacterUtil.MMR_HISTORY_COMPLETE_POINT_TIMEOUT);
    }

    static getAdditionalMmrHistoryData(data, dataset, ix1, ix2)
    {
        const header = dataset.datasets[ix2].label;
        const historyData = data.history[header];
        const history = historyData.history;
        const historyIx = Object.values(data.index)[ix1][header];
        const from = new Date(history[TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP.fullName][historyIx] * 1000);
        ElementUtil.clearInputTimeout(CharacterUtil.MMR_HISTORY_COMPLETE_POINT_TASK_NAME);
        if(!CharacterUtil.isMmrHistoryEntryComplete(historyData, historyIx))
            CharacterUtil.requeueUpdateCharacterMmrHistoryWithCompleteData(from, ()=>ChartUtil.CHARTS.get("mmr-table").tooltip.update(true, false));
        const lines = [];
        lines.push(history[TEAM_HISTORY_HISTORY_COLUMN.SEASON.fullName]?.[historyIx] || CharacterUtil.MMR_HISTORY_PLACEHOLDER);
        lines.push(CharacterUtil.createMmrHistoryLeague(
            history[TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_TYPE.fullName]?.[historyIx],
            history[TEAM_HISTORY_HISTORY_COLUMN.TIER_TYPE.fullName]?.[historyIx]
        ));
        lines.push(history[TEAM_HISTORY_HISTORY_COLUMN.RATING.fullName]?.[historyIx] || CharacterUtil.MMR_HISTORY_PLACEHOLDER);
        lines.push(CharacterUtil.createMmrHistoryGames(
            history[TEAM_HISTORY_HISTORY_COLUMN.GAMES.fullName]?.[historyIx],
            history[TEAM_HISTORY_HISTORY_COLUMN.WINS.fullName]?.[historyIx]
        ));
        lines.push(CharacterUtil.createMmrHistoryRank(
            history[TEAM_HISTORY_HISTORY_COLUMN.GLOBAL_RANK.fullName]?.[historyIx],
            history[TEAM_HISTORY_HISTORY_COLUMN.GLOBAL_TEAM_COUNT.fullName]?.[historyIx]
        ));
        lines.push(CharacterUtil.createMmrHistoryRank(
            history[TEAM_HISTORY_HISTORY_COLUMN.REGION_RANK.fullName]?.[historyIx],
            history[TEAM_HISTORY_HISTORY_COLUMN.REGION_TEAM_COUNT.fullName]?.[historyIx]
        ));
        lines.push(CharacterUtil.createMmrHistoryRank(
            history[TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_RANK.fullName]?.[historyIx],
            history[TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_TEAM_COUNT.fullName]?.[historyIx]
        ));
        return lines;
    }

    static createMmrHistoryLeague(leagueType, tierType)
    {
        if(leagueType == null) return CharacterUtil.MMR_HISTORY_PLACEHOLDER;

        return TeamUtil.createLeagueDiv({league: {type: leagueType}, tierType: tierType});
    }
    
    static createMmrTooltipPlaceholder(levels = 2, classes)
    {
        const container = document.createElement("span");
        container.appendChild(document.createTextNode(CharacterUtil.MMR_HISTORY_PLACEHOLDER));
        for(let i = 0; i < levels - 1; i++) {
            container.appendChild(document.createElement("br"));
            container.appendChild(document.createTextNode(CharacterUtil.MMR_HISTORY_PLACEHOLDER));
        }
        if(classes) classes.forEach(clazz=>container.classList.add(clazz));
        return container;
    }

    static createMmrHistoryGames(games, wins)
    {
        if(games == null) return CharacterUtil.createMmrTooltipPlaceholder();

        const content = games + "</br>" + (wins != null ? `${Math.round((wins / games) * 100)}%` : CharacterUtil.MMR_HISTORY_PLACEHOLDER);
        const container = document.createElement("span");
        container.innerHTML = content;
        return container;
    }

    static createMmrHistoryRank(rank, teamCount)
    {
        if(rank == null || teamCount == null) return CharacterUtil.createMmrTooltipPlaceholder(2, ["tooltip-mmr-rank"]);

        const rankElem = document.createElement("span");
        rankElem.classList.add("tooltip-mmr-rank");
        rankElem.innerHTML = `${Util.NUMBER_FORMAT.format(rank)}/${Util.NUMBER_FORMAT.format(teamCount)}<br/>
            (${Util.DECIMAL_FORMAT.format((rank / teamCount) * 100)}%)`;
        return rankElem;
    }

    static createMmrHistoryGamesFromTeamState(curData)
    {
        return CharacterUtil.createMmrHistoryGames(curData.teamState.games, curData.teamState.wins);
    }

    static appendAdditionalMmrHistoryRanks(curData, lines)
    {
        lines.push(CharacterUtil.createMmrHistoryRank(curData.teamState.globalRank, curData.teamState.globalTeamCount));
        lines.push(CharacterUtil.createMmrHistoryRank(curData.teamState.regionRank, curData.teamState.regionTeamCount));
        lines.push(CharacterUtil.createMmrHistoryRank(curData.teamState.leagueRank, curData.teamState.leagueTeamCount));
    }

    static updateCharacterLinkedCharacters()
    {
        CharacterUtil.resetCharacterLinkedCharacters();
        const fullChar = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR);
        return CharacterUtil.updateCharacterLinkedCharactersModel(fullChar.members)
            .then(chars=>{
                CharacterUtil.updateCharacterLinkedCharactersView();
                return {data: chars, status: LOADING_STATUS.COMPLETE};
            });
    }

    static enqueueUpdateCharacterLinkedCharacters()
    {
        return Util.load(document.querySelector("#player-stats-characters"), n=>CharacterUtil.updateCharacterLinkedCharacters());
    }

    static resetCharacterLinkedCharacters()
    {
        delete Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).linkedDistinctCharacters;
        ElementUtil.removeChildren(document.querySelector("#linked-characters-table tbody"));
    }

    static updateCharacterLinkedCharactersModel(member)
    {
        const params = CharacterUtil.createTopCharacterGroupIdParameters(member);
        return GroupUtil.getCharacters(params)
            .then(chars=>Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).linkedDistinctCharacters = chars);
    }

    static updateCharacterLinkedCharactersView()
    {
        const table = document.getElementById("linked-characters-table");
        for(const tr of table.querySelectorAll(":scope tr.active")) tr.classList.remove("active");
        const commonCharacter = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
        if(!commonCharacter.linkedDistinctCharacters) return;

        const fullChar = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR);
        CharacterUtil.updateCharacters(table, commonCharacter.linkedDistinctCharacters);
        const activeCharAnchor = table.querySelector(':scope a[data-character-id="' + fullChar.members.character.id + '"]');
        if(activeCharAnchor != null) activeCharAnchor.closest("tr").classList.add("active");
    }

    static updateCharacterMatchesView()
    {
        const tab = document.querySelector("#player-stats-matches-tab");
        const tabNav = tab.closest(".nav-item");
        const pane = document.querySelector("#player-stats-matches");
        const commonCharacter = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
        const characterId = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).members.character.id;
        const matches = commonCharacter.matches;

        tabNav.classList.remove("d-none");
        pane.classList.remove("d-none");
        const result = MatchUtil.updateMatchTable(document.querySelector("#matches"), matches,
            (data)=>Number.isInteger(data) ? data == characterId : data.member.character.id == characterId,
            localStorage.getItem("matches-historical-mmr") != "false"
        );
        Model.DATA.get(VIEW.CHARACTER).set(VIEW_DATA.TEAMS, {result: commonCharacter.teams ? commonCharacter.teams.concat(result.teams) : result.teams});

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
            .then(json => {
                Model.DATA.get(VIEW.CHARACTER_SEARCH).set(VIEW_DATA.SEARCH, json);
                Model.DATA.get(VIEW.CHARACTER_SEARCH).set(VIEW_DATA.VAR, name);
                return json;
            });
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
            const leagueCell = row.insertCell();
            if(character.leagueMax != null) leagueCell.appendChild(ElementUtil.createImage("league/", EnumUtil.enumOfId(character.leagueMax, LEAGUE).name, "table-image table-image-square mr-1"));
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

    static resetNextMatchesModel()
    {
        const data = Model.DATA.get(VIEW.CHARACTER);
        if(!data || !data.get(VIEW_DATA.VAR)) return;

        delete Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).matches;
    }

    static resetNextMatchesView()
    {
        ElementUtil.removeChildren(document.querySelector("#matches tbody"));
    }

    static resetNextMatches()
    {
        CharacterUtil.resetNextMatchesModel();
        CharacterUtil.resetNextMatchesView();
        Util.resetLoadingIndicator(document.querySelector("#player-stats-matches"));
    }

    static enqueueResetNextMatchesView()
    {
        return Util.load(document.querySelector("#player-stats-matches-reset-loading"), n=>{
            CharacterUtil.resetNextMatchesView();
            return Promise.resolve({data: null, status: LOADING_STATUS.COMPLETE});
        });
    }

    static enqueueUpdateNextMatches()
    {
        return Util.load(document.querySelector("#player-stats-matches"), n=>CharacterUtil.updateNextMatches());
    }

    static updateNextMatches()
    {
        const commonCharacter = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
        if(!commonCharacter.matches) CharacterUtil.resetNextMatchesView();
        const lastMatch = commonCharacter.matches ? commonCharacter.matches[commonCharacter.matches.length - 1] : null;
        let type = localStorage.getItem("matches-type") || "all";
        if(type == "all") type = null;
        return CharacterUtil.updateNextMatchesModel(
            Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).members.character.id,
            type,
            lastMatch?.match.date, lastMatch?.match.type, lastMatch?.map.id
        ).then(matches => {
            if(matches && matches.length > 0) CharacterUtil.updateCharacterMatchesView();
            const cursorIsComplete = !matches || matches.length < MATCH_BATCH_SIZE;
            return {data: matches, status: cursorIsComplete ? LOADING_STATUS.COMPLETE : LOADING_STATUS.NONE};
         });
    }

    static updateNextMatchesModel(id, type, dateCursor, typeCursor, mapCursor)
    {
        const params = new URLSearchParams();
        params.append("characterId", id);
        if(type) params.append("type", type);
        if(dateCursor) params.append("dateCursor", dateCursor);
        if(typeCursor) params.append("typeCursor", typeCursor);
        if(mapCursor) params.append("mapCursor", mapCursor);
        return GroupUtil.getMatches(params)
            .then(matches => {
                if(matches) {
                    const commonCharacter = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
                    commonCharacter.matches = commonCharacter.matches ? commonCharacter.matches.concat(matches) : matches;
                }
                return matches;
            });
    }

    static updateCharacterSearch(name)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        name = name.trim();
        name = CharacterUtil.autoCompleteIfClanSearch(name);
        const searchParams = new URLSearchParams();
        searchParams.append("type", "search");
        searchParams.append("name", name);
        const stringParams = searchParams.toString();
        return CharacterUtil.updateCharacterSearchModel(name)
            .then(json => {
                CharacterUtil.updateCharacterSearchView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
                if(!Session.isHistorical) HistoryUtil.pushState({type: "search", name: name}, document.title, "?" + searchParams.toString() + "#search");
                Session.currentSearchParams = stringParams;
            })
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
            .then(json => {
                Model.DATA.get(VIEW.PERSONAL_CHARACTERS).set(VIEW_DATA.SEARCH, json);
                return json;
            });
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
            .then(json => {
                CharacterUtil.updatePersonalCharactersView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
            })
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
        document.getElementById("mmr-queue-filter").addEventListener("change", e=>window.setTimeout(evt=>CharacterUtil.onMmrHistoryQueueChange(e), 0));
        document.getElementById("mmr-depth").addEventListener("input",  CharacterUtil.onMmrHistoryDepthChange);
        document.getElementById("mmr-best-race").addEventListener("change", e=>window.setTimeout(evt=>CharacterUtil.onMmrHistoryBestRaceOnlyChange(e), 0));
        document.getElementById("mmr-season-last").addEventListener("change", e=>window.setTimeout(evt=>CharacterUtil.onMmrHistoryEndOfSeasonChange(e), 0));
        document.getElementById("mmr-y-axis").addEventListener("change", e=>window.setTimeout(evt=>{
            CharacterUtil.setMmrYAxis(e.target.value, e.target.getAttribute("data-chartable"));
            CharacterUtil.onMmrHistoryYAxisChange(e);
        }, 0));
        document.getElementById("mmr-x-type").addEventListener("change", e=>window.setTimeout(evt=>CharacterUtil.updateCharacterMmrHistoryView(), 0));
        document.getElementById("mmr-leagues").addEventListener("change", e=>window.setTimeout(evt=>CharacterUtil.onMmrHistoryShowLeaguesChange(e), 0));
    }

    static onMmrHistoryShowLeaguesChange(evt)
    {
        const mmrHistory = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory;
        mmrHistory.parameters.showLeagues = (localStorage.getItem("mmr-leagues") || "false") === "true";
        if(!mmrHistory.parameters.historyColumns.has(TEAM_HISTORY_HISTORY_COLUMN.LEAGUE_TYPE)) {
            CharacterUtil.reloadCharacterMmrHistory();
        } else {
            CharacterUtil.updateCharacterMmrHistoryView();
        }
    }

    static onMmrHistoryEndOfSeasonChange(evt)
    {
        const mmrHistory = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory;
        mmrHistory.parameters.endOfSeason = (localStorage.getItem("mmr-season-last") || "false") === "true";
        if(!mmrHistory.parameters.historyColumns.has(TEAM_HISTORY_HISTORY_COLUMN.SEASON)) {
            CharacterUtil.reloadCharacterMmrHistory();
        } else {
            CharacterUtil.refilterCharacterMmrHistory();
        }
    }

    static onMmrHistoryBestRaceOnlyChange(evt)
    {
        const mmrHistory = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory;
        mmrHistory.parameters.bestRaceOnly = (localStorage.getItem("mmr-best-race") || "false") === "true";
        CharacterUtil.refilterCharacterMmrHistory();
    }

    static refilterCharacterMmrHistory()
    {
        CharacterUtil.resetCharacterMmrHistoryFilteredData();
        CharacterUtil.filterCharacterMmrHistory();
        CharacterUtil.updateCharacterMmrHistoryView();
    }

    static onMmrHistoryYAxisChange(evt)
    {
        const mmrHistory = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory;
        const yAxis = localStorage.getItem("mmr-y-axis") || "mmr";
        mmrHistory.parameters.yAxis = yAxis;
        const requiredColumns = new Set(CharacterUtil.MMR_Y_REQUIRED_HISTORY_COLUMNS.get(yAxis));
        const loadedColumns = Object.keys(mmrHistory.history.data[0].history);
        if(Array.from(requiredColumns.values()).every(c=>loadedColumns.includes(c))) {
            CharacterUtil.refilterCharacterMmrHistory();
        } else {
            CharacterUtil.reloadCharacterMmrHistory();
        }
    }

    static reloadCharacterMmrHistory(resetParameters = false)
    {
        CharacterUtil.resetCharacterMmrHistory(true);
        CharacterUtil.setCharacterMmrParameters(resetParameters);
        return CharacterUtil.enqueueUpdateCharacterMmrHistory();
    }

    static reloadCharacterMmrHistoryAll(resetParameters = false)
    {
        CharacterUtil.resetCharacterMmrHistoryAll(true)
        CharacterUtil.resetUpdateCharacterMmrHistoryAllLoading();
        return CharacterUtil.enqueueUpdateCharacterMmrHistoryAll(resetParameters);
    }

    static onMmrHistoryQueueChange(evt)
    {
        Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH).mmrHistory.parameters.queueData
            = CharacterUtil.getCharacterMmrQueueData();
        CharacterUtil.reloadCharacterMmrHistoryAll();
    }

    static onMmrHistoryDepthChange(evt)
    {
        const prev = ElementUtil.INPUT_TIMEOUTS.get(evt.target.id);
        if(prev != null)  window.clearTimeout(prev);
        ElementUtil.INPUT_TIMEOUTS.set(evt.target.id, window.setTimeout(e=>CharacterUtil.reloadCharacterMmrHistoryAll(true), ElementUtil.INPUT_TIMEOUT));
    }

    static setMmrYAxis(mode, chartable)
    {
        if(mode == "mmr") {
            ChartUtil.setNormalYAxis(chartable);
        } else {
            ChartUtil.setTopPercentYAxis(chartable);
        }
    }

    static onMmrInput(evt)
    {
        const prev = ElementUtil.INPUT_TIMEOUTS.get(evt.target.id);
        if(prev != null)  window.clearTimeout(prev);
        ElementUtil.INPUT_TIMEOUTS.set(evt.target.id, window.setTimeout(e=>CharacterUtil.updateCharacter(Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).members.character.id), ElementUtil.INPUT_TIMEOUT));
    }

    static enhanceMatchTypeInput()
    {
        const ctl = document.querySelector("#matches-type");
        if(!ctl) return;
        ctl.addEventListener("change", e=>window.setTimeout(e=>{
            const data = Model.DATA.get(VIEW.CHARACTER);
            if(!data || !data.get(VIEW_DATA.VAR)) return;
            CharacterUtil.resetNextMatches();
            CharacterUtil.enqueueUpdateNextMatches();
        }, 1));
    }

    static enhanceAutoClanSearch()
    {
        for(const e of document.querySelectorAll(".clan-auto-search")) e.addEventListener("click", GroupUtil.onGroupLinkClick);
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
            .then(r=>window.scrollTo(0, 0));
    }

    static createTopCharacterGroupIdParameters(member)
    {
        const params = new URLSearchParams();
        if(member.proId != null) {
            params.append("proPlayerId", member.proId);
        } else {
            params.append("accountId", member.account.id);
        }
        return params;
    }

    static updateCharacterGroupLink(link, member)
    {
        const params = CharacterUtil.createTopCharacterGroupIdParameters(member);
        const fullParams = GroupUtil.fullUrlSearchParams(params);
        link.setAttribute("href", `${ROOT_CONTEXT_PATH}?${fullParams.toString()}#group-group`);
    }

    static getCheaterFlag(reports)
    {
        if(!reports) return null;

        const confirmedReports = reports.filter(r=>r.report.status);
        return confirmedReports.some(r=>r.report.restrictions)
            ? CHEATER_FLAG.CHEATER
            : confirmedReports.some(r=>r.report.restrictions === false)
                ? CHEATER_FLAG.SUSPICIOUS
                : CHEATER_FLAG.REPORTED;
    }

    static updateCharacterReportsView()
    {
        const reportsContainer = document.querySelector("#character-reports");
        const reportsBody = reportsContainer.querySelector(":scope .character-reports");
        const reports = Model.DATA.get(VIEW.CHARACTER).get("reports");
        if(!reports || reports.length == 0) {
            reportsContainer.classList.add("d-none");
            return;
        }
        reportsContainer.classList.remove("d-none");
        CharacterUtil.updateCharacterReportsSection(reportsBody, reports, 4);
        if(!document.querySelector("#player-info-additional-container .player-flag-class-cheater"))
            document.querySelector("#player-info-additional-container").appendChild(ElementUtil.createCheaterFlag(CharacterUtil.getCheaterFlag(reports), true));
    }

    static updateAllCharacterReportsView()
    {
        const reportsContainer = document.querySelector("#all-character-reports");
        const reportsBody = reportsContainer.querySelector(":scope .character-reports");
        const reports = Model.DATA.get(VIEW.CHARACTER_REPORTS).get("reports");
        if(!reports || reports.length == 0) return;
        CharacterUtil.updateCharacterReportsSection(reportsBody, reports, 4);
        Session.updateReportsNotifications();
    }

    static updateCharacterReportsSection(section, reports, headerLevel, removeChildren = true)
    {
        if(removeChildren) ElementUtil.removeChildren(section);
        for(const report of reports) section.appendChild(CharacterUtil.createReportElement(report, headerLevel));
        $(section).popover
        ({
            html: true,
            boundary: "body",
            placement: "auto",
            trigger: "hover",
            selector: '[data-toggle="popover"]',
            content: function(){return CharacterUtil.createDynamicVotersTable($(this)[0]).outerHTML;}
        });
    }

    static createReportElement(report, headerLevel)
    {
        const reportContainer = ElementUtil.createElement("section", null, "player-character-report text-left mb-5");

        const header = ElementUtil.createElement("h" + headerLevel, null, "header d-flex flex-wrap-gap-05 py-1 mb-3 em-1 font-weight-bold bg-transparent-05 rounded");
        header.appendChild(TeamUtil.createPlayerLink(null, report.member, false));
        if(report.additionalMember) header.appendChild(TeamUtil.createPlayerLink(null, report.additionalMember, false));
        header.appendChild(ElementUtil.createElement("span", null, "type", report.report.type));
        header.appendChild(CharacterUtil.createReportStatus(report.report.status));

        const evidenceContainer = ElementUtil.createElement("div", null, "evidence-container d-flex flex-column flex-wrap-gap-1-5");
        for(const evidence of report.evidence) evidenceContainer.appendChild(CharacterUtil.createEvidenceElement(evidence, headerLevel + 1));
        reportContainer.appendChild(header);
        reportContainer.appendChild(evidenceContainer);
        return reportContainer;
    }

    static createEvidenceElement(evidence, headerLevel)
    {
        const evidenceElement = ElementUtil.createElement("article", null, "evidence", null, [
            ["data-report-id", evidence.evidence.playerCharacterReportId],
            ["data-evidence-id", evidence.evidence.id],
        ]);
        evidenceElement.appendChild(CharacterUtil.createEvidenceHeader(evidence, headerLevel));
        evidenceElement.appendChild(ElementUtil.createElement("p", null, "content text-break", evidence.evidence.description));
        evidenceElement.appendChild(CharacterUtil.createEvidenceFooter(evidence));

        return evidenceElement;
    }

    static createEvidenceHeader(evidence, headerLevel)
    {
        const header = ElementUtil.createElement("h" + headerLevel, null, "header em-1 d-flex flex-wrap-gap-05");
        header.appendChild(ElementUtil.createElement("span", null, "reporter font-weight-bold",
            evidence.reporterAccount ? evidence.reporterAccount.battleTag : "Anonymous"));
        header.appendChild(ElementUtil.createElement("time", null, "reporter text-secondary",
            Util.DATE_TIME_FORMAT.format(Util.parseIsoDateTime(evidence.evidence.created)),
            [["datetime", evidence.evidence.created]]
        ));
        return header;
    }

    static createEvidenceFooter(evidence)
    {
        const footer = document.createElement("footer");
        footer.classList.add("d-flex", "flex-wrap-gap");
        footer.appendChild(CharacterUtil.createVotesCell(evidence.votes.filter(v=>v.vote.vote == true), "text-success", "bg-success", "true"));
        footer.appendChild(CharacterUtil.createReportStatus(evidence.evidence.status));
        footer.appendChild(CharacterUtil.createVotesCell(evidence.votes.filter(v=>v.vote.vote == false), "text-danger", "bg-danger", "false"));
        return footer;
    }
    
    static createReportStatus(status)
    {
        const statusCell = ElementUtil.createElement("span", null, "status px-1 rounded");
        if(status == true) {
            statusCell.classList.add("text-success", "border-success");
            statusCell.textContent = "Confirmed";
        } else if (status == false) {
            statusCell.classList.add("text-danger", "border-danger");
            statusCell.textContent = "Denied";
        } else {
            statusCell.classList.add("text-secondary", "border-secondary");
            statusCell.textContent = "Undecided";
        }
        return statusCell;
    }

    static createVotesCell(votes, textClass, bgClass, vote)
    {
        const votesCell = ElementUtil.createElement("span", null, "vote d-inline-block px-2 rounded",
            votes.length,
            [
                ["data-toggle", "popover"],
                ["data-vote", vote]
            ]
        );
        if(Session.currentAccount && votes.find(v=>v.vote.voterAccountId == Session.currentAccount.id)) {
            votesCell.classList.add("text-white", "font-weight-bold", bgClass);
        } else {
            votesCell.classList.add(textClass);
        }

        if(Session.currentRoles && Session.currentRoles.find(r=>r == "MODERATOR")) {
            votesCell.addEventListener("click", CharacterUtil.onEvidenceVote);
            votesCell.setAttribute("role", "button");
        }
        return votesCell;
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
        CharacterUtil.voteOnEvidence(td.closest("[data-evidence-id]").getAttribute("data-evidence-id"), td.getAttribute("data-vote"))
            .then(updatedVotes=>
            {
                const rows = document.querySelectorAll('[data-evidence-id="' + updatedVotes[0].vote.evidenceId + '"]');
                for(const row of rows)
                {
                    const evidence = Model.DATA.get(ViewUtil.getView(row)).get("reports")
                        .flatMap(r=>r.evidence)
                        .find(e=>e.evidence.id == updatedVotes[0].vote.evidenceId);
                    evidence.votes = updatedVotes;
                    row.querySelector(":scope footer").remove();
                    row.appendChild(CharacterUtil.createEvidenceFooter(evidence));
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
        const row = parent.closest("[data-report-id]");
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
            fd.set("playerCharacterId", Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).members.character.id);
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
                if(m.character.id == Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR).members.character.id) return;

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
            .then(e=>{
                CharacterUtil.resetCharacterReports(true);
                return CharacterUtil.enqueueUpdateCharacterReports();
            })
            .then(e=>{
                $("#report-character-modal").modal('hide');
                $("#character-reports").collapse('show');
                window.setTimeout(e=>Util.scrollIntoViewById("character-reports"), 500);
            });
    }

    static updateReportForm()
    {
        const select = document.querySelector("#report-character-type");
        const additionalGroup = document.querySelector("#report-character-additional-group");
        const additionalInput = additionalGroup.querySelector(":scope #report-character-additional");
        if(select.value != "LINK") {
           additionalGroup.classList.add("d-none");
           additionalInput.setAttribute("disabled", "disabled");
        } else {
            additionalGroup.classList.remove("d-none");
            additionalInput.removeAttribute("disabled");
        }
    }

    static enhanceAllCharacterReportsControls()
    {
        const form = document.querySelector("#load-all-character-reports");
        if(form) form.addEventListener("submit", e=>{
            e.preventDefault();
            const formData = new FormData(e.target);
            CharacterUtil.updateAllCharacterReports(formData.get("only-unreviewed"));
        });
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

    static renderLadderProPlayer(proPlayer)
    {
        return (proPlayer.proTeam != null ? "[" + proPlayer.proTeam.shortName + "]" : "")
            + proPlayer.proPlayer.nickname;
    }

    static renderLadderProPlayerGroupLink(ladderProPlayer)
    {
        return GroupUtil.createGroupLink(
            new URLSearchParams([["proPlayerId", ladderProPlayer.proPlayer.id]]),
            CharacterUtil.renderLadderProPlayer(ladderProPlayer)
        );
    }

    static renderAccount(account)
    {
        return Util.isFakeBattleTag(account.battleTag) ? account.id : account.battleTag;
    }

    static createAccountGroupLink(account)
    {
        return GroupUtil.createGroupLink(
            new URLSearchParams([["accountId", account.id]]),
            CharacterUtil.renderAccount(account)
        );
    }

}
CharacterUtil.TEAM_SNAPSHOT_SEASON_END_OFFSET_MILLIS = 2 * 24 * 60 * 60 * 1000;
CharacterUtil.MMR_Y_VALUE_GETTERS = new Map([
    ["mmr", (history)=>history.teamState.rating],
    ["percent-region", (history)=>history.teamState.regionTopPercent],
    ["default", (history)=>history.teamState.rating],
]);

CharacterUtil.MMR_Y_VALUE_OPERATIONS = new Map([
    ["mmr", {
        get: (history, ix)=>history.history[TEAM_HISTORY_HISTORY_COLUMN.RATING.fullName][ix],
        max: (values)=>Math.max(...values.filter(v=>v != null)),
        compare: (a, b)=>b - a
    }],
    ["percent-region", {
        get:  (history, ix)=>{
            const rank = history.history[TEAM_HISTORY_HISTORY_COLUMN.REGION_RANK.fullName][ix];
            return rank != null
                ? (rank / history.history[TEAM_HISTORY_HISTORY_COLUMN.REGION_TEAM_COUNT.fullName][ix]) * 100
                : null;
        },
        max: (values)=>Math.min(...values.filter(v=>v != null)),
        compare: (a, b)=>a - b
    }]
]);
CharacterUtil.MMR_Y_REQUIRED_HISTORY_COLUMNS = new Map([
    ["mmr", new Set([TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP, TEAM_HISTORY_HISTORY_COLUMN.RATING])],
    ["percent-region", new Set([
        TEAM_HISTORY_HISTORY_COLUMN.TIMESTAMP,
        TEAM_HISTORY_HISTORY_COLUMN.REGION_RANK,
        TEAM_HISTORY_HISTORY_COLUMN.REGION_TEAM_COUNT
    ])]
]);
CharacterUtil.MMR_Y_SUMMARY_COLUMN_FORMATTERS = new Map([
    [TEAM_HISTORY_SUMMARY_COLUMN.RATING_AVG, Math.floor]
]);
CharacterUtil.MMR_Y_REQUIRED_NUMERIC_SUMMARY_COLUMNS = new Map([
    ["mmr", new Set([
        TEAM_HISTORY_SUMMARY_COLUMN.GAMES,
        TEAM_HISTORY_SUMMARY_COLUMN.RATING_LAST,
        TEAM_HISTORY_SUMMARY_COLUMN.RATING_AVG,
        TEAM_HISTORY_SUMMARY_COLUMN.RATING_MAX,
    ])],
    //the same util top% summary is done
    ["percent-region", new Set([
        TEAM_HISTORY_SUMMARY_COLUMN.GAMES,
        TEAM_HISTORY_SUMMARY_COLUMN.RATING_LAST,
        TEAM_HISTORY_SUMMARY_COLUMN.RATING_AVG,
        TEAM_HISTORY_SUMMARY_COLUMN.RATING_MAX,
    ])]
]);
CharacterUtil.MMR_REQUIRED_PROGRESS_SUMMARY_COLUMNS = new Set([
    TEAM_HISTORY_SUMMARY_COLUMN.REGION_RANK_LAST,
    TEAM_HISTORY_SUMMARY_COLUMN.REGION_TEAM_COUNT_LAST
]);
CharacterUtil.CHARACTER_UPDATE_IDS = Array.from(document.querySelectorAll("#player-info .container-loading"))
    .map(elem=>elem.id);
CharacterUtil.MMR_HISTORY_PLACEHOLDER = '-';
CharacterUtil.ALL_RACE = Object.freeze({name: "all", fullName: "ALL", order: 999});
CharacterUtil.MMR_HISTORY_COMPLETE_POINT_TASK_NAME = "mmr-history-complete-point-task";
CharacterUtil.MMR_HISTORY_COMPLETE_POINT_TIMEOUT = 100;