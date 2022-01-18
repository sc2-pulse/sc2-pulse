// Copyright (C) 2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

class ClanUtil
{

    static updateClanSearchModel(formParams, cursor, cursorValue, idCursor = 0, page = 0, pageDiff = 1)
    {
        const searchParams = new URLSearchParams("?" + formParams);
        if(!cursorValue) cursorValue = searchParams.get(pageDiff < 0 ? cursor.minParamName : cursor.maxParamName);

        const previousData = Model.DATA.get(VIEW.CLAN_SEARCH).get(VIEW_DATA.SEARCH);
        const params =
        {
            formParams: formParams,
            cursor: cursor,
            cursorValue: cursorValue,
            idCursor: idCursor,
            page: page,
            pageDiff: pageDiff
        }
        if(!previousData || (page == 0 && pageDiff == 1));
            ClanUtil.updateClanSearchPaginationConfig
            (
                searchParams.get(params.cursor.minParamName),
                searchParams.get(params.cursor.maxParamName),
                params.cursor.getter
            );

        const tagOrName = searchParams.get("tagOrName");
        const byTagOrName = tagOrName && tagOrName.length > 0;
        const request = byTagOrName
            ? `${ROOT_CONTEXT_PATH}api/clan/tag-or-name/${encodeURIComponent(tagOrName)}`
            : `${ROOT_CONTEXT_PATH}api/clan/cursor/${cursor.fullName}/${cursorValue}/${idCursor}/${page}/${pageDiff}?${formParams}`;
        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(Session.verifyJsonResponse)
            .then(json=>ClanUtil.extractModelData(json, params, byTagOrName));
    }

    static extractModelData(json, params, byTagOrName)
    {
        return new Promise((res, rej)=>{
            if(byTagOrName) {
                Model.DATA.get(VIEW.CLAN_SEARCH).set(VIEW_DATA.SEARCH, {searchResult: PaginationUtil.resultToPagedResult(json), params: params});
            } else {
                const empty = json.result.length == 0;
                if(empty) {
                    if(params.page == 0) {
                        Model.DATA.get(VIEW.CLAN_SEARCH).set(VIEW_DATA.SEARCH, {searchResult: json, params: params});
                    } else {
                        const data = Model.DATA.get(VIEW.CLAN_SEARCH).get(VIEW_DATA.SEARCH);
                        data.searchResult.empty = true;
                        data.searchResult.meta.pageDiff = params.pageDiff;
                    }
                }
                else {
                    json.empty = false;
                    Model.DATA.get(VIEW.CLAN_SEARCH).set(VIEW_DATA.SEARCH, {searchResult: json, params: params});
                }
            }
            res(Model.DATA.get(VIEW.CLAN_SEARCH).get(VIEW_DATA.SEARCH));
        });
    }

    static updateClanSearchPaginationConfig(min, max, getter)
    {
        PaginationUtil.PAGINATIONS.set("clan-search",
            new Pagination
            (
                ".pagination-clan-search",
                [
                    {name: "cursor-value", min: min - 1, max: max + 1, getter: getter},
                    {name: "id-cursor", min: 0, max: 1, getter: (t)=>t.id}
                ],
                ClanUtil.clanSearchPaginationPageClick
            )
        );
    }

    static updateClanSearchView()
    {
        const searchData = Model.DATA.get(VIEW.CLAN_SEARCH).get(VIEW_DATA.SEARCH);
        ClanUtil.updateClanTable(document.querySelector("#search-result-clan"), searchData.searchResult.result);
        const pagination = PaginationUtil.PAGINATIONS.get("clan-search").update(searchData.searchResult);
        document.getElementById("search-result-clan-all").classList.remove("d-none");
    }

