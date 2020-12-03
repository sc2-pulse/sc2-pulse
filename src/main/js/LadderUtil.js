// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class LadderUtil
{

    static getLadderAll()
    {
        const formParams = Util.getFormParameters();
        return Promise.all
        ([
            LadderUtil.updateLadder(formParams),
            StatsUtil.updateQueueStats(formParams),
            StatsUtil.updateLadderStats(formParams),
            StatsUtil.updateLeagueBounds(formParams)
        ]);
    }

    static updateLadderModel(params, formParams, ratingAnchor = 99999, idAnchor = 0, forward = true, count = 1)
    {
        const request = `api/ladder/a/${ratingAnchor}/${idAnchor}/${forward}/${count}?` + formParams;
        const ladderPromise = fetch(request)
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{Model.DATA.get(VIEW.LADDER).set(VIEW_DATA.SEARCH, json); res(json);}));
        return Promise.all([ladderPromise, StatsUtil.updateBundleModel()]);
    }

    static updateLadderView()
    {
        TeamUtil.updateTeamsTable
        (
            document.getElementById("ladder"),
            Model.DATA.get(VIEW.LADDER).get(VIEW_DATA.SEARCH)
        );
        PaginationUtil.updateLadderPaginations();
        document.getElementById("generated-info-all").classList.remove("d-none");
    }

    static updateLadder(formParams, ratingAnchor = 99999, idAnchor = 0, forward = true, count = 1)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        const params =
        {
            form: formParams,
            ratingAnchor: ratingAnchor,
            idAnchor: idAnchor,
            forward: forward,
            count: count
        };
        const searchParams = new URLSearchParams(formParams);
        searchParams.append("type", "ladder");
        for(const [param, val] of Object.entries(params)) if(param != "form") searchParams.append(param, val);
        const stringParams = searchParams.toString();

        return LadderUtil.updateLadderModel(params, formParams, ratingAnchor, idAnchor, forward, count)
            .then(e => new Promise((res, rej)=>{
                LadderUtil.updateLadderView();
                Util.setGeneratingStatus(STATUS.SUCCESS, null, "generated-info-all");
                if(!Session.isHistorical) HistoryUtil.pushState(params, document.title, "?" + searchParams.toString() + "#ladder-top");
                Session.currentSeason = searchParams.get("season");
                Session.currentTeamFormat = EnumUtil.enumOfFullName(searchParams.get("queue"), TEAM_FORMAT);
                Session.currentTeamType = EnumUtil.enumOfName(searchParams.get("team-type"), TEAM_TYPE);
                Session.currentSearchParams = stringParams;
                res();
            }))
            .catch(error => Util.setGeneratingStatus(STATUS.ERROR, error.message));
    }

    static updateMyLadderModel(formParams)
    {
        const ladderPromise = fetch("api/my/following/ladder?" + formParams)
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{
                const result =
                {
                    result: json,
                    meta:
                    {
                        page: 1,
                        perPage: json.length,
                        totalCount: json.length
                    }
                }
                Model.DATA.get(VIEW.FOLLOWING_LADDER).set(VIEW_DATA.SEARCH, result); res(result);}));
        return Promise.all([ladderPromise, StatsUtil.updateBundleModel()]);
    }

    static updateMyLadderView()
    {
        TeamUtil.updateTeamsTable
        (
            document.getElementById("following-ladder"),
            Model.DATA.get(VIEW.FOLLOWING_LADDER).get(VIEW_DATA.SEARCH)
        );
        document.getElementById("following-ladder-container").classList.remove("d-none");
    }

    static updateMyLadder(formParams)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);

        const params = {form: formParams}
        const searchParams = new URLSearchParams(formParams);
        searchParams.append("type", "following-ladder");
        const stringParams = searchParams.toString();

        return LadderUtil.updateMyLadderModel(formParams)
            .then(jsons => new Promise((res, rej)=>{
                LadderUtil.updateMyLadderView();
                Util.setGeneratingStatus(STATUS.SUCCESS, null, "following-ladder");
                if(!Session.isHistorical) HistoryUtil.pushState(params, document.title, "?" + searchParams.toString() + "#personal-following");
                Session.currentPersonalSeasonSeason = searchParams.get("season");
                Session.currentPersonalTeamFormat = EnumUtil.enumOfFullName(searchParams.get("queue"), TEAM_FORMAT);
                Session.currentPersonalTeamType = EnumUtil.enumOfName(searchParams.get("team-type"), TEAM_TYPE);
                Session.currentSearchParams = stringParams;
                res();
            }))
            .catch(error => Util.setGeneratingStatus(STATUS.ERROR, error.message));
    }

    static ladderPaginationPageClick(evt)
    {
        evt.preventDefault();
        const formParams = Util.getFormParameters(evt.target.getAttribute("data-page-number"));
        LadderUtil.updateLadder
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
        form.querySelector(".team-format-picker").addEventListener("change", LadderUtil.onLadderFormTeamFormatChange);
        form.addEventListener("submit", function(evt)
            {
                evt.preventDefault();
                Session.currentSeason = document.getElementById("form-ladder-season-picker").value;
                Session.currentTeamFormat = EnumUtil.enumOfFullName(document.getElementById("form-ladder-team-format-picker").value, TEAM_FORMAT);
                Session.currentTeamType = EnumUtil.enumOfName(document.getElementById("form-ladder-team-type-picker").value, TEAM_TYPE);
                Promise.all([LadderUtil.getLadderAll(), BootstrapUtil.hideCollapsible("form-ladder")])
                    .then(e=>{Util.scrollIntoViewById(form.getAttribute("data-on-success-scroll-to")); HistoryUtil.updateActiveTabs();});
            }
        );
    }

    static enhanceMyLadderForm()
    {
        const form = document.getElementById("form-following-ladder");
        form.querySelector(".team-format-picker").addEventListener("change", LadderUtil.onLadderFormTeamFormatChange);
        form.addEventListener
        (
            "submit",
             function(evt)
            {
                evt.preventDefault();
                Session.currentPersonalSeason = document.getElementById("form-following-ladder-season-picker").value;
                Session.currentPersonalTeamFormat = EnumUtil.enumOfFullName(document.getElementById("form-following-ladder-team-format-picker").value, TEAM_FORMAT);
                Session.currentPersonalTeamType = EnumUtil.enumOfName(document.getElementById("form-following-ladder-team-type-picker").value, TEAM_TYPE);
                Promise.all([LadderUtil.updateMyLadder(Util.urlencodeFormData(new FormData(document.getElementById("form-following-ladder"))), BootstrapUtil.hideCollapsible("form-following-ladder"))])
                    .then(e=>{Util.scrollIntoViewById(form.getAttribute("data-on-success-scroll-to")); HistoryUtil.updateActiveTabs();});
            }
        );
    }

    static onLadderFormTeamFormatChange(e)
    {
        const teamTypeSelect = e.target.closest("form").querySelector('.team-type-picker');
        const randomSelectOption = teamTypeSelect.querySelector(':scope option[value="RANDOM"]');
        const teamFormat = EnumUtil.enumOfFullName(e.target.value, TEAM_FORMAT);
        if(teamFormat == TEAM_FORMAT._1V1 || teamFormat == TEAM_FORMAT.ARCHON)
        {
            randomSelectOption.setAttribute("disabled", "disabled");
            teamTypeSelect.value = TEAM_TYPE.ARRANGED.name.toUpperCase();
        }
        else
        {
            randomSelectOption.removeAttribute("disabled");
        }
    }

}
