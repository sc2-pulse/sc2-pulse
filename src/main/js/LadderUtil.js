// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class LadderUtil
{

    static getLadderAll()
    {
        const formParams = Util.getFormParameters();
        return Promise.all([LadderUtil.getLadder(formParams), StatsUtil.getLadderStats(formParams), StatsUtil.getLeagueBounds(formParams)]);
    }

    static getLadder(formParams, ratingAnchor = 99999, idAnchor = 0, forward = true, count = 1)
    {
        Util.setGeneratingStatus("begin");
        const tabs = new URLSearchParams(window.location.search).getAll("t");
        const params =
        {
            form: formParams,
            ratingAnchor: ratingAnchor,
            idAnchor: idAnchor,
            forward: forward,
            count: count
        }
        const searchParams = new URLSearchParams(formParams);
        searchParams.append("type", "ladder");
        for(const [param, val] of Object.entries(params)) if(param != "form") searchParams.append(param, val);
        const stringParams = searchParams.toString();
        for(const tab of tabs) searchParams.append("t", tab);

        const request = `api/ladder/a/${ratingAnchor}/${idAnchor}/${forward}/${count}?` + formParams;
        return fetch(request)
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{
                LadderUtil.updateLadder(json);
                Util.setGeneratingStatus("success", null, "generated-info-all");
                if(!Session.isHistorical) HistoryUtil.pushState(params, document.title, "?" + searchParams.toString());
                Session.currentSeason = searchParams.get("season");
                Session.currentTeamFormat = EnumUtil.enumOfFullName(searchParams.get("queue"), TEAM_FORMAT);
                Session.currentTeamType = EnumUtil.enumOfName(searchParams.get("team-type"), TEAM_TYPE);
                Session.currentSearchParams = stringParams;
                HistoryUtil.updateActiveTabs();
                res();}))
            .catch(error => Util.setGeneratingStatus("error", error.message));
    }

    static updateLadder(searchResult)
    {
        Session.currentLadder = searchResult;
        TeamUtil.updateTeamsTable(document.getElementById("ladder"), searchResult);
        PaginationUtil.updateLadderPaginations();
        document.getElementById("generated-info-all").classList.remove("d-none");
    }

    static getMyLadder(formParams)
    {
        Util.setGeneratingStatus("begin");

        const tabs = new URLSearchParams(window.location.search).getAll("t");
        const params = {form: formParams}
        const searchParams = new URLSearchParams(formParams);
        searchParams.append("type", "following-ladder");
        const stringParams = searchParams.toString();
        for(const tab of tabs) searchParams.append("t", tab);

        return fetch("api/my/following/ladder?" + formParams)
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{
                LadderUtil.updateMyLadder(json);
                Util.setGeneratingStatus("success", null, "following-ladder");
                if(!Session.isHistorical) HistoryUtil.pushState(params, document.title, "?" + searchParams.toString());
                Session.currentPersonalSeasonSeason = searchParams.get("season");
                Session.currentPersonalTeamFormat = EnumUtil.enumOfFullName(searchParams.get("queue"), TEAM_FORMAT);
                Session.currentPersonalTeamType = EnumUtil.enumOfName(searchParams.get("team-type"), TEAM_TYPE);
                Session.currentSearchParams = stringParams;
                res();
            }))
            .catch(error => Util.setGeneratingStatus("error", error.message));
    }

    static updateMyLadder(searchResult)
    {
        const result =
        {
            result: searchResult,
            meta:
            {
                page: 1,
                perPage: searchResult.length
            }
        }
        TeamUtil.updateTeamsTable(document.getElementById("following-ladder"), result);
        document.getElementById("following-ladder-container").classList.remove("d-none");
    }

    static ladderPaginationPageClick(evt)
    {
        evt.preventDefault();
        const formParams = Util.getFormParameters(evt.target.getAttribute("data-page-number"));
        LadderUtil.getLadder
        (
            formParams,
            evt.target.getAttribute("data-page-rating-anchor"),
            evt.target.getAttribute("data-page-id-anchor"),
            evt.target.getAttribute("data-page-forward"),
            evt.target.getAttribute("data-page-count")
        ).then(e=>Util.scrollIntoViewById(evt.target.getAttribute("href").substring(1)));
    }

    static enhanceLadderForm()
    {
        const form = document.getElementById("form-ladder");
        form.addEventListener("submit", function(evt)
            {
                evt.preventDefault();
                Session.currentSeason = document.getElementById("form-ladder-season-picker").value;
                Session.currentTeamFormat = EnumUtil.enumOfFullName(document.getElementById("form-ladder-team-format-picker").value, TEAM_FORMAT);
                Session.currentTeamType = EnumUtil.enumOfName(document.getElementById("form-ladder-team-type-picker").value, TEAM_TYPE);
                Promise.all([LadderUtil.getLadderAll(), BootstrapUtil.hideCollapsible("form-ladder")])
                    .then(e=>Util.scrollIntoViewById(form.getAttribute("data-on-success-scroll-to")));
            }
        );
    }

    static enhanceMyLadderForm()
    {
        const form = document.getElementById("form-following-ladder");
        form.addEventListener
        (
            "submit",
             function(evt)
            {
                evt.preventDefault();
                Session.currentPersonalSeason = document.getElementById("form-following-ladder-season-picker").value;
                Session.currentPersonalTeamFormat = EnumUtil.enumOfFullName(document.getElementById("form-following-ladder-team-format-picker").value, TEAM_FORMAT);
                Session.currentPersonalTeamType = EnumUtil.enumOfName(document.getElementById("form-following-ladder-team-type-picker").value, TEAM_TYPE);
                Promise.all([LadderUtil.getMyLadder(Util.urlencodeFormData(new FormData(document.getElementById("form-following-ladder"))), BootstrapUtil.hideCollapsible("form-following-ladder"))])
                    .then(e=>Util.scrollIntoViewById(form.getAttribute("data-on-success-scroll-to")));
            }
        );
    }

}
