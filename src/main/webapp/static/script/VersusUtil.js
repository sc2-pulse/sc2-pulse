// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

class VersusUtil
{

    static updateVersusWithNewType(type)
    {
        const varData = Model.DATA.get(VIEW.VERSUS).get(VIEW_DATA.VAR);
        const params = varData.params;
        params.delete("type");
        if(type && type != "all") params.append("type", type);
        return VersusUtil.updateFromParams(VersusUtil.apiParamsToUrlParams(params));
    }

    static updateFromParams(params)
    {
        return VersusUtil.updateVersus(
            params.getAll("clan1"), params.getAll("team1"),
            params.getAll("clan2"), params.getAll("team2"),
            params.getAll("matchType")
        );
    }

    static updateVersus(clans1, teams1, clans2, teams2, types)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return VersusUtil.updateVersusModel(clans1, teams1, clans2, teams2, types)
            .then(VersusUtil.updateVersusView)
            .then(e=>{
                Util.setGeneratingStatus(STATUS.SUCCESS);
                const varData = Model.DATA.get(VIEW.VERSUS).get(VIEW_DATA.VAR);
                const urlParams = VersusUtil.apiParamsToUrlParams(varData.params);
                const stringParams = urlParams.toString();
                HistoryUtil.pushState({}, document.title, "?" + stringParams + "#versus");
                Session.currentSearchParams = stringParams;
            })
            .then(e=>BootstrapUtil.showModal("versus-modal"))
            .catch(error => Session.onPersonalException(error));
    }

    static updateVersusModel(clans1, teams1, clans2, teams2, types)
    {
        const params = new URLSearchParams();
        Util.addParams(params, "clan1", clans1);
        Util.addParams(params, "team1", teams1);
        Util.addParams(params, "clan2", clans2);
        Util.addParams(params, "team2", teams2);
        Util.addParams(params, "type", types);
        const request = ROOT_CONTEXT_PATH + "api/versus/common?" + params.toString();
        return  Session.beforeRequest()
            .then(n=>fetch(request))
            .then(Session.verifyJsonResponse)
            .then(json=>{
                Model.DATA.get(VIEW.VERSUS).set(VIEW_DATA.SEARCH, json);
                Model.DATA.get(VIEW.VERSUS).set(VIEW_DATA.VAR, {params: params});
                VersusUtil.initDynamicViews();
                Model.DATA.get("versusClans1").set(VIEW_DATA.SEARCH, {searchResult: {result: json.clansGroup1}});
                Model.DATA.get("versusTeams1").set(VIEW_DATA.TEAMS, {result: json.teamsGroup1});
                Model.DATA.get("versusClans2").set(VIEW_DATA.SEARCH, {searchResult: {result: json.clansGroup2}});
                Model.DATA.get("versusTeams2").set(VIEW_DATA.TEAMS, {result: json.teamsGroup2});
                return json;
            });
    }

    static initDynamicViews()
    {
        if(Model.DATA.get("versusClans1")) return;
        Model.DATA.set("versusClans1", new Map());
        Model.DATA.set("versusTeams1", new Map());
        Model.DATA.set("versusClans2", new Map());
        Model.DATA.set("versusTeams2", new Map());
    }

    static updateVersusView()
    {
        const searchResult = Model.DATA.get(VIEW.VERSUS).get(VIEW_DATA.SEARCH);
        const params = Model.DATA.get(VIEW.VERSUS).get(VIEW_DATA.VAR);
        VersusUtil.updateVersusHeader(searchResult);
        const versusLinkParams = VersusUtil.apiParamsToUrlParams(params.params);
        versusLinkParams.delete("team2");
        versusLinkParams.delete("clan2");
        const result = MatchUtil.updateMatchTable(document.querySelector("#matches-versus"), searchResult.matches.result,
            (data)=>Number.isInteger(data) ? false
                : (searchResult.clansGroup1 && searchResult.clansGroup1.some(clan=>clan.id == data.member.character.clanId))
                    || (searchResult.teamsGroup1 && searchResult.teamsGroup1.some(team=>
                        team.queueType == data.team.queueType
                        && team.region == data.team.region
                        && team.legacyId == data.team.legacyId)),
            localStorage.getItem("matches-historical-mmr-versus") != "false",
            `${ROOT_CONTEXT_PATH}versus?${versusLinkParams.toString()}`
        );
        Model.DATA.get(VIEW.VERSUS).set(VIEW_DATA.TEAMS, {result: result.teams});
        if(result.validMatches.length >= MATCH_BATCH_SIZE) {
            document.querySelector("#load-more-matches-versus").classList.remove("d-none");
        }
        else {
            document.querySelector("#load-more-matches-versus").classList.add("d-none");
        }
        const formParams = new URLSearchParams();
        let type =  params.params.get("type");
        type = type ? type : "all";
        formParams.append("matches-type", type);
        params.changedMatchType = (localStorage.getItem("matches-type-versus") || "all") != (params.params.get("type") || "all");
        FormUtil.setFormState(document.querySelector("#matches-form-versus"), formParams);
        return Promise.resolve();
    }

    static updateVersusHeader(searchResult)
    {
        VersusUtil.updateVersusClans(document.querySelector("#versus-clans1"), searchResult.clansGroup1);
        VersusUtil.updateVersusTeams(document.querySelector("#versus-teams1"), searchResult.teamsGroup1);
        VersusUtil.updateVersusClans(document.querySelector("#versus-clans2"), searchResult.clansGroup2);
        VersusUtil.updateVersusTeams(document.querySelector("#versus-teams2"), searchResult.teamsGroup2);
        document.querySelector("#versus-result-1").textContent = searchResult.summary.wins;
        document.querySelector("#versus-result-2").textContent = searchResult.summary.losses;
    }

    static updateVersusTeams(table, teams)
    {
        TeamUtil.updateTeamsTable(table, {result: teams}, true, "xl");
        if(teams.length > 0) {
            table.classList.remove("d-none");
        } else {
            table.classList.add("d-none");
        }
    }

    static updateVersusClans(table, clans)
    {
        ClanUtil.updateClanTable(table, clans);
        if(clans.length > 0) {
            table.classList.remove("d-none");
        } else {
            table.classList.add("d-none");
        }
    }

    static loadNextMatches(evt)
    {
        evt.preventDefault();
        Util.setGeneratingStatus(STATUS.BEGIN);
        const matches = Model.DATA.get(VIEW.VERSUS).get(VIEW_DATA.SEARCH).matches.result;
        const lastMatch = matches[matches.length - 1];
        VersusUtil.loadNextMatchesModel(lastMatch.match.date, lastMatch.match.type, lastMatch.map.id, lastMatch.match.region, Model.DATA.get(VIEW.VERSUS).get(VIEW_DATA.VAR).params)
            .then(json => {
                if(json.result.length > 0) VersusUtil.updateVersusView();
                if(json.result.length < MATCH_BATCH_SIZE) document.querySelector("#load-more-matches-versus").classList.add("d-none");
                Util.setGeneratingStatus(STATUS.SUCCESS);
            })
            .catch(error => Session.onPersonalException(error));
    }

    static loadNextMatchesModel(dateCursor, typeCursor, mapCursor, regionCursor, params)
    {
        const allParams = new URLSearchParams(params);
        allParams.append("dateCursor", dateCursor);
        allParams.append("typeCursor", typeCursor);
        allParams.append("mapCursor", mapCursor);
        allParams.append("regionCursor", regionCursor);
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/versus/matches?${allParams.toString()}`))
            .then(Session.verifyJsonResponse)
            .then(json => {
                const searchResult = Model.DATA.get(VIEW.VERSUS).get(VIEW_DATA.SEARCH);
                searchResult.matches.result = searchResult.matches.result.concat(json.result);
                return json;
            });
    }

    static enhance()
    {
        document.querySelector("#load-more-matches-versus").addEventListener("click", VersusUtil.loadNextMatches);
        document.querySelector("#matches-historical-mmr-versus").addEventListener("change",
            e=>window.setTimeout(VersusUtil.updateVersusView, 1));
        document.querySelector("#matches-type-versus").addEventListener("change",
            e=>window.setTimeout(q=>{
                const varData = Model.DATA.get(VIEW.VERSUS).get(VIEW_DATA.VAR);
                if(varData.changedMatchType) {
                    varData.changedMatchType = false;
                    return;
                }
                VersusUtil.updateVersusWithNewType(localStorage.getItem("matches-type-versus"));
            }, 1));
    }

    static generateVersusTitle()
    {
        const buf = [];
        const defaultTitle = "Versus";
        const searchResult = Model.DATA.get(VIEW.VERSUS).get(VIEW_DATA.SEARCH);
        if(!searchResult) return defaultTitle;
        if(searchResult.clansGroup1) searchResult.clansGroup1.forEach(c=>buf.push(ClanUtil.generateClanName(c)));
        if(searchResult.teamsGroup1) searchResult.teamsGroup1.forEach(t=>buf.push(TeamUtil.generateTeamName(t, false)));
        buf.push("VS")
        if(searchResult.clansGroup2) searchResult.clansGroup2.forEach(c=>buf.push(ClanUtil.generateClanName(c)));
        if(searchResult.teamsGroup2) searchResult.teamsGroup2.forEach(t=>buf.push(TeamUtil.generateTeamName(t, false)));
        return buf.length > 1 ? buf.join(" ") : defaultTitle;
    }

    static apiParamsToUrlParams(apiParams)
    {
        const urlParams = new URLSearchParams(apiParams.toString());
        if(urlParams.get("type")) urlParams.append("matchType", urlParams.get("type"));
        urlParams.set("type", "versus");
        urlParams.set("m", "1");
        return urlParams;
    }

    static createEmptyVersusLink()
    {
        return ElementUtil.createElement(
            "a",
            null,
            "font-weight-bold d-inline-block mr-3 link-versus",
            "VS",
            [["rel", "noopener"], ["target", "_blank"], ["role", "button"]]
        );
    }

    static getVersusUrl(itemName = "matches-type-versus")
    {
        const type = localStorage.getItem(itemName);
        return `${ROOT_CONTEXT_PATH}?type=versus&m=1${type && type != "all" ? "&matchType=" + encodeURIComponent(type) : ''}`;
    }

    static onVersusLinkClick(evt)
    {
        evt.preventDefault();
        const href = evt.target.getAttribute("href");
        VersusUtil.updateFromParams(new URLSearchParams(href.substring(href.indexOf("?"))));
    }

}