// Copyright (C) 2020-2023 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class GroupUtil
{

    static getGroup(groupParams, cache = false)
    {
        let cachedResult = {};
        if(cache) {
           const cached = GroupUtil.getGroupCache(groupParams);
           groupParams = cached.missedParams;
           cachedResult = cached.cached;
           if(groupParams.size == 0) return Promise.resolve(cachedResult);
        }
        const request = `${ROOT_CONTEXT_PATH}api/group?${groupParams.toString()}`;
        return Session.beforeRequest()
           .then(n=>fetch(request))
           .then(resp=>Session.verifyJsonResponse(resp, [200, 404]))
           .then(json=>{
                if(cache) {
                    GroupUtil.cacheGroup(json);
                    Util.concatObject(cachedResult, json);
                    return json;
                } else {
                    return json;
                }
           });
    }

    static getGroupCache(groupParams)
    {
        const missedParams = new URLSearchParams(groupParams);
        const result = {cached: {}, missedParams: missedParams, params: groupParams};
        for(const key of new Set(missedParams.keys())) {
            const cacheKey = GroupUtil.mapGroupCacheKey(key);
            result.cached[cacheKey] = [];
            if(!GroupUtil.CACHE[cacheKey]) GroupUtil.CACHE[cacheKey] = new Map();
            for(const value of missedParams.getAll(key)) {
                const cachedObj = GroupUtil.CACHE[cacheKey].get(parseInt(value));
                if(cachedObj) {
                    result.cached[cacheKey].push(cachedObj);
                    missedParams.delete(key, value);
                }
            }
        }
        return result;
    }

    static mapGroupCacheKey(key)
    {
        return key.substring(0, key.length - 2) + "s";
    }

    static cacheGroup(group)
    {
        for(const [key, values] of Object.entries(group)) {
            if(!GroupUtil.CACHE[key]) GroupUtil.CACHE[key] = new Map();
            values.forEach(val=>GroupUtil.CACHE[key].set(val.id, val));
        }
    }

    static loadGroupModel(groupParams)
    {
        return GroupUtil.getGroup(groupParams)
            .then(json=>{
                Model.DATA.get(VIEW.GROUP).get(VIEW_DATA.VAR).group = json;
                Model.DATA.get(VIEW.GROUP).get(VIEW_DATA.SEARCH).clans = json != null ? json.clans : [];
                return json;
            });
    }

    static updateGroup(group, section)
    {
        section.querySelectorAll("section").forEach(s=>s.classList.add("d-none"));
        if(group.characters && group.characters.length > 0) GroupUtil.updateGroupSection(
            table=>CharacterUtil.updateCharacters(table, group.characters),
            section.querySelector(":scope .table-character")
        );
        if(group.clans && group.clans.length > 0) GroupUtil.updateGroupSection(
            table=>ClanUtil.updateClanTable(table,  group.clans),
            section.querySelector(":scope .table-clan")
        );
        if(group.proPlayers && group.proPlayers.length > 0) GroupUtil.updateGroupSection(
            container=>ElementUtil.updateGenericContainer(container, group.proPlayers
                .map(CharacterUtil.renderLadderProPlayerGroupLink)),
            section.querySelector(":scope .players")
        );
        if(group.accounts && group.accounts.length > 0) GroupUtil.updateGroupSection(
            container=>ElementUtil.updateGenericContainer(container, group.accounts
                .map(CharacterUtil.createAccountGroupLink)),
            section.querySelector(":scope .accounts")
        );
    }

    static updateGroupSection(updater, section)
    {
        updater(section);
        section.closest("section").classList.remove("d-none");
    }

    static createHeaderText(group)
    {
        let text;
        if(group.clans && group.clans.length > 0) {
            text = ClanUtil.generateClanName(group.clans[0], true);
        } else if(group.proPlayers && group.proPlayers.length > 0) {
            text = CharacterUtil.renderLadderProPlayer(group.proPlayers[0]);
        } else if(group.accounts && group.accounts.length > 0) {
            text = CharacterUtil.renderAccount(group.accounts[0]);
        } else if(group.characters && group.characters.length > 0) {
            text = Util.unmaskName(group.characters[0].members).unmaskedName;
        }
        const count = (group.clans && group.clans.length || 0)
            + (group.proPlayers && group.proPlayers.length || 0)
            + (group.accounts && group.accounts.length || 0)
            + (group.characters && group.characters.length || 0)
            - 1;
        if(count > 0) text += `(+${count})`;
        return text;
    }

    static generatePageTitle(params, hash)
    {
        const defaultTitle = "Group";
        const searchResult = Model.DATA.get(VIEW.GROUP).get(VIEW_DATA.VAR);
        if(!searchResult || !searchResult.group) return defaultTitle;

        return GroupUtil.createHeaderText(searchResult.group) + " - " + ElementUtil.getTabTitle(hash);
    }

    static updateRequiredMetadata(groupParams, section)
    {
        return GroupUtil.loadGroupModel(groupParams)
            .then(g=>{
                GroupUtil.updateGroup(g, section.querySelector(":scope .character-group"))
                section.querySelector(":scope .modal-title").textContent = GroupUtil.createHeaderText(g);
            });
    }

    static getTeams(params)
    {
        const request = `${ROOT_CONTEXT_PATH}api/character-teams?${params.toString()}`;
        return Session.beforeRequest()
           .then(n=>fetch(request))
           .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static createTeamParams(groupParams, queue, season)
    {
        const params = new URLSearchParams(groupParams);
        params.append("queue", queue);
        params.append("season", season);
        return params;
    }

    static updateTeamModel(view, params)
    {
        return GroupUtil.getTeams(params)
            .then(teams=>{
                Model.DATA.get(view).get(VIEW_DATA.SEARCH).teams = teams;
                const teamData = Model.DATA.get(view).get(VIEW_DATA.TEAMS);
                teamData.result = teamData.result ? teamData.result.concat(teams) : teams;
                return teams;
            });
    }

    static updateTeamView(container)
    {
        const view = ViewUtil.getView(container);
        const teams = Model.DATA.get(view).get(VIEW_DATA.SEARCH).teams;
        TeamUtil.updateTeamsTable(container.querySelector(":scope .table-team"), {result: teams || []});
    }

    static updateTeams(section, queue, season)
    {
        const view = ViewUtil.getView(section);
        const varModel = Model.DATA.get(view).get(VIEW_DATA.VAR);
        const params = GroupUtil.createTeamParams(varModel.groupParams, queue, season);
        const container = section.querySelector(":scope .group-teams");
        return GroupUtil.updateTeamModel(view, params)
            .then(teams=>{
                GroupUtil.updateTeamView(container);
                return {data: teams, status: LOADING_STATUS.COMPLETE};
            });
    }

    static resetTeams(container)
    {
        Util.resetLoadingIndicator(container);
        const view = ViewUtil.getView(container);
        Model.DATA.get(view).get(VIEW_DATA.SEARCH).teams = [];
        ElementUtil.removeChildren(container.querySelector(":scope .teams tbody"));
    }

    static getCharacters(groupParams)
    {
        const request = `${ROOT_CONTEXT_PATH}api/characters?${groupParams.toString()}`;
        return Session.beforeRequest()
           .then(n=>fetch(request))
           .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static updateCharacters(groupParams, section)
    {
        const container = section.querySelector(":scope .group-characters");
        const promise = ()=>GroupUtil.getCharacters(groupParams)
            .then(characters=>{
                const view = ViewUtil.getView(section);
                Model.DATA.get(view).get(VIEW_DATA.SEARCH).characters = characters;
                CharacterUtil.updateCharacters(container.querySelector(":scope .table-character"), characters);
                return {data: characters, status: LOADING_STATUS.COMPLETE};
            });
        return Util.load(container, promise);
    }

    static getMatches(params)
    {
        const request = `${ROOT_CONTEXT_PATH}api/character-matches?${params.toString()}`;
        return Session.beforeRequest()
           .then(n=>fetch(request))
           .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static createMatchesParams(matches, varModel, matchType)
    {
        const params = new URLSearchParams(varModel.groupParams);
        if(matches && matches.length > 0) {
            const matchCursor = matches[matches.length - 1];
            params.append("dateCursor", matchCursor.match.date);
            params.append("typeCursor", matchCursor.match.type);
            params.append("mapCursor", matchCursor.map.id);
            params.append("regionCursor", matchCursor.match.region);
            if(varModel.matchType) params.append("type", varModel.matchType);
        } else {
            if(matchType != "all") {
                varModel.matchType = matchType;
                params.append("type", matchType);
            }
        }
        return params;
    }

    static updateMatches(section, matchType)
    {
        const view = ViewUtil.getView(section);
        const container = section.querySelector(":scope .group-matches");
        const matches = Model.DATA.get(view).get(VIEW_DATA.SEARCH).matches;
        const varModel = Model.DATA.get(view).get(VIEW_DATA.VAR);
        const params = GroupUtil.createMatchesParams(matches, varModel, matchType);
        return GroupUtil.getMatches(params)
            .then(matchesResponse=>{
                if(!matchesResponse || matchesResponse.result.length == 0) {
                    return {status: LOADING_STATUS.COMPLETE};
                }
                Model.DATA.get(view).get(VIEW_DATA.SEARCH).matches = matches
                    ? matches.concat(matchesResponse.result)
                    : matchesResponse.result;
                GroupUtil.updateMatchesView(container, matchesResponse.result);
                return {data: matchesResponse.result, status: LOADING_STATUS.NONE};
            });
    }

    static updateMatchesView(section, matches, reset = false)
    {
        const view = ViewUtil.getView(section);
        const varModel = Model.DATA.get(view).get(VIEW_DATA.VAR);
        const result = MatchUtil.updateMatchTable(
            section.querySelector(":scope .matches"), matches,
            (data)=>GroupUtil.isMainMatchParticipant(data, varModel.groupParams),
            localStorage.getItem("matches-historical-mmr-" + view.name) != "false",
            null,
            reset
        );
        const teamsData = Model.DATA.get(view).get(VIEW_DATA.TEAMS);
        teamsData.result = reset
            ? result.teams
            : teamsData.result ? teamsData.result.concat(result.teams) : result.teams;
    }

    static isMainMatchParticipant(data, groupParams)
    {
        return Number.isInteger(data)
            ? false
            :
                (
                    data.member.clan
                        && data.member.clan.id
                        && groupParams.getAll("clanId").some(clanId=>data.member.clan.id == clanId)
                )
                || groupParams.getAll("characterId").some(characterId=>data.member.character.id == characterId)
    }

    static resetMatches(section)
    {
        Util.resetLoadingIndicator(section);
        const view = ViewUtil.getView(section);
        ElementUtil.removeChildren(section.querySelector(":scope .matches tbody"));
        Model.DATA.get(view).get(VIEW_DATA.SEARCH).matches = [];
    }

    static getClanHistory(params)
    {
        const request = `${ROOT_CONTEXT_PATH}api/group/clan/history?${params.toString()}`;
        return Session.beforeRequest()
           .then(n=>fetch(request))
           .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static createClanHistoryParams(clanHistory, varModel)
    {
        const params = new URLSearchParams(varModel.groupParams);
        if(clanHistory && clanHistory.events && clanHistory.events.length > 0) {
            const cursor = clanHistory.events[clanHistory.events.length - 1];
            params.append("createdCursor", cursor.created);
            params.append("characterIdCursor", cursor.playerCharacterId);
        }
        return params;
    }

    static updateClanHistory(section)
    {
        const view = ViewUtil.getView(section);
        const container = section.querySelector(":scope .group-clan");
        const clanHistory = Model.DATA.get(view).get(VIEW_DATA.SEARCH).clanHistory;
        const varModel = Model.DATA.get(view).get(VIEW_DATA.VAR);
        const params = GroupUtil.createClanHistoryParams(clanHistory, varModel);

        return GroupUtil.getClanHistory(params)
            .then(response=>{
                if(!response) {
                    return {status: LOADING_STATUS.COMPLETE};
                }
                GroupUtil.mapClanHistory(response);
                Model.DATA.get(view).get(VIEW_DATA.SEARCH).clanHistory = clanHistory
                    ? Util.addAllCollections(response, clanHistory)
                    : response;
                ClanUtil.updateClanHistoryTable(container.querySelector(":scope .clan-history"), response);
                return {data: response, status: LOADING_STATUS.NONE}
            });
    }

    static mapClanHistory(clanHistory)
    {
        clanHistory.clans = Util.toMap(clanHistory.clans, clan=>clan.id);
        clanHistory.characters = Util.toMap(clanHistory.characters, character=>character.members.character.id);
    }

    static getLinks(params)
    {
        const request = `${ROOT_CONTEXT_PATH}api/character-links?${params.toString()}`;
        return Session.beforeRequest()
           .then(n=>fetch(request))
           .then(resp=>Session.verifyJsonResponse(resp, [200, 404, 500]));
    }

    static resetLinksView(section)
    {
        ElementUtil.removeChildren(section.querySelector(":scope .links"))
    }

    static resetLinksModel(view)
    {
        delete Model.DATA.get(view).get(VIEW_DATA.SEARCH)?.links;
    }

    static resetLinks(section, clearLoading = false)
    {
        GroupUtil.resetLinksModel(ViewUtil.getView(section));
        GroupUtil.resetLinksView(section);
        if(clearLoading) Util.resetLoadingIndicatorTree(section.querySelector(":scope .group-links"));
    }

    static groupLinkData(links, type)
    {
        const failed = links.flatMap(l=>l.failedTypes).length > 0;
        return {
            type: type,
            failed: failed,
            status: failed ? LOADING_STATUS.ERROR : LOADING_STATUS.COMPLETE,
            links: links.flatMap(l=>l.links)
        };
    }

    static calculateMissingCharacters(links, allCharacters, validCharacters)
    {
        const linkCharacterIds  = new Set(links.filter(l=>l.links.length > 0).map(l=>l.playerCharacterId));
        return {
            missing: validCharacters.filter(c=>!linkCharacterIds.has(c.members.character.id)),
            excluded: allCharacters.slice(validCharacters.length)
        }
    }

    static renderLinkErrors(linkResults)
    {
        const ul = ElementUtil.createElement("ul", null, "errors mx-auto text-danger");
        for(const linkResult of linkResults)
            for(const error of linkResult.errors)
                ul.appendChild(ElementUtil.createElement("li", null, linkResult.type.name + " error",
                    linkResult.type.name + ": " + error));
        return ul;
    }

    static renderMissingLinkCharacters(characters, title, description, classes)
    {
        const header = ElementUtil.createElement("h4", null, "text-warning", title + " ");
        const infoIcon = ElementUtil.createIcoFontElement("info", description, "text-primary", [["data-toggle", "tooltip"]]);
        header.appendChild(infoIcon);
        const missingCharacters = CharacterUtil.renderCharacters(characters, header);
        if(classes != null) missingCharacters.classList.add(...classes);
        return missingCharacters;
    }

    static updateLinksView(section)
    {
        const view = ViewUtil.getView(section);
        const links = Model.DATA.get(view).get(VIEW_DATA.SEARCH).links?.data;
        if(!links) return;

        const container = section.querySelector(".links");
        const linkContainer = ElementUtil.createElement("div", null, "container-links d-flex flex-center-wrap-gap");
        container.appendChild(linkContainer);
        container.appendChild(ElementUtil.createElement("div", null, "c-divider-hr my-0"));
        if(links.map(r=>r.errors.length).reduce((a, b) => a + b, 0) > 0)
            container.appendChild(GroupUtil.renderLinkErrors(links));
        for(const linkResult of links) {
            if(linkResult.data == null) continue;

            const a = ElementUtil.createElement("a", null, "social-media", null,
                [["href", linkResult.data], ["target", "_blank"], ["rel", "noopener"]]);
            a.appendChild(ElementUtil.createImage("logo/", linkResult.type.name, "", null, null, "png"));
            linkContainer.appendChild(a);

            if(linkResult.missing?.missing?.length != 0) {
                container.appendChild(GroupUtil.renderMissingLinkCharacters(linkResult.missing.missing,
                    "Missing " + linkResult.type.name + " characters",
                    "Profiles that don't exist on " + linkResult.type.name,
                    ["missing", linkResult.type.name]
                ));
            }
            if(linkResult.missing?.excluded?.length != 0) {
                container.appendChild(GroupUtil.renderMissingLinkCharacters(linkResult.missing.excluded,
                    "Excluded " + linkResult.type.name + " characters",
                    "Inactive profiles(based on 1v1 stats) that have been excluded to meet the "
                        + linkResult.type.name + " group limit("
                        + GroupUtil.LINK_OPERATIONS.get(linkResult.type).activeCharactersMax + ")",
                    ["excluded", linkResult.type.name]
                ));
            }
        }
    }

    static loadCharacterLinks(characters, type)
    {
        const params = new URLSearchParams();
        params.append("type", type.fullName);
        characters.forEach(c=>params.append("characterId", c.members.character.id));
        return GroupUtil.getLinks(params);
    }

    static getActiveCharacters(characters, activeCharactersMax)
    {
        return activeCharactersMax === -1 || characters.length <= activeCharactersMax
            ? characters
            : characters[activeCharactersMax].currentStats.rating == null
                && characters[activeCharactersMax].previousStats.rating == null
                    ? characters.slice(0, activeCharactersMax)
                    : null;
    }

    static getLinksFromCharacters(characters, type)
    {
        const operations = GroupUtil.LINK_OPERATIONS.get(type);
        const validCharacters = GroupUtil.getActiveCharacters(characters, operations.activeCharactersMax);
        if(validCharacters == null) return {
            errors: [
                "Active profile limit exceeded("
                    + (characters.findLastIndex(c=>c.currentStats.rating != null || c.previousStats.rating != null) + 1
                        || characters.length)
                    + "/" + operations.activeCharactersMax + ")"
            ],
            status: LOADING_STATUS.ERROR,
            type: type
        };
        return operations.load(validCharacters)
            .then(links=>{
                const grouped = GroupUtil.groupLinkData(links, type);
                grouped.missing = GroupUtil.calculateMissingCharacters(links, characters, validCharacters);
                grouped.errors = [];
                return operations.generate(grouped);
            })
    }

    static updateLinks(section)
    {
        GroupUtil.resetLinks(section);
        const view = ViewUtil.getView(section);
        const varModel = Model.DATA.get(view).get(VIEW_DATA.VAR);
        return GroupUtil.updateCharacters(varModel.groupParams, section)
            .then(e=>{
                const chars = Model.DATA.get(view).get(VIEW_DATA.SEARCH).characters;
                if(chars == null || chars.length == 0) {
                    const error = new Error("No characters found");
                    error.characterStatus = LOADING_STATUS.COMPLETE;
                    throw error;
                }
                const sortedChars = Array.from(chars);
                sortedChars.sort((a, b)=>(b.currentStats.gamesPlayed - a.currentStats.gamesPlayed)
                    || (b.previousStats.gamesPlayed - a.previousStats.gamesPlayed)
                    || (b.totalGamesPlayed - a.totalGamesPlayed)
                );
                return Promise.allSettled(Array.from(GroupUtil.LINK_OPERATIONS.keys())
                    .map(type=>GroupUtil.getLinksFromCharacters(sortedChars, type)));
            })
            .then(results=>{
                const result = {
                    data: results.map(r=>r.value),
                    status: results.map(r=>r.value?.status).some(status=>status == LOADING_STATUS.ERROR)
                        || Util.getAllSettledLoadingStatus(results) == LOADING_STATUS.ERROR
                            ? LOADING_STATUS.ERROR
                            : LOADING_STATUS.COMPLETE
                }
                Model.DATA.get(view).get(VIEW_DATA.SEARCH).links = result;
                GroupUtil.updateLinksView(section);
                Util.throwFirstSettledError(results);
                return result;
            })
            .catch(e=>{
                if(e.characterStatus != null) return {data: null, status: e.characterStatus};
                throw e;
            });
    }

    static enqueueUpdateLinks()
    {
        const groupSection = document.querySelector("#group");
        const linksContainer = groupSection.querySelector("#group-links");
        return Util.load(linksContainer, ()=>GroupUtil.updateLinks(groupSection));
    }

    static loadAndShowGroup(groupIds)
    {
        document.querySelectorAll("#group .container-loading")
            .forEach(container=>ElementUtil.executeTask(container.id, ()=>container.querySelectorAll(":scope tbody")
                .forEach(ElementUtil.removeChildren)));
        const groupParams = groupIds instanceof URLSearchParams ? groupIds : Util.mapToUrlSearchParams(groupIds);
        const fullParams = GroupUtil.fullUrlSearchParams(groupParams);
        Model.DATA.get(VIEW.GROUP).set(VIEW_DATA.VAR, {groupParams: groupParams, fullGroupParams: fullParams});
        Model.reset(VIEW.GROUP, [VIEW_DATA.SEARCH, VIEW_DATA.TEAMS]);
        document.querySelectorAll("#group .container-loading").forEach(Util.resetLoadingIndicator);
        const modal = document.querySelector("#group");
        Util.setGeneratingStatus(STATUS.BEGIN);
        return GroupUtil.updateRequiredMetadata(groupParams, modal)
            .then(e=>{
                BootstrapUtil.showModal("group");
                Util.setGeneratingStatus(STATUS.SUCCESS);
                const stringParams = fullParams.toString();
                if(!Session.isHistorical) HistoryUtil.pushState({}, document.title, "?" + stringParams + "#group-group");
                Session.currentSearchParams = stringParams;
            })
            .catch(error => Session.onPersonalException(error));
    }

    static fullUrlSearchParams(params)
    {
        const searchParams = new URLSearchParams();
        searchParams.append("type", "group");
        searchParams.append("m", "1");
        return new URLSearchParams("?" + searchParams.toString() + "&" + params.toString());
    }

    static onGroupLinkClick(evt)
    {
        evt.preventDefault();
        return GroupUtil.loadAndShowGroup(Util.deleteSearchParams(Util.getHrefUrlSearchParams(evt.target.closest("a"))));
    }

    static createGroupLink(params, text = "", eventListener = true)
    {
        const a = document.createElement("a");
        a.textContent = text;
        const fullParams = GroupUtil.fullUrlSearchParams(params);
        a.setAttribute("href", `${ROOT_CONTEXT_PATH}?${fullParams.toString()}#group-group`);
        if(eventListener) {
            a.addEventListener("click", GroupUtil.onGroupLinkClick);
        } else {
            a.setAttribute("target", "_blank");
        }
        return a;
    }

    static enhance()
    {
        GroupUtil.enhanceMisc();
        GroupUtil.enhanceTeams();
        ElementUtil.ELEMENT_TASKS.set("group-characters-tab", e=>GroupUtil.updateCharacters(Model.DATA.get(VIEW.GROUP).get(VIEW_DATA.VAR).groupParams, document.querySelector("#group")));
        GroupUtil.enhanceMatches();
        GroupUtil.enhanceClanHistory();
        GroupUtil.enhanceLinks();
    }

    static enhanceMisc()
    {
        document.querySelectorAll(".group-link").forEach(link=>link.addEventListener("click", GroupUtil.onGroupLinkClick));
    }

    static updateGroupTeams()
    {
        const groupSection = document.querySelector("#group");
        const teamsContainer = groupSection.querySelector(":scope .group-teams");
        const fd = new FormData(document.querySelector("#group-teams-form"));
        return Util.load(teamsContainer, ()=>GroupUtil.updateTeams(groupSection, fd.get("queue"), fd.get("season")));
    }

    static enhanceTeams()
    {
        ElementUtil.ELEMENT_TASKS.set("group-teams-tab", GroupUtil.updateGroupTeams);
        document.querySelectorAll("#group-teams-form .form-control").forEach(ctl=>ctl.addEventListener("change", e=>{
            const container = e.target.closest(".group-teams");
            ElementUtil.executeTask(container.id, ()=>{
                GroupUtil.resetTeams(container);
                GroupUtil.updateGroupTeams();
            });
        }));
    }

    static enhanceMatches()
    {
        const groupSection = document.querySelector("#group");
        const matchContainer = groupSection.querySelector(":scope .group-matches");
        ElementUtil.infiniteScroll(document.querySelector("#group .group-matches .container-indicator-loading-default"),
            ()=>Util.load(matchContainer, e=>GroupUtil.updateMatches(
                document.querySelector("#group"),
                localStorage.getItem("matches-type-group") || "all")));

        document.querySelector("#matches-historical-mmr-group").addEventListener("change",
            e=>window.setTimeout(e=>GroupUtil.updateMatchesView(
                matchContainer,
                Model.DATA.get(VIEW.GROUP).get(VIEW_DATA.SEARCH).matches,
                true),
            1));
        document.querySelector("#matches-type-group").addEventListener("change",
            evt=>window.setTimeout(timeout=>{
                ElementUtil.executeTask(matchContainer.id, ()=>{
                    GroupUtil.resetMatches(matchContainer);
                    Util.load(matchContainer, ()=>GroupUtil.updateMatches(
                        groupSection,
                        localStorage.getItem("matches-type-group") || "all"
                    ));
                });
            }, 1));
    }

    static enhanceClanHistory()
    {
        ElementUtil.infiniteScroll(document.querySelector("#group .group-clan .container-indicator-loading-default"),
            ()=>Util.load(document.querySelector("#group .group-clan"),
                e=>GroupUtil.updateClanHistory(document.querySelector("#group"))));
    }

    static enhanceLinks()
    {
        ElementUtil.ELEMENT_TASKS.set("group-links-tab", GroupUtil.enqueueUpdateLinks);
    }

}

GroupUtil.CACHE = {};
GroupUtil.PARAMETER_KEYS = new Set(["characterId", "accountId", "proPlayerId", "clanId"]);
GroupUtil.LINK_OPERATIONS = new Map([
    [
        SOCIAL_MEDIA.REPLAY_STATS,
        {
            activeCharactersMax: 20,
            load: characters=>GroupUtil.loadCharacterLinks(characters, SOCIAL_MEDIA.REPLAY_STATS),
            generate: links=>{
                if(links.failed || links?.links?.length == 0) return links;

                const ids = links.links.map(link=>encodeURIComponent(link.relativeUrl));
                ids.sort();
                links.data = SOCIAL_MEDIA.REPLAY_STATS.baseUserUrl + "/" + ids.join(",");
                return links;
            }
        }
    ]
]);