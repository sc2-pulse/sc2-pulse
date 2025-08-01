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

    static updateLadderModel(params, formParams, ratingCursor = 99999, idCursor = 0, sortingOrder = SORTING_ORDER.DESC)
    {
        return LadderUtil.chainLadderPromise(params, formParams, ratingCursor, idCursor, sortingOrder);
    }

    static chainLadderPromise(params, formParams, ratingCursor, idCursor, sortingOrder = SORTING_ORDER.DESC)
    {
        const allParams = new URLSearchParams(params.form);
        allParams.append("ratingCursor", ratingCursor);
        allParams.append("idCursor", idCursor);
        allParams.append("sortingOrder", sortingOrder.fullName);

        const request = `${ROOT_CONTEXT_PATH}api/ladder/a?` + allParams.toString();
        const ladderPromise = Session.beforeRequest()
        .then(n=>fetch(request))
        .then(Session.verifyJsonResponse)
        .then(json => {
            json.meta = {page: ratingCursor == 99999
                ? PaginationUtil.CURSOR_DISABLED_PREV_PAGE_NUMBER
                : PaginationUtil.CURSOR_PAGE_NUMBER};
            if(json.result.length == 0) {
                const searchData = Model.DATA.get(VIEW.LADDER).get(VIEW_DATA.SEARCH);
                if(searchData) {
                    searchData.meta.page = sortingOrder == SORTING_ORDER.DESC
                        ? PaginationUtil.CURSOR_DISABLED_NEXT_PAGE_NUMBER
                        : PaginationUtil.CURSOR_DISABLED_PREV_PAGE_NUMBER;
                    if(sortingOrder == SORTING_ORDER.DESC) searchData.meta.isLastPage = true;
                    return Model.DATA.get(VIEW.LADDER).get(VIEW_DATA.VAR);
                }
            }
            Model.DATA.get(VIEW.LADDER).set(VIEW_DATA.SEARCH, json);
            Model.DATA.get(VIEW.LADDER).set(VIEW_DATA.VAR, params);
            return params;
        });
         return ladderPromise;
    }

    static updateLadderView()
    {
        const searchData = Model.DATA.get(VIEW.LADDER).get(VIEW_DATA.SEARCH);
        TeamUtil.updateTeamsTable(document.getElementById("ladder"), searchData);
        PaginationUtil.PAGINATIONS.get("ladder").update(searchData);
        document.getElementById("generated-info-all").classList.remove("d-none");
    }

    static updateLadder(formParams, ratingCursor = 99999, idCursor = 0, sortingOrder = SORTING_ORDER.DESC)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        const params =
        {
            form: formParams,
            ratingCursor: ratingCursor,
            idCursor: idCursor,
            sortingOrder: sortingOrder.fullName
        };

        return LadderUtil.updateLadderModel(params, formParams, ratingCursor, idCursor, sortingOrder)
            .then(e => {
                const searchParams = new URLSearchParams(e.form);
                searchParams.append("type", "ladder");
                searchParams.append("idCursor", e.idCursor);
                searchParams.append("ratingCursor", e.ratingCursor);
                searchParams.append("sortingOrder", e.sortingOrder);
                const stringParams = searchParams.toString();

                LadderUtil.updateLadderView();
                Util.setGeneratingStatus(STATUS.SUCCESS, null, "generated-info-all");
                if(!Session.isHistorical) HistoryUtil.pushState(params, document.title, "?" + searchParams.toString() + "#ladder-top");
                Session.currentSeason = searchParams.get("season");
                Session.currentTeamFormat = EnumUtil.enumOfFullName(searchParams.get("queue"), TEAM_FORMAT);
                Session.currentTeamType = EnumUtil.enumOfName(searchParams.get("team-type"), TEAM_TYPE);
                Session.currentSearchParams = stringParams;
            })
            .catch(error => Session.onPersonalException(error));
    }

    static updateMyLadderModel(formParams)
    {
        return Session.beforeRequest()
            .then(n=>fetch(ROOT_CONTEXT_PATH + "api/my/following/ladder?" + formParams))
            .then(Session.verifyJsonResponse)
            .then(json => {
                const result =
                {
                    result: json,
                    meta:
                    {
                        page: 1,
                        perPage: json.length,
                        totalCount: json.length
                    }
                };
                Model.DATA.get(VIEW.FOLLOWING_LADDER).set(VIEW_DATA.SEARCH, result);
                return result;
            });
    }

    static updateMyLadderView()
    {
        TeamUtil.updateTeamsTable
        (
            document.getElementById("following-ladder"),
            Model.DATA.get(VIEW.FOLLOWING_LADDER).get(VIEW_DATA.SEARCH)
        );
        document.getElementById("following-ladder").classList.remove("d-none");
    }

    static updateMyLadder(formParams)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);

        const params = {form: formParams}
        const searchParams = new URLSearchParams(formParams);
        searchParams.append("type", "following-ladder");
        const stringParams = searchParams.toString();

        return LadderUtil.updateMyLadderModel(formParams)
            .then(jsons =>{
                LadderUtil.updateMyLadderView();
                Util.setGeneratingStatus(STATUS.SUCCESS, null, "following-ladder");
                if(!Session.isHistorical) HistoryUtil.pushState(params, document.title, "?" + searchParams.toString() + "#personal-following");
                Session.currentPersonalSeasonSeason = searchParams.get("season");
                Session.currentPersonalTeamFormat = EnumUtil.enumOfFullName(searchParams.get("queue"), TEAM_FORMAT);
                Session.currentPersonalTeamType = EnumUtil.enumOfName(searchParams.get("team-type"), TEAM_TYPE);
                Session.currentSearchParams = stringParams;
            })
            .catch(error => Session.onPersonalException(error));
    }

    static ladderPaginationPageClick(evt)
    {
        evt.preventDefault();
        const formParams = Util.getFormParameters();
        LadderUtil.updateLadder
        (
            formParams,
            evt.target.getAttribute("data-page-rating-cursor"),
            evt.target.getAttribute("data-page-id-cursor"),
            evt.target.getAttribute("data-page-count") > 0 ? SORTING_ORDER.DESC : SORTING_ORDER.ASC
        ).then(e=>Util.scrollIntoViewById(evt.target.getAttribute("href").substring(1)));
    }

    static enhanceLadderForm()
    {
        const form = document.getElementById("form-ladder");
        form.querySelector(".team-format-picker").addEventListener("change", LadderUtil.onLadderFormTeamFormatChange);
        form.addEventListener("submit", function(evt)
            {
                evt.preventDefault();
                if(!FormUtil.verifyForm(form, form.querySelector(":scope .error-out"))) return;
                Session.currentSeason = document.getElementById("form-ladder-season-picker").value;
                Session.currentTeamFormat = EnumUtil.enumOfFullName(document.getElementById("form-ladder-team-format-picker").value, TEAM_FORMAT);
                Session.currentTeamType = EnumUtil.enumOfName(document.getElementById("form-ladder-team-type-picker").value, TEAM_TYPE);
                LadderUtil.getLadderAll()
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
                if(!FormUtil.verifyForm(form, form.querySelector(":scope .error-out"))) return;
                Session.currentPersonalSeason = document.getElementById("form-following-ladder-season-picker").value;
                Session.currentPersonalTeamFormat = EnumUtil.enumOfFullName(document.getElementById("form-following-ladder-team-format-picker").value, TEAM_FORMAT);
                Session.currentPersonalTeamType = EnumUtil.enumOfName(document.getElementById("form-following-ladder-team-type-picker").value, TEAM_TYPE);
                LadderUtil.updateMyLadder(Util.urlencodeFormData(new FormData(document.getElementById("form-following-ladder"))))
                    .then(e=>{Util.scrollIntoViewById(form.getAttribute("data-on-success-scroll-to")); HistoryUtil.updateActiveTabs();});
            }
        );
    }

    static restoreLadderFormState(form, params)
    {
        for(const checkbox of form.querySelectorAll('input[type="checkbox"]')) checkbox.checked = false;
        ElementUtil.changeInputValue(form.querySelector("#" + form.id + "-season-picker"), params.get("season"));
        ElementUtil.changeInputValue(form.querySelector("#" + form.id + "-team-format-picker"), params.get("queue"));
        ElementUtil.changeInputValue(form.querySelector("#" + form.id + "-team-type-picker"), params.get("team-type"));
        for(const entry of params.entries())
        {
            const checkbox = form.querySelector("#" + form.id + "-" + entry[0] + '[type="checkbox"]');
            if(checkbox != null) ElementUtil.changeInputValue(checkbox, true);
        }
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