    static updateClanTable(table, clans)
    {
        const tBody = table.querySelector(":scope tbody")
        ElementUtil.removeChildren(tBody);
        for(const clan of clans)
        {
            const tr = tBody.insertRow();
            if(clan.avgLeagueType) {
                const league = EnumUtil.enumOfId(clan.avgLeagueType, LEAGUE);
                tr.insertCell().appendChild(TeamUtil.createLeagueDivFromEnum(league, null));
            } else {
                tr.insertCell();
            }
            tr.insertCell().appendChild(ElementUtil.createImage("flag/", clan.region.toLowerCase(), "table-image-long"));
            tr.insertCell().appendChild(ClanUtil.createClanTagElem(clan));
            tr.insertCell().textContent = clan.activeMembers;
            tr.insertCell().textContent = clan.members;
            tr.insertCell().textContent = clan.avgRating;
            const gamesPerActiveMember = Math.floor(clan.games / clan.activeMembers);
            tr.insertCell().textContent = `${gamesPerActiveMember} (${Util.DECIMAL_FORMAT.format(gamesPerActiveMember / CLAN_STATS_DEPTH_DAYS)})`;
            const nameCell = tr.insertCell();
            nameCell.classList.add("cell-main", "complex");
            nameCell.textContent = clan.name;
        }
    }

    static updateClanSearch(formParams, cursor, cursorValue, idCursor = 0, page = 0, pageDiff = 1)
    {
        if(!cursor.fullName) cursor = EnumUtil.enumOfFullName(cursor, CLAN_CURSOR);
        Util.setGeneratingStatus(STATUS.BEGIN);
        return ClanUtil.updateClanSearchModel(formParams, cursor, cursorValue, idCursor, page, pageDiff)
            .then(e => new Promise((res, rej)=>{
                const searchParams = new URLSearchParams(formParams);
                searchParams.append("type", "clan-search");
                searchParams.append("sortBy", cursor.fullName);
                searchParams.append("cursorValue", cursorValue);
                searchParams.append("idCursor", idCursor);
                searchParams.append("page", page);
                searchParams.append("pageDiff", pageDiff);
                const stringParams = searchParams.toString();

                ClanUtil.updateClanSearchView();
                Util.scrollIntoViewById("search-result-clan-all");
                Util.setGeneratingStatus(STATUS.SUCCESS);
                if(!Session.isHistorical) HistoryUtil.pushState({}, document.title, "?" + stringParams + "#search-clan");
                Session.currentSearchParams = stringParams;
                res();
            }))
            .catch(error => Session.onPersonalException(error));
    }

    static clanSearchPaginationPageClick(evt)
    {
        evt.preventDefault();
        const searchData = Model.DATA.get(VIEW.CLAN_SEARCH).get(VIEW_DATA.SEARCH);

        ClanUtil.updateClanSearch
        (
            searchData.params.formParams,
            searchData.params.cursor,
            evt.target.getAttribute("data-page-cursor-value"),
            evt.target.getAttribute("data-page-id-cursor"),
            evt.target.getAttribute("data-page-number"),
            evt.target.getAttribute("data-page-count")
        );
    }

    static enhanceClanSearchForm()
    {
        const form = document.querySelector("#form-search-clan");
        form.addEventListener("submit", evt=>
        {
            evt.preventDefault();
            if(!FormUtil.verifyForm(form, form.querySelector(":scope .error-out"))) return;
            const formData = new FormData(form);
            const cursor = formData.get("sortBy") ? EnumUtil.enumOfFullName(formData.get("sortBy"), CLAN_CURSOR) : CLAN_CURSOR.ACTIVE_MEMBERS;
            for(const p of ClanUtil.REQUIRED_CURSOR_PARAMETERS) formData.delete(p);
            ClanUtil.updateClanSearch(Util.urlencodeFormData(formData), cursor, CLAN_MAX_ADDITIONAL_CURSOR_FILTER);
        });
    }

    static createClanTagElem(clan)
    {
        const a = ElementUtil.createElement("a", null, "clan-auto-search", clan.tag);
        a.setAttribute("href", encodeURI(`${ROOT_CONTEXT_PATH}?type=search&name=[${clan.tag}]#search`));
        a.addEventListener("click", CharacterUtil.autoClanSearch);
        return a;
    }

}

ClanUtil.REQUIRED_CURSOR_PARAMETERS = ["sortBy", "cursorValue", "idCursor", "page", "pageDiff"];