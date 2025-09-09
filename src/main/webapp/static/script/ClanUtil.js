// Copyright (C) 2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

class ClanUtil
{

    static updateClanSearchModel(formParams, navigationCursor = null, sort = ClanUtil.DEFAULT_SORT)
    {
        const cursor = EnumUtil.enumOfProperty("field", sort.field, CLAN_CURSOR);
        const searchParams = new URLSearchParams("?" + formParams);
        if(navigationCursor != null) searchParams.append(navigationCursor.direction.relativePosition, navigationCursor.token);
        searchParams.append("sort", sort.toPrefixedString());

        const previousData = Model.DATA.get(VIEW.CLAN_SEARCH).get(VIEW_DATA.SEARCH);
        const params =
        {
            formParams: formParams,
            cursor: cursor,
            navigationCursor: navigationCursor,
            sort: sort
        }
        if(!previousData || navigationCursor == null)
            ClanUtil.updateClanSearchPaginationConfig
            (
                Number.parseFloat(searchParams.get(params.cursor.minParamName)) || CLAN_MIN_ADDITIONAL_CURSOR_FILTER,
                Number.parseFloat(searchParams.get(params.cursor.maxParamName)) || CLAN_MAX_ADDITIONAL_CURSOR_FILTER,
                params.cursor.getter
            );

        const tagOrName = searchParams.get("tagOrName").trim();
        const byTagOrName = tagOrName && tagOrName.length > 0;
        const request = byTagOrName
            ? `${ROOT_CONTEXT_PATH}api/clans?query=${encodeURIComponent(tagOrName)}`
            : `${ROOT_CONTEXT_PATH}api/clans?${searchParams.toString()}`;
        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(Session.verifyJsonResponse)
            .then(json=>ClanUtil.extractModelData(json, params, byTagOrName));
    }

    static extractModelData(json, params, byTagOrName)
    {
        if(byTagOrName) {
            Model.DATA.get(VIEW.CLAN_SEARCH).set(VIEW_DATA.SEARCH, {searchResult: PaginationUtil.resultToPagedResult(json), params: params});
        } else {
            const direction = params.navigationCursor?.direction || NAVIGATION_DIRECTION.FORWARD;
            const empty = json.result.length == 0;
            json.meta = PaginationUtil.createCursorMeta(
                json,
                params.navigationCursor == null || json.navigation?.[NAVIGATION_DIRECTION.BACKWARD.relativePosition] == null,
                direction
            );
            if(empty) {
                const searchData = Model.DATA.get(VIEW.CLAN_SEARCH).get(VIEW_DATA.SEARCH);
                if(searchData?.searchResult?.meta != null) {
                    PaginationUtil.setEmptyResultMeta(searchData.searchResult.meta, direction);
                    return searchData;
                }
            }
            Model.DATA.get(VIEW.CLAN_SEARCH).set(VIEW_DATA.SEARCH, {searchResult: json, params: params});
        }
        return Model.DATA.get(VIEW.CLAN_SEARCH).get(VIEW_DATA.SEARCH);
    }

    static updateClanSearchPaginationConfig(min, max, getter)
    {
        PaginationUtil.PAGINATIONS.set("clan-search",
            new Pagination
            (
                ".pagination-clan-search",
                [],
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
            tr.setAttribute("data-clan-id", clan.id);
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
            tr.insertCell().textContent = Util.DECIMAL_FORMAT.format(clan.games / clan.activeMembers / CLAN_STATS_DEPTH_DAYS);
            const nameCell = tr.insertCell();
            nameCell.classList.add("cell-main", "complex");
            nameCell.textContent = clan.name;
            const miscCell = tr.insertCell();
            miscCell.classList.add("text-nowrap", "misc", "text-right");
            miscCell.appendChild(BufferUtil.createToggleElement(BufferUtil.clanBuffer, clan));
        }
    }

    static updateClanSearch
    (
        formParams,
        navigationCursor = null,
        sort = ClanUtil.DEFAULT_SORT
    )
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return ClanUtil.updateClanSearchModel(formParams, navigationCursor, sort)
            .then(e => {
                const executedParams = Model.DATA.get(VIEW.CLAN_SEARCH).get(VIEW_DATA.SEARCH).params;
                const searchParams = new URLSearchParams(executedParams.formParams);
                searchParams.append("type", "clan-search");
                if(executedParams.navigationCursor != null)
                    searchParams.append(executedParams.navigationCursor.direction.relativePosition, executedParams.navigationCursor.token);
                searchParams.append("sort", executedParams.sort.toPrefixedString());
                const stringParams = searchParams.toString();

                ClanUtil.updateClanSearchView();
                Util.scrollIntoViewById("search-result-clan-all");
                Util.setGeneratingStatus(STATUS.SUCCESS);
                if(!Session.isHistorical) HistoryUtil.pushState({}, document.title, "?" + stringParams + "#search-clan");
                Session.currentSearchParams = stringParams;
            })
            .catch(error => Session.onPersonalException(error));
    }

