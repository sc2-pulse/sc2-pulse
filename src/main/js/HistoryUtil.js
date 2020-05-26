// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class HistoryUtil
{

    static initActiveTabs()
    {
        const params = new URLSearchParams(window.location.search);
        const hashes = params.getAll("t");
        if(hashes.length > 0) return; //tabs are explicit, do not touch them

        for(const tab of document.querySelectorAll(".nav-pills a.active"))
                if(tab.offsetParent != null) params.append("t", tab.hash.substring(1));
        Session.lastNonModalParams =  "?" + params.toString();
        history.replaceState({}, document.title, "?" + params.toString());
    }

    static updateActiveTabs(modalOnly = false)
    {
        const params = new URLSearchParams(window.location.search);
        params.delete("t");
        const tabs = modalOnly
            ? document.querySelectorAll(".modal.show .nav-pills a.active")
            : document.querySelectorAll(".nav-pills a.active");
        for(const tab of tabs)
            if(tab.offsetParent != null) params.append("t", tab.hash.substring(1));
       history.replaceState({}, document.title, "?" + params.toString());
    }

    static showAnchoredTabs()
    {
        Util.setGeneratingStatus("begin");
        const params = new URLSearchParams(window.location.search);
        const hashes = params.getAll("t");
        const promises = [];
        for(const hash of hashes)
        {
            const element = document.querySelector('.nav-pills a[href="#' + hash + '"]');
            if(!element.classList.contains("active"))
            {
                for(const chart of document.querySelectorAll("#" + hash + " > * > * > * > .c-chart"))
                {
                    if(chart.style.width.startsWith("0"))
                        promises.push(new Promise((res, rej)=>ElementUtil.ELEMENT_RESOLVERS.set(chart.id, res)));
                }
                if(element.offsetParent != null) promises.push(new Promise((res, rej)=>ElementUtil.ELEMENT_RESOLVERS.set(hash, res)));
                $(element).tab('show')
            }
        }
        return Promise.all(promises).then(e=>new Promise((res, rej)=>{Util.setGeneratingStatus("success"); res();}));
    }

    static restoreState(e)
    {
        if(e != null && e.state == null) return;
        Util.setGeneratingStatus("begin");
        Session.isHistorical = true;
        promises = [];
        lazyPromises = [];
        lazyPromises.push(e=>HistoryUtil.showAnchoredTabs());
        const params = new URLSearchParams(window.location.search);
        const tabs = params.getAll("t"); params.delete("t");
        const isModal = params.get("m"); params.delete("m");
        const stringParams = params.toString();
        if(Session.currentSearchParams === stringParams) return Promise.all(promises)
            .then(e => {const ap = []; for(lp of lazyPromises) ap.push(lp()); return Promise.all(ap);})
            .then(e => new Promise((res, rej)=>{HistoryUtil.updateActiveTabs(); Util.setGeneratingStatus("success"); res();}));

        const type = params.get("type"); params.delete("type");
        let scrollTo = null;
        switch(type)
        {
            case "ladder":
                const ratingAnchor = params.get("ratingAnchor"); params.delete("ratingAnchor");
                const idAnchor = params.get("idAnchor"); params.delete("idAnchor");
                const forward = params.get("forward"); params.delete("forward");
                const count = params.get("count"); params.delete("count");
                const formParams = params.toString();
                scrollTo = "generated-info-all";
                lazyPromises.push(e=>BootstrapUtil.hideCollapsible("form-ladder"));
                lazyPromises.push(e=>BootstrapUtil.hideActiveModal());
                promises.push(LadderUtil.getLadder(formParams, ratingAnchor, idAnchor, forward, count));
                promises.push(StatsUtil.getLadderStats(formParams));
                promises.push(StatsUtil.getLeagueBounds(formParams));
                break;
            case "character":
                const id = params.get("id"); params.delete("id");
                promises.push(CharacterUtil.showCharacterInfo(null, id));
                break;
            case "following-ladder":
                scrollTo = "following-ladder";
                lazyPromises.push(e=>BootstrapUtil.hideCollapsible("form-following-ladder"));
                lazyPromises.push(e=>BootstrapUtil.hideActiveModal());
                promises.push(LadderUtil.getMyLadder(params.toString()));
                break;
            default:
                break;
        }

        return Promise.all(promises)
        .then(e => {const ap = []; for(lp of lazyPromises) ap.push(lp()); return Promise.all(ap)})
        .then(e => new Promise((res, rej)=>{
            HistoryUtil.updateActiveTabs();
            Util.setGeneratingStatus("success");
            if(scrollTo != null) Util.scrollIntoViewById(scrollTo);
            res();
        }));
    }

}