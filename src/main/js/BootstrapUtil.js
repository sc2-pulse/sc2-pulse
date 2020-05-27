// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class BootstrapUtil
{

    static enhanceTabs()
    {
        $('.nav-pills a').on('shown.bs.tab', BootstrapUtil.showTab);
    }

    static showTab(e)
    {
        ElementUtil.resolveElementPromise(e.target.getAttribute("href").substring(1));
        if(Session.isHistorical) return;
        const hash = e.target.hash.substring(1);
        const params = new URLSearchParams(window.location.search);
        params.delete("t");
        const modal = e.target.closest(".modal");
        const root = modal != null ? ("#" + modal.id) : "body";
        for(const tab of document.querySelectorAll(root + " .nav-pills a.active"))
            if(tab.offsetParent != null) params.append("t", tab.hash.substring(1));
        history.pushState({}, document.title, "?" + params.toString());
    }

    static hideCollapsible(id)
    {
        return new Promise((res, rej)=>{
            const elem = document.getElementById(id);
            if(elem.offsetParent == null)
            {
                $(elem).collapse("hide");
                res();
            }
            else
            {
                ElementUtil.ELEMENT_RESOLVERS.set(ElementUtil.NEGATION_PREFIX + id, res);
                $(elem).collapse("hide");
            }
        });
    }

    static hideActiveModal(skipIds = [])
    {
        return new Promise((res, rej)=>{
            const activeModal = document.querySelector(".modal.show");
            if(activeModal != null)
            {
                if(skipIds.includes(activeModal.id))
                {
                    res();
                }
                else
                {
                    ElementUtil.ELEMENT_RESOLVERS.set(activeModal.id, res);
                    $(activeModal).modal("hide");
                }
            }
            else
            {
                res();
            }
        });
    }

    static enhanceModals()
    {
        $(".modal")
            .on("hidden.bs.modal", e=>{
                ElementUtil.resolveElementPromise(e.target.id);
                if(!Session.isHistorical) history.pushState({}, document.title, Session.lastNonModalParams);
            })
            .on("show.bs.modal", e=>{
                if(!window.location.search.includes("m=1")) Session.lastNonModalParams = window.location.search;
            })
            .on("shown.bs.modal", e=>HistoryUtil.updateActiveTabs(true));
        $("#error-session").on("hide.bs.modal", Session.doRenewBlizzardRegistration);
        $("#error-session").on("shown.bs.modal", e=>window.setTimeout(Session.doRenewBlizzardRegistration, 3500));
    }

    static enhanceCollapsibles()
    {
        $(".collapse").on("hidden.bs.collapse", e=>ElementUtil.resolveElementPromise(ElementUtil.NEGATION_PREFIX + e.target.id));
    }

    static setFormCollapsibleScroll(id)
    {
        const jCol = $(document.getElementById(id));
        jCol.on("show.bs.collapse", function(e){
            const scrollTo = e.currentTarget.getAttribute("data-scroll-to");
            if(scrollTo != null) Util.scrollIntoViewById(scrollTo);
        });
    }

    static enhanceTabSelect(select, nav)
    {
        select.addEventListener("change", e=>$(document.getElementById(e.target.options[e.target.selectedIndex].getAttribute("data-tab"))).tab("show"));
        return select;
    }

}