    static clanSearchPaginationPageClick(evt)
    {
        evt.preventDefault();
        const searchData = Model.DATA.get(VIEW.CLAN_SEARCH).get(VIEW_DATA.SEARCH);

        ClanUtil.updateClanSearch
        (
            searchData.params.formParams,
            Cursor.fromElementAttributes(evt.target, "data-page-"),
            searchData.params.sort
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
            const field = formData.get("sortBy") || CLAN_CURSOR.ACTIVE_MEMBERS.field;
            formData.delete("sortBy");
            for(const p of ClanUtil.REQUIRED_CURSOR_PARAMETERS) formData.delete(p);
            ClanUtil.updateClanSearch(
                Util.urlencodeFormData(formData),
                null,
                new SortParameter(field, ClanUtil.DEFAULT_SORT.order)
            );
        });
    }

    static createClanTagElem(clan)
    {
        const a = ElementUtil.createElement("a", null, "clan-auto-search", clan.tag);
        a.setAttribute("href", encodeURI(`${ROOT_CONTEXT_PATH}?type=group&clanId=${clan.id}#group-group`));
        a.addEventListener("click", GroupUtil.onGroupLinkClick);
        return a;
    }

    static showClanGroup(evt)
    {
        evt.preventDefault();
        return GroupUtil.loadAndShowGroup(Util.getHrefUrlSearchParams(evt.target));
    }

    static getClanFromElement(parent)
    {
        const id = parent.closest("tr").getAttribute("data-clan-id");
        const searchData = Model.DATA.get(ViewUtil.getView(parent)).get(VIEW_DATA.SEARCH);
        return (searchData.clans || searchData.searchResult.result).find(t=>t.id==id);
    }

    static generateClanName(clan, includeName = false)
    {
        return `[${clan.tag}]` + (includeName && clan.name ? ` ${clan.name}` : "");
    }

    static updateClanHistoryTable(table, clanHistory, reset = false)
    {
        const tBody = table.querySelector(":scope tbody")
        if(reset) ElementUtil.removeChildren(tBody);
        for(const event of clanHistory.events)
        {
            const tr = tBody.insertRow();
            const character = clanHistory.characters.get(event.playerCharacterId);
            const clan = clanHistory.clans.get(event.clanId);
            const type = EnumUtil.enumOfName(event.type, CLAN_MEMBER_EVENT_TYPE);
            tr.insertCell().textContent = Util.DATE_TIME_FORMAT.format(Util.parseIsoDateTime(event.created));
            TableUtil.insertCell(tr, "text-right").appendChild(TeamUtil.createMemberInfo(character, character.members));
            tr.insertCell().appendChild(type.element.cloneNode());
            tr.insertCell().appendChild(ElementUtil.createImage("flag/", clan.region.toLowerCase(), "table-image-long"));
            TableUtil.insertCell(tr, "cell-main text-left").appendChild(ClanUtil.createClanTagElem(clan));
        }
    }

}

ClanUtil.REQUIRED_CURSOR_PARAMETERS = ["sort"];
ClanUtil.DEFAULT_SORT = new SortParameter(CLAN_CURSOR.ACTIVE_MEMBERS.field, SORTING_ORDER.DESC);