// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class BootstrapUtil
{

    static init()
    {
        $.fn.popover.Constructor.Default.whiteList.table = [];
        $.fn.popover.Constructor.Default.whiteList.tr = [];
        $.fn.popover.Constructor.Default.whiteList.th = [];
        $.fn.popover.Constructor.Default.whiteList.td = [];
        $.fn.popover.Constructor.Default.whiteList.div = [];
        $.fn.popover.Constructor.Default.whiteList.tbody = [];
        $.fn.popover.Constructor.Default.whiteList.thead = [];
    }

    static enhanceTabs()
    {
        $('.nav-pills a')
            .on('show.bs.tab', BootstrapUtil.onTabShow)
            .on('shown.bs.tab', BootstrapUtil.onTabShown);
        for(const a of document.querySelectorAll('#stats .nav-pills a'))
        {
            ElementUtil.TITLE_CONSTRUCTORS.set(a.getAttribute("data-target"), ElementUtil.generateLadderTitle);
            ElementUtil.DESCRIPTION_CONSTRUCTORS.set(a.getAttribute("data-target"), ElementUtil.generateLadderDescription);
        }

        for(const a of document.querySelectorAll('#player-info .nav-pills a'))
        {
            ElementUtil.TITLE_CONSTRUCTORS.set(a.getAttribute("data-target"), ElementUtil.generateCharacterTitle);
            ElementUtil.DESCRIPTION_CONSTRUCTORS.set(a.getAttribute("data-target"), ElementUtil.generateCharacterDescription);
        }
    }

    static renderTabContent(tab)
    {
        ChartUtil.updateChartableTab(tab);
    }

    static onTabShow(e)
    {
        BootstrapUtil.renderTabContent(e.target);
    }

    static onTabShown(e)
    {
        const dataTarget = e.target.getAttribute("data-target");
        ElementUtil.resolveElementPromise(dataTarget.substring(1));
        if(Session.isHistorical) return;

        const params = new URLSearchParams();
        const modal = e.target.closest(".modal");
        const root = modal != null ? ("#" + modal.id) : "body";
        for(const tab of document.querySelectorAll(root + " .nav-pills a.active"))
            if(tab.offsetParent != null) params.append("t", tab.getAttribute("data-target").substring(1));
        const newTabs = params.getAll("t");
        const parentDataTarget = modal != null
            ? "#" + modal.id
            : (newTabs.length == 1 ? "#" + newTabs[0] : "#" + newTabs[newTabs.length - 2]);
        const lastDataTarget = "#" + newTabs[newTabs.length - 1];
        ElementUtil.setMainContent(lastDataTarget);

        const parentParamsStr = Session.sectionParams.get(parentDataTarget);
        const fullParams = new URLSearchParams(parentParamsStr == null ? "" : parentParamsStr);

        const hash = HistoryUtil.getDeepestTabId(document.querySelector(modal != null ? "#" + modal.id : "body"));
        ElementUtil.updateTitleAndDescription(fullParams, "#" + hash, lastDataTarget);

        HistoryUtil.pushState({}, document.title, HistoryUtil.formatSearchString(fullParams.toString(), hash));
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

    static showGenericModal(header, body = "", spinner = false)
    {
        const modal = document.querySelector("#modal-generic");
        modal.querySelector(":scope .modal-title .title-text").textContent = header;
        if(spinner)
        {
            modal.querySelector(":scope .modal-title .spinner-border").classList.remove("d-none");
        }
        else
        {
            modal.querySelector(":scope .modal-title .spinner-border").classList.add("d-none");
        }
        modal.querySelector(":scope .modal-body").textContent = body;
        modal.setAttribute("data-view-title", header);
        return BootstrapUtil.showModal("modal-generic");
    }

    static showModal(id)
    {
        return new Promise((res, rej)=>{
            const elem = document.getElementById(id);
            if(!elem.classList.contains("show"))
            {
                ElementUtil.ELEMENT_RESOLVERS.set(id, res);
                $(elem).modal();
            }
            else
            {
                BootstrapUtil.onModalShow(elem);
                BootstrapUtil.onModalShown(elem);
                res();
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
                if(!Session.isHistorical && !e.target.classList.contains("c-no-history"))
                {
                    HistoryUtil.pushState({}, Session.lastNonModalTitle, Session.lastNonModalParams);
                    document.title = Session.lastNonModalTitle;
                }
            })
            .on("show.bs.modal", e=>{BootstrapUtil.onModalShow(e.currentTarget)})
            .on("shown.bs.modal", e=>{BootstrapUtil.onModalShown(e.currentTarget)});
        $("#error-session").on("hide.bs.modal", Session.doRenewBlizzardRegistration);
        $("#error-session").on("shown.bs.modal", e=>window.setTimeout(Session.doRenewBlizzardRegistration, 3500));
    }

    static onModalShow(modal)
    {
    }

    static onModalShown(modal)
    {
        if(!modal.classList.contains("c-no-history"))
        {
            if(!Session.isHistorical && modal.getAttribute("data-modal-singleton") != null)
                HistoryUtil.pushState({}, modal.getAttribute("data-view-title"), "?type=modal&id=" + modal.id + "&m=1");
            if(!Session.isHistorical) HistoryUtil.updateActiveTabs();
            const prev = HistoryUtil.previousTitleAndUrl();
            if(!prev[1].includes("m=1"))
            {
                Session.lastNonModalTitle = prev[0];
                Session.lastNonModalParams = prev[1];
            }
        }
        ElementUtil.resolveElementPromise(modal.id);
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

    static enhanceTooltips()
    {
        $("body").tooltip
        ({
            html: false,
            boundary: "body",
            placement: "auto",
            trigger: "hover",
            selector: '[data-toggle="tooltip"]',
            content: function(){return $(this).attr("title");}
        });
    }

    static addTooltip(elem, text)
    {
        elem.setAttribute("title", text);
        elem.setAttribute("data-toggle", "tooltip");
    }

    static enhancePopovers(selector = "body")
    {
        $(selector + ' [data-toggle="popover"]').popover();
    }

}
