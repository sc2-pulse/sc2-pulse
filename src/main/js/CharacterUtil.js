// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class CharacterUtil
{

    static showCharacterInfo(e = null, explicitId = null)
    {
        if (e != null) e.preventDefault();
        const id = explicitId || e.currentTarget.getAttribute("data-character-id");

        const promises = [];
        const tabs = new URLSearchParams(window.location.search).getAll("t");
        const searchParams = new URLSearchParams();
        searchParams.append("type", "character");
        searchParams.append("id", id);
        const stringParams = searchParams.toString();
        searchParams.append("m", "1");
        for(const tab of tabs) searchParams.append("t", tab);
        promises.push(BootstrapUtil.hideActiveModal(["player-info", "error-generation"]));
        promises.push(CharacterUtil.updateCharacterTeams(id));
        promises.push(CharacterUtil.updateCharacterStats(id));

        return Promise.all(promises)
            .then(o=>new Promise((res, rej)=>{
                if(!Session.isHistorical) HistoryUtil.pushState({type: "character", id: id}, document.title, "?" + searchParams.toString());
                Session.currentSearchParams = stringParams;
                res();
            }))
            .then(e=>BootstrapUtil.showModal("player-info"));
    }

    static updateCharacterTeamsModel(id)
    {
        const request = "api/character/" + id + "/teams";
        const characterTeamsPromise =
            fetch(request).then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
        return Promise.all([characterTeamsPromise, StatsUtil.updateBundleModel()])
            .then(jsons => new Promise((res, rej)=>{
                Model.DATA.get(VIEW.CHARACTER).set(VIEW_DATA.SEARCH, {result: jsons[0]});
                Model.DATA.get(VIEW.CHARACTER).set(VIEW_DATA.VAR, id);
                res(jsons);
             }));
    }

    static updateCharacterTeamsView()
    {
        const id = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.VAR);
        const searchResult = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.SEARCH);
        CharacterUtil.updateCharacterInfo(searchResult, id);
        CharacterUtil.updateCharacterTeamsSection(searchResult);
    }

    static updateCharacterTeams(id)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return CharacterUtil.updateCharacterTeamsModel(id)
            .then(jsons => new Promise((res, rej)=>{
                CharacterUtil.updateCharacterTeamsView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
                res();
            }))
            .catch(error => Util.setGeneratingStatus(STATUS.ERROR, error.message));
    }

    static updateCharacterInfo(searchResultFull, id)
    {
        const searchResult = searchResultFull.result;
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

        CharacterUtil.updateCharacterInfoName(character, account);
        const region = EnumUtil.enumOfName(character.region, REGION);
        const profileLinkElement = document.getElementById("battlenet-profile-link");
        if(region == REGION.CN)
        {
            //the upstream site is not supporting the CN region.
            profileLinkElement.parentElement.classList.add("d-none");
        }
        else
        {
            const profileLink = `https://starcraft2.com/profile/${region.code}/${character.realm}/${character.battlenetId}`;
            profileLinkElement.setAttribute("href", profileLink);
            profileLinkElement.parentElement.classList.remove("d-none");
        }
        document.getElementById("player-info-battletag").textContent = account.battleTag;
    }

    static updateCharacterInfoName(character, account)
    {
        let charName;
        let charNameAdditional;
        const hashIx = character.name.indexOf("#");
        const nameNoHash = character.name.substring(0, hashIx);
        if(!Util.isBarcode(nameNoHash))
        {
            charName = nameNoHash;
            charNameAdditional = character.name.substring(hashIx);
        }
        else
        {
            charName = Util.unmaskBarcode(character, account);
            charNameAdditional = `(${character.name})`;
        }
        document.getElementById("player-info-title-name").textContent = charName;
        document.getElementById("player-info-title-name-additional").textContent = charNameAdditional;
    }

    static updateCharacterTeamsSection(searchResultFull)
    {
        const searchResult = searchResultFull.result;
        grouped = searchResult.reduce(function(rv, x) {
            (rv[x["season"]] = rv[x["season"]] || []).push(x);
            return rv;
        }, {});

        const navs = document.getElementById("character-teams-section").getElementsByClassName("nav-item");
        const panes = document.getElementById("character-teams-section").getElementsByClassName("tab-pane");
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
            const linkText = seasonFull.year + " s" + seasonFull.number;
            link.textContent = linkText;
            pane.querySelector(":scope table h4").textContent =
                seasonFull.year + " season " + seasonFull.number + " teams";
            if(!shown)
            {
                if(Session.currentSeason == null || season == Session.currentSeason || ix == groupedEntries.length - 1)
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

    static updateCharacterStatsModel(id)
    {
        const request = "api/character/" + id + "/stats";
        return fetch(request)
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{
                Model.DATA.get(VIEW.CHARACTER).set(VIEW_DATA.CHARACTER_STATS, json);
                Model.DATA.get(VIEW.CHARACTER).set(VIEW_DATA.VAR, id);
                res(json);
            }));
    }

    static updateCharacterStatsView()
    {
        const searchResult = Model.DATA.get(VIEW.CHARACTER).get(VIEW_DATA.CHARACTER_STATS);
        for(const statsSection of document.getElementsByClassName("player-stats-dynamic")) statsSection.classList.add("d-none");
        for(const stats of searchResult)
        {
            const teamFormat = EnumUtil.enumOfId(stats.queueType, TEAM_FORMAT);
            const teamType = EnumUtil.enumOfId(stats.teamType, TEAM_TYPE);
            const raceName = stats.race == null ? "all" : EnumUtil.enumOfName(stats.race, RACE).name;
            const league = EnumUtil.enumOfId(stats.leagueMax, LEAGUE);
            const card = document.getElementById("player-stats-" + teamFormat.name + "-" + teamType.name);
            const raceStats = card.getElementsByClassName("player-stats-" + raceName)[0];
            raceStats.getElementsByClassName("player-stats-" + raceName + "-mmr")[0].textContent = stats.ratingMax;
            raceStats.getElementsByClassName("player-stats-" + raceName + "-games")[0].textContent = stats.gamesPlayed;
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

    static updateCharacterStats(id)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        CharacterUtil.updateCharacterStatsModel(id)
            .then(json => new Promise((res, rej)=>{CharacterUtil.updateCharacterStatsView(); Util.setGeneratingStatus(STATUS.SUCCESS); res();}))
            .catch(error => Util.setGeneratingStatus(STATUS.ERROR, error.message));
    }

    static findCharactersByName()
    {
        CharacterUtil.updateCharacterSearch(document.getElementById("search-player-name").value);
    }

    static updateCharacterSearchModel(name)
    {
        const request = "api/characters?name=" + encodeURIComponent(name);
        return fetch(request)
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
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

        for(let i = 0; i < searchResult.length; i++)
        {
            const character = searchResult[i];
            const row = tbody.insertRow();
            row.insertCell().appendChild(ElementUtil.createImage("flag/", character.members.character.region.toLowerCase(), "table-image-long"));
            row.insertCell().appendChild(ElementUtil.createImage("league/", EnumUtil.enumOfId(character.leagueMax, LEAGUE).name, "table-image table-image-square mr-1"));
            row.insertCell().appendChild(document.createTextNode(character.ratingMax));
            row.insertCell().appendChild(document.createTextNode(character.totalGamesPlayed))
            const membersCell = row.insertCell();
            membersCell.classList.add("complex", "cell-main");
            const mRow = document.createElement("span");
            mRow.classList.add("row", "no-gutters");
            const mInfo = TeamUtil.createMemberInfo(character, character.members);
            mInfo.getElementsByClassName("player-name")[0].classList.add("c-divider");
            const bTag = document.createElement("span");
            bTag.classList.add("c-divider", "battle-tag");
            bTag.textContent = character.members.account.battleTag;
            mInfo.getElementsByClassName("player-link")[0].appendChild(bTag);
            mRow.appendChild(mInfo);
            membersCell.appendChild(mRow);
            tbody.appendChild(row);
        }
    }

    static updateCharacterSearch(name)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        const tabs = new URLSearchParams(window.location.search).getAll("t");
        const searchParams = new URLSearchParams();
        searchParams.append("type", "search");
        searchParams.append("name", name);
        const stringParams = searchParams.toString();
        for(const tab of tabs) searchParams.append("t", tab);
        return CharacterUtil.updateCharacterSearchModel(name)
            .then(json => new Promise((res, rej)=>{
                CharacterUtil.updateCharacterSearchView();
                Util.setGeneratingStatus(STATUS.SUCCESS);
                if(!Session.isHistorical) HistoryUtil.pushState({type: "search", name: name}, document.title, "?" + searchParams.toString());
                Session.currentSearchParams = stringParams;
                res();
            }))
            .catch(error => Util.setGeneratingStatus(STATUS.ERROR, error.message));
    }

    static updatePersonalCharactersModel()
    {
        return fetch("api/my/characters")
            .then(resp => {if (!resp.ok) throw new Error(resp.status + " " + resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{
                Model.DATA.get(VIEW.PERSONAL_CHARACTERS).set(VIEW_DATA.SEARCH, json);
                res(json);
            }));
    }

    static updatePersonalCharactersView()
    {
        CharacterUtil.updateCharacters(document.querySelector("#personal-characters-table"), Model.DATA.get(VIEW.PERSONAL_CHARACTERS).get(VIEW_DATA.SEARCH));
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
            .catch(error => Util.setGeneratingStatus(STATUS.ERROR, error.message));
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
    }

}