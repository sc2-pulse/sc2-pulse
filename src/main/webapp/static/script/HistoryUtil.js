// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class HistoryUtil
{

    static replaceState(obj, title, params)
    {
        Session.titleAndUrlHistory[Session.titleAndUrlHistory.length - 1] = [title, params];
        HistoryUtil.setObjectLocation(obj, params);
        HistoryUtil.updateState(obj, title, params, true);
    }

    static pushState(obj, title, params)
    {
        if(params == Session.titleAndUrlHistory[Session.titleAndUrlHistory.length - 1][1]) return;
        Session.titleAndUrlHistory.push([title, params]);
        if(Session.titleAndUrlHistory.length > 2) Session.titleAndUrlHistory.shift();
        HistoryUtil.setObjectLocation(obj, params);
        HistoryUtil.updateState(obj, title, params, false);
    }

    static setObjectLocation(obj, params)
    {
        const hashIx = params.indexOf("#");
        if(hashIx == -1)
        {
            obj.locationSearch = params;
        }
        else
        {
            obj.locationSearch = params.substring(0, hashIx);
            obj.locationHash = params.substring(hashIx)
        }
    }

    static previousTitleAndUrl()
    {
        return Session.titleAndUrlHistory.length > 1
            ? Session.titleAndUrlHistory[Session.titleAndUrlHistory.length - 2]
            : Session.titleAndUrlHistory[Session.titleAndUrlHistory.length - 1];
    }

    static updateState(obj, title, paramsStr, replace)
    {
        const hashIx = paramsStr.indexOf("#");
        const hash = hashIx > -1 ? paramsStr.substring(hashIx + 1) : null;
        HistoryUtil.setParentSectionParameters(hash, paramsStr);
        if(Session.isHistorical) return;
        if(replace)
        {
            history.replaceState(obj, title, paramsStr);
        }
        else
        {
            history.pushState(obj, title, paramsStr);
        }
        ElementUtil.executeActiveTabTask();
    }

    static setParentSectionParameters(deepestTabId, paramsStr)
    {
        const params = new URLSearchParams(paramsStr);
        const tabs = [];
        let parentTab;
        if(deepestTabId != null)
        {
            let prevTab = document.getElementById(deepestTabId);
            tabs.push(prevTab);
            while(true)
            {
                const curTab = prevTab.parentNode.closest(".tab-pane");
                if(curTab == null || curTab == prevTab) break;
                tabs.push(curTab);
                if(tabs.length == 2) break;
                prevTab = curTab;
            }
            parentTab = tabs.length == 1 ? tabs[0] : tabs[1];
        }
        let dataTarget;
        if(params.get("type") == "modal")
        {
            dataTarget = "#" + params.get("id");
        }
        else
        {
            dataTarget = tabs.length == 0
                ? "#" + document.querySelector(".modal.show").id
                : "#" + parentTab.id;
            const modal = document.querySelector(dataTarget).closest(".modal");
            dataTarget = modal == null ? dataTarget : "#" + modal.id;
        }
        Session.sectionParams.set(dataTarget, paramsStr.split("#")[0]);
        if(document.getElementById(deepestTabId).classList.contains("root"))
            Session.sectionParams.set("#" + deepestTabId, paramsStr.split("#")[0]);
    }

    static getDeepestTabId(el)
    {
        let hash = null;
        const tabs = el.querySelectorAll(":scope .nav-pills a.active");
        for(let i = tabs.length - 1; i > -1; i--)
        {
            const tab = tabs[i];
            if(ElementUtil.isElementVisible(tab))
            {
                hash = tab.getAttribute("data-target").substring(1);
                break;
            }
        }
        return hash;
    }

    static formatSearchString(search, hash)
    {
        return (search != null && search.length > 0 ? (search.startsWith("?") ? search : "?" + search) : "?")
            + (hash != null && hash.length > 0 ? (hash.startsWith("#") ? hash : "#" + hash) : "");
    }

    static initActiveTabs()
    {
        const locationSearch = Session.locationSearch();
        if(Session.locationHash() != null && Session.locationHash().length > 1)
            return; //tabs are explicit, do not touch them

        const params = new URLSearchParams(locationSearch);
        if(params.get("m") != 1)
        {
            Session.lastNonModalParams =  locationSearch;
            Session.lastNonModalTitle = document.title;
        }
        const deepestTabId = HistoryUtil.getDeepestTabId(document);
        if(!deepestTabId) return;
        HistoryUtil.replaceState({}, document.title,
            HistoryUtil.formatSearchString(params.toString(), deepestTabId));
    }

    static updateActiveTabs(alwaysReplace = true)
    {
        const modal = document.querySelector(".modal.show");
        const modalOnly = modal != null;
        const deepestDocumentTab = HistoryUtil.getDeepestTabId(document);
        if(!deepestDocumentTab) return false;
        const hash = modalOnly
            ? HistoryUtil.getDeepestTabId(modal)
            : deepestDocumentTab;
        const dataTarget = "#" + (hash != null ? hash : modal.id);
        if(alwaysReplace === false && dataTarget === window.location.hash) return false;

        ElementUtil.setMainContent(dataTarget);
        ElementUtil.updateTitleAndDescription(new URLSearchParams(Session.locationSearch()), "#" + hash, dataTarget);

        HistoryUtil.replaceState({}, document.title,
            HistoryUtil.formatSearchString(Session.locationSearch(),
                hash != null ? hash : deepestDocumentTab));
        return true;
    }

    static showAnchoredTabs(activateOnly = false, hash = Session.locationHash())
    {
        if(hash == null || hash.length == 0) return Promise.resolve();

        Util.setGeneratingStatus(STATUS.BEGIN);
        const promises = [];
        let prevTab = document.querySelector(hash);
        HistoryUtil.showAnchoredTab(prevTab, promises, activateOnly);
        while(true)
        {
            const curTab = prevTab.parentNode.closest(".tab-pane");
            if(curTab == null || curTab == prevTab) break;
            HistoryUtil.showAnchoredTab(curTab, promises, activateOnly);
            prevTab = curTab;
        }
        return Promise.all(promises).then(e=>Util.setGeneratingStatus(STATUS.SUCCESS));
    }

    static showAnchoredTab(tab, promises, activateOnly = false)
    {
        const tabEl = document.querySelector('.nav-pills a[data-target="#' + tab.id + '"]');
        if(tabEl.classList.contains("active")) return;

        if(activateOnly) {
            tabEl.classList.add("active")
        } else {
            if(ElementUtil.isElementVisible(tabEl)) promises.push(new Promise((res, rej)=>ElementUtil.ELEMENT_RESOLVERS.set(tab.id, res)));
            $(tabEl).tab('show');
        }
    }

    static restoreState(e)
    {
        let promise = Session.currentStateRestoration != null
            ? Session.currentStateRestoration.then(r=>HistoryUtil.doRestoreState(e))
            : HistoryUtil.doRestoreState(e);
        if(Session.statesRestored === 0) promise = promise.then(e=>HistoryUtil.updateActiveTabs(false));
        Session.currentStateRestoration = promise;
    }

    static doRestoreState(e)
    {
        if(e != null && e.state == null) return;
        Util.setGeneratingStatus(STATUS.BEGIN);
        Session.isHistorical = true;
        const locationSearch = (e != null && e.state.locationSearch != null) ? e.state.locationSearch : window.location.search;
        const hash = (e != null && e.state.locationHash != null) ? e.state.locationHash : window.location.hash;
        Session.currentRestorationSearch =  locationSearch;
        Session.currentRestorationHash = hash;
        const promises = [];
        const lazyPromises = [];
        lazyPromises.push(e=>HistoryUtil.showAnchoredTabs());
        const params = new URLSearchParams(locationSearch);
        const isModal = params.get("m"); params.delete("m");
        const stringParams = params.toString();
        if(Session.currentSearchParams === stringParams) return Promise.all(promises)
            .then(e => {const ap = []; for(const lp of lazyPromises) ap.push(lp()); return Promise.all(ap);})
            .then(e => new Promise((res, rej)=>{
                if(hash != null && hash.length > 0 && isModal != null)
                {
                    const lastModal = document.querySelector(hash).closest(".modal");
                    if(lastModal != null) BootstrapUtil.showModal(lastModal.id).then(e=>res());
                }
                else
                {
                    res();
                }
            }))
            .then(e => {
                HistoryUtil.updateActiveTabs();
                Session.statesRestored += 1;
                Util.setGeneratingStatus(STATUS.SUCCESS);
                ElementUtil.executeActiveTabTask();
            });

        const type = params.get("type"); params.delete("type");
        let scrollTo = null;
        let cursor = null;
        switch(type)
        {
            case "ladder":
                LadderUtil.restoreLadderFormState(document.getElementById("form-ladder"), params);
                cursor = Cursor.fromUrlSearchParams(params);
                for(const direction of Object.values(NAVIGATION_DIRECTION)) params.delete(direction.relativePosition);
                const sort = SortParameter.fromPrefixedString(params.get("sort"));
                params.delete("sort");
                const formParams = params.toString();
                scrollTo = "generated-info-all";
                lazyPromises.push(e=>BootstrapUtil.hideActiveModal("error-generation"));
                promises.push(LadderUtil.updateLadder(formParams, cursor, sort));
                promises.push(StatsUtil.updateQueueStats(formParams));
                promises.push(StatsUtil.updateLadderStats(formParams));
                promises.push(StatsUtil.updateLeagueBounds(formParams));
                break;
            case "character":
                const id = params.get("id"); params.delete("id");
                promises.push(CharacterUtil.showCharacterInfo(null, id));
                break;
            case "search":
                const name = params.get("name"); params.delete("name");
                scrollTo = "search-result-all";
                lazyPromises.push(e=>BootstrapUtil.hideActiveModal("error-generation"));
                promises.push(CharacterUtil.updateCharacterSearch(name));
                break;
            case "vod-search":
                lazyPromises.push(e=>BootstrapUtil.hideActiveModal("error-generation"));
                scrollTo = "search-result-vod-all";
                promises.push(VODUtil.update(params));
                break;
            case "team-mmr":
                 lazyPromises.push(e=>BootstrapUtil.hideActiveModal("error-generation"));
                 promises.push(TeamUtil.updateTeamMmr(params));
                 break;
            case "online":
                scrollTo = "online-data";
                lazyPromises.push(e=>BootstrapUtil.hideActiveModal("error-generation"));
                lazyPromises.push(e=>FormUtil.setFormState(document.querySelector("#form-online"), params));
                promises.push(SeasonUtil.updateSeasonState(params));
                break;
            case "clan-search":
                scrollTo = "search-result-clan-all";
                lazyPromises.push(e=>BootstrapUtil.hideActiveModal("error-generation"));
                lazyPromises.push(e=>FormUtil.setFormState(document.querySelector("#form-search-clan"), params));
                promises.push(HistoryUtil.callWithArguments(
                    (p, reqParams)=>ClanUtil.updateClanSearch(
                        p,
                        reqParams[0], reqParams[1], SortParameter.fromPrefixedString(reqParams[2])),
                    params,
                    ClanUtil.REQUIRED_CURSOR_PARAMETERS
                ));
                break;
            case "following-ladder":
                LadderUtil.restoreLadderFormState(document.getElementById("form-following-ladder"), params);
                scrollTo = "following-ladder";
                lazyPromises.push(e=>BootstrapUtil.hideActiveModal("error-generation"));
                promises.push(LadderUtil.updateMyLadder(params.toString()));
                break;
            case "versus":
                promises.push(VersusUtil.updateFromParams(params));
                break;
            case "group":
                promises.push(GroupUtil.loadAndShowGroup(params));
                break;
            case "modal":
                const modalId = params.get("id"); params.delete("id");
                promises.push(BootstrapUtil.hideActiveModal("error-generation"));
                lazyPromises.push(e=>BootstrapUtil.showModal(modalId));
                break;
            case "team-search":
                scrollTo = "team-search-container";
                promises.push(TeamUtil.updateTeams(params));
                break;
            case null:
                lazyPromises.push(e=>BootstrapUtil.hideActiveModal());
                break;
            default:
                break;
        }

        return Promise.all(promises)
        .then(e => {const ap = []; for(const lp of lazyPromises) ap.push(lp()); return Promise.all(ap)})
        .then(e => {
            HistoryUtil.updateActiveTabs();
            Session.statesRestored += 1;
            Util.setGeneratingStatus(STATUS.SUCCESS);
            Session.isHistorical = false;
            if(scrollTo != null) Util.scrollIntoViewById(scrollTo);
            ElementUtil.executeActiveTabTask();
        });
    }

    static callWithArguments(f, params, requiredParamNames)
    {
        const requiredParamVals = [];
        for(const paramName of requiredParamNames)
        {
            requiredParamVals.push(params.get(paramName));
            params.delete(paramName);
        }
        return f(params.toString(), requiredParamVals);
    }

}