// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

class VODUtil
{

    static getMatches(params, cursor)
    {
        const cursorParams = new URLSearchParams(params);
        if(cursor != null) cursorParams.append(cursor.direction.relativePosition, cursor.token);
        cursorParams.append("vod", "");
        const request = `${ROOT_CONTEXT_PATH}api/matches?${cursorParams.toString()}`;
        return Session.beforeRequest()
           .then(n=>fetch(request))
           .then(Session.verifyJsonResponse);
    }

    static updateModel(params, cursor = null)
    {
        if(params.get("minDuration")) params.set("minDuration", params.get("minDuration") * 60);
        if(params.get("maxDuration")) params.set("maxDuration", params.get("maxDuration") * 60);
        return VODUtil.getMatches(params, cursor)
            .then(json=>{
                if(cursor == null) {
                    Model.DATA.get(VIEW.VOD_SEARCH).set(VIEW_DATA.SEARCH, json);
                } else {
                    const data = Model.DATA.get(VIEW.VOD_SEARCH).get(VIEW_DATA.SEARCH);
                    data.result = data.result.concat(json.result);
                    data.navigation = json.navigation;
                }
                Model.DATA.get(VIEW.VOD_SEARCH).set(VIEW_DATA.VAR, {params: params});
                if(json.result.length < MATCH_BATCH_SIZE){
                    document.querySelector("#load-more-matches-vod").classList.add("d-none");
                } else{
                    document.querySelector("#load-more-matches-vod").classList.remove("d-none");
                }

                return Promise.resolve(json);
            });
    }

    static updateView()
    {
        const matches = Model.DATA.get(VIEW.VOD_SEARCH).get(VIEW_DATA.SEARCH).result;
        document.querySelector("#search-result-vod-all").classList.remove("d-none");
        const result = MatchUtil.updateMatchTable(document.querySelector("#matches-vod"), matches,
            (data)=>data.team && data.team.matchParticipant.twitchVodUrl,
            localStorage.getItem("matches-historical-mmr-vod") != "false"
        );
        Model.DATA.get(VIEW.VOD_SEARCH).set(VIEW_DATA.TEAMS, {result: result.teams});

        return Promise.resolve();
    }

    static update(params, cursor = null)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return VODUtil.updateModel(params, cursor)
            .then(VODUtil.updateView)
            .then(e=>{
                Util.setGeneratingStatus(STATUS.SUCCESS);
                const searchParams = new URLSearchParams(params);
                searchParams.append("type", "vod-search");
                const stringParams = searchParams.toString();
                if(!Session.isHistorical) HistoryUtil.pushState({}, document.title, "?" + stringParams + "#search-vod");
                Session.currentSearchParams = stringParams;
                return Promise.resolve();
             })
            .catch(error => Session.onPersonalException(error));
    }

    static loadNextMatches(evt)
    {
        evt.preventDefault();
        const forwardToken = Model.DATA.get(VIEW.VOD_SEARCH).get(VIEW_DATA.SEARCH)
            .navigation[NAVIGATION_DIRECTION.FORWARD.relativePosition];
        VODUtil.update(
            Model.DATA.get(VIEW.VOD_SEARCH).get(VIEW_DATA.VAR).params,
            forwardToken != null ? new Cursor(forwardToken, NAVIGATION_DIRECTION.FORWARD) : null
        );
    }

    static enhance()
    {
        document.querySelector("#load-more-matches-vod").addEventListener("click", VODUtil.loadNextMatches);
        const form = document.getElementById("form-search-vod");
        form.addEventListener("submit", evt=>{
            evt.preventDefault();
            if(!FormUtil.verifyForm(form, form.querySelector(":scope .error-out"))) return;
            VODUtil.update(new URLSearchParams(new FormData(evt.target)));
        });
        document.querySelector("#matches-historical-mmr-vod").addEventListener("change",
            e=>window.setTimeout(VODUtil.updateView, 1));
    }

}