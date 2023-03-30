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

    static updateLadderModel(params, formParams, ratingAnchor = 99999, idAnchor = 0, count = 1)
    {
        return LadderUtil.chainLadderPromise(params, formParams, ratingAnchor, idAnchor, count);
    }

    static chainLadderPromise(params, formParams, ratingAnchor, idAnchor, count, isLastPage = false)
    {
        const request = `${ROOT_CONTEXT_PATH}api/ladder/a/${ratingAnchor}/${idAnchor}/${count}?` + formParams;
        const ladderPromise = Session.beforeRequest()
        .then(n=>fetch(request))
        .then(Session.verifyJsonResponse)
        .then(json => {
            json.meta.isLastPage = isLastPage;
            if(json.result.length == 0) {
                count--;
                params.count = count;
                if(count < 1) return LadderUtil.setLastOrCurrentLadder(params, json);

                return LadderUtil.chainLadderPromise(params, formParams, ratingAnchor, idAnchor, count, true)
            }

             return new Promise((res, rej)=>{
                Model.DATA.get(VIEW.LADDER).set(VIEW_DATA.SEARCH, json);
                Model.DATA.get(VIEW.LADDER).set(VIEW_DATA.VAR, params);
                res(params);})
        });
         return ladderPromise;
    }

    static setLastOrCurrentLadder(params, json)
    {
        const data = Model.DATA.get(VIEW.LADDER).get(VIEW_DATA.SEARCH);
        const dataParams = Model.DATA.get(VIEW.LADDER).get(VIEW_DATA.VAR);
        if(data != null)
        {
            const dataSearchParams = new URLSearchParams(dataParams.form);
            const searchParams = new URLSearchParams(params.form);
            dataSearchParams.delete("page");
            searchParams.delete("page");
            if(dataSearchParams.toString() == searchParams.toString())
            {
                Object.assign(params,  dataParams);
                data.meta.isLastPage = true;
            }
            else
            {
                params.count++;
                Model.DATA.get(VIEW.LADDER).set(VIEW_DATA.SEARCH, json);
                Model.DATA.get(VIEW.LADDER).set(VIEW_DATA.VAR, params);
            }
        }
        else
        {
            params.count++;
            Model.DATA.get(VIEW.LADDER).set(VIEW_DATA.SEARCH, json);
            Model.DATA.get(VIEW.LADDER).set(VIEW_DATA.VAR, params);
        }
        return params;
    }

    static updateLadderView()
    {
        const searchData = Model.DATA.get(VIEW.LADDER).get(VIEW_DATA.SEARCH);
        TeamUtil.updateTeamsTable(document.getElementById("ladder"), searchData);
        PaginationUtil.PAGINATIONS.get("ladder").update(searchData);
        document.getElementById("generated-info-all").classList.remove("d-none");
    }

    static updateLadder(formParams, ratingAnchor = 99999, idAnchor = 0, count = 1)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        const params =
        {
            form: formParams,
            ratingAnchor: ratingAnchor,
            idAnchor: idAnchor,
            count: count
        };

        return LadderUtil.updateLadderModel(params, formParams, ratingAnchor, idAnchor, count)
            .then(e => {
                const searchParams = new URLSearchParams(e.form);
                searchParams.append("type", "ladder");
                for(const [param, val] of Object.entries(params)) if(param != "form") searchParams.append(param, val);
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
        const formParams = Util.getFormParameters(evt.target.getAttribute("data-page-number"));
        LadderUtil.updateLadder
        (
            formParams,
            evt.target.getAttribute("data-page-rating-anchor"),
            evt.target.getAttribute("data-page-id-anchor"),
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
