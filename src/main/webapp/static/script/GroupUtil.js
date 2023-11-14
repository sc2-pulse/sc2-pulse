// Copyright (C) 2020-2023 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class GroupUtil
{

    static getGroup(groupParams)
    {
        const request = `${ROOT_CONTEXT_PATH}api/group?${groupParams.toString()}`;
        return Session.beforeRequest()
           .then(n=>fetch(request))
           .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
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
        } else if(group.characters && group.characters.length > 0) {
            text = Util.unmaskName(group.characters[0].members).unmaskedName;
        }
        const count = (group.clans && group.clans.length || 0)
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
        const request = `${ROOT_CONTEXT_PATH}api/group/team?${params.toString()}`;
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
        TeamUtil.updateTeamsTable(container.querySelector(":scope .table-team"), {result: teams});
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
        const request = `${ROOT_CONTEXT_PATH}api/group/character/full?${groupParams.toString()}`;
        return Session.beforeRequest()
           .then(n=>fetch(request))
           .then(resp=>Session.verifyJsonResponse(resp, [200, 404]));
    }

    static updateCharacters(groupParams, section)
    {
        const container = section.querySelector(":scope .group-characters");
        const promise = ()=>GroupUtil.getCharacters(groupParams)
            .then(characters=>{
                CharacterUtil.updateCharacters(container.querySelector(":scope .table-character"), characters);
                return {data: characters, status: LOADING_STATUS.COMPLETE};
            });
        return Util.load(container, promise);
    }

    static getMatches(params)
    {
        const request = `${ROOT_CONTEXT_PATH}api/group/match?${params.toString()}`;
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
                if(!matchesResponse) {
                    return {status: LOADING_STATUS.COMPLETE};
                }
                Model.DATA.get(view).get(VIEW_DATA.SEARCH).matches = matches
                    ? matches.concat(matchesResponse)
                    : matchesResponse;
                GroupUtil.updateMatchesView(container, matchesResponse);
                return {data: matchesResponse, status: LOADING_STATUS.NONE};
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

    static enhance()
    {
        GroupUtil.enhanceTeams();
        ElementUtil.ELEMENT_TASKS.set("group-characters-tab", e=>GroupUtil.updateCharacters(Model.DATA.get(VIEW.GROUP).get(VIEW_DATA.VAR).groupParams, document.querySelector("#group")));
        GroupUtil.enhanceMatches();
        GroupUtil.enhanceClanHistory();
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
        const observer = new IntersectionObserver((intersection)=>{
            if (intersection.some(i=>i.isIntersecting))
                Util.load(matchContainer,
                    e=>GroupUtil.updateMatches(document.querySelector("#group"),
                        localStorage.getItem("matches-type-group") || "all"));
        }, ElementUtil.INFINITE_SCROLL_OPTIONS);
        observer.observe(document.querySelector("#group .group-matches .container-indicator-loading-default"));

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
        const observer = new IntersectionObserver((intersection)=>{
            if (intersection.some(i=>i.isIntersecting))
                Util.load(document.querySelector("#group .group-clan"),
                    e=>GroupUtil.updateClanHistory(document.querySelector("#group")));
        }, ElementUtil.INFINITE_SCROLL_OPTIONS);
        observer.observe(document.querySelector("#group .group-clan .container-indicator-loading-default"));
    }

}