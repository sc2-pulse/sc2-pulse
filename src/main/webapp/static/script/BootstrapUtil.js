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
        document.querySelectorAll('.stop-propagation *')
            .forEach(e=>e.addEventListener("click", e=>e.stopPropagation()));
        document.querySelectorAll('.dropdown-menu .close').forEach(e=>e.addEventListener("click", e=>
                $(e.currentTarget.closest(".btn-group").querySelector(":scope .dropdown-toggle")).dropdown('toggle')));
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

        ElementUtil.TITLE_CONSTRUCTORS.set("#online", ElementUtil.generateOnlineTitle);
        ElementUtil.TITLE_CONSTRUCTORS.set("#team-mmr-history", TeamUtil.generateTeamMmrTitle);
        ElementUtil.DESCRIPTION_CONSTRUCTORS.set("#team-mmr-history", TeamUtil.generateTeamMmrDescription);
        ElementUtil.TITLE_CONSTRUCTORS.set("#team-mmr-teams", TeamUtil.generateTeamMmrTitle);
        ElementUtil.TITLE_CONSTRUCTORS.set("#versus", VersusUtil.generateVersusTitle);
    }

    static renderTabContent(tab)
    {
        ChartUtil.updateChartableTab(tab);
    }

    static showTab(id)
    {
        const tab = document.getElementById(id);
        if(tab.classList.contains("active")) return Promise.resolve();
        if(tab.offsetParent == null)
        {
            $(tab).tab('show');
            return Promise.resolve();
        }

        const promise = new Promise((res, rej)=>ElementUtil.ELEMENT_RESOLVERS.set(tab.getAttribute("data-target").substring(1), res));
        $(tab).tab('show');
        return promise;
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

        if(!e.target.closest(".modal"))
        {
            Session.isHistorical = true;
            Session.lastNonModalScroll = 0;
            BootstrapUtil.hideActiveModal();
            Session.isHistorical = false;
        }

        const params = new URLSearchParams();
        const modal = e.target.closest(".modal");
        const root = modal != null ? ("#" + modal.id) : "body";
        for(const tab of document.querySelectorAll(root + " .nav-pills a.active"))
            if(tab.getAttribute("data-ignore-visibility") || tab.offsetParent != null)
                params.append("t", tab.getAttribute("data-target").substring(1));
        const newTabs = params.getAll("t");
        //this can happen only when hiding tabs
        if(newTabs.length == 0) return;

        const parentDataTarget = modal != null
            ? "#" + modal.id
            : document.querySelector(dataTarget).classList.contains("root")
                ? dataTarget
                : (newTabs.length == 1 ? "#" + newTabs[0] : "#" + newTabs[newTabs.length - 2]);
        const lastDataTarget = "#" + newTabs[newTabs.length - 1];
        ElementUtil.setMainContent(lastDataTarget);
        document.querySelectorAll(dataTarget + " .c-autofocus").forEach(e=>FormUtil.selectAndFocusOnInput(e, true));

        const parentParamsStr = Session.sectionParams.get(parentDataTarget);
        const fullParams = new URLSearchParams(parentParamsStr == null ? "" : parentParamsStr);

        const hash = HistoryUtil.getDeepestTabId(document.querySelector(modal != null ? "#" + modal.id : "body"));
        ElementUtil.updateTitleAndDescription(fullParams, "#" + hash, lastDataTarget);

        HistoryUtil.pushState({}, document.title, HistoryUtil.formatSearchString(fullParams.toString(), hash));
    }

    //This fixes a Bootstrap bug where invalid tab may be active even though it isn't selected
    static deactivateInvalidTabs(ul)
    {
        ul.querySelectorAll(':scope .nav-link.active[aria-selected="false"]').forEach(e=>e.classList.remove("active"));
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
        const elem = document.getElementById(id);
        const promises = [];
        const lazyPromises = [];
        if(elem.classList.contains("no-popup"))
        {
            const activeModal = document.querySelector(".modal.no-popup.show");
            if(activeModal && activeModal.id != id) {
                Session.nonPopupSwitch = true;
                promises.push(BootstrapUtil.hideActiveModal());
            }
            lazyPromises.push(e=>new Promise((res, rej)=>{
                Session.lastNonModalScroll = window.pageYOffset;
                document.body.classList.add("modal-open-no-popup");
                document.getElementById(id).classList.remove("d-none");
                document.querySelectorAll(".no-popup-hide").forEach(e=>e.classList.add("d-none"));
                elem.scrollIntoView();
                res();
            }));
        }
        const ap = [];
        return Promise.all(promises)
            .then(e => {const ap = []; for(const lp of lazyPromises) ap.push(lp()); return Promise.all(ap)})
            .then(e => new Promise((res, rej)=>{
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
            }));
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

    static enhanceEmbedBackdropCloseControls()
    {
        const ctl = document.querySelector("#embed-backdrop-close");
        if(!ctl) return;
        ctl.addEventListener("click", e=>window.setTimeout(BootstrapUtil.updateEmbedBackdropClose, 1));
    }

    static updateEmbedBackdropClose()
    {
        const active = localStorage.getItem("embed-backdrop-close") == "true";
        document.querySelectorAll(".section-side").forEach(s=>{
            if(active) {
                s.addEventListener("click", BootstrapUtil.onModalBackdropClose)
                s.classList.add("backdrop");
            } else {
                s.removeEventListener("click", BootstrapUtil.onModalBackdropClose);
                s.classList.remove("backdrop");
            }
        });
    }

    static onModalBackdropClose(evt)
    {
        BootstrapUtil.hideActiveModal();
    }

    static enhanceModals()
    {
        document.querySelectorAll(".modal.no-popup").forEach(m=>{
            m.classList.add("d-none", "mb-3");
            m.classList.remove("fade");
            m.setAttribute("data-backdrop", "false");
        });
        BootstrapUtil.updateEmbedBackdropClose();
        $(".modal")
            .on("hidden.bs.modal", e=>{
                ElementUtil.resolveElementPromise(e.target.id);
                if(!Session.isHistorical && !e.target.classList.contains("c-no-history"))
                {
                    if(Session.nonPopupSwitch) {
                        const curParams = Session.titleAndUrlHistory[Session.titleAndUrlHistory.length - 1];
                        HistoryUtil.pushState({}, curParams[0], curParams[1]);
                        document.title = curParams[0];
                    } else {
                        HistoryUtil.pushState({}, Session.lastNonModalTitle, Session.lastNonModalParams);
                        document.title = Session.lastNonModalTitle;
                    }
                    if(e.target.classList.contains("no-popup")) HistoryUtil.showAnchoredTabs(true);
                    if(!Util.isMobile()) document.querySelectorAll(".tab-pane.active.show .c-autofocus").forEach(e=>FormUtil.selectAndFocusOnInput(e, true));
                }
                Session.nonPopupSwitch = false;
            })
            .on("hide.bs.modal", e=>{
                if(e.target.classList.contains("no-popup")) {
                    document.querySelectorAll(".no-popup-hide").forEach(e=>e.classList.remove("d-none"));
                    document.body.classList.remove("modal-open-no-popup");
                    document.querySelectorAll(".backdrop").forEach(b=>b.classList.remove("backdrop-active"));
                    e.target.classList.add("d-none");
                    window.scrollBy(0, Session.lastNonModalScroll);
                }
            })
            .on("show.bs.modal", e=>{BootstrapUtil.onModalShow(e.currentTarget)})
            .on("shown.bs.modal", e=>{BootstrapUtil.onModalShown(e.currentTarget)});
        $("#error-session").on("shown.bs.modal", e=>window.setTimeout(Session.doRenewBlizzardRegistration, 3500));
        $("#application-version-update").on("shown.bs.modal", e=>Util.reload("application-version-update"));
        document.querySelectorAll(".modal .modal-header .close-left").forEach(e=>e.addEventListener("click", e=>history.back()));
        BootstrapUtil.enhanceConfirmationModal();
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
            if(modal.classList.contains("no-popup"))
            {
                document.querySelectorAll("#main-tabs .nav-link.active").forEach(l=>l.classList.remove("active"));
                if(localStorage.getItem("embed-backdrop-close") == "true")
                    document.querySelectorAll(".backdrop").forEach(b=>b.classList.add("backdrop-active"));
            }
            const prev = HistoryUtil.previousTitleAndUrl();
            if(!prev[1].includes("m=1"))
            {
                Session.lastNonModalTitle = prev[0];
                Session.lastNonModalParams = prev[1];
            }
        }
        modal.querySelectorAll(":scope nav").forEach(BootstrapUtil.deactivateInvalidTabs);
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

    static enhanceConfirmationModal()
    {
        const confirmationModal = document.querySelector("#modal-confirmation");
        const modalObj = $(confirmationModal);
        modalObj.on("hide.bs.modal", e=>{BootstrapUtil.resetConfirmationModal(confirmationModal)});
        modalObj.on("shown.bs.modal", e=>{
            document.querySelector("#modal-confirmation-input").focus();
        });
        confirmationModal.querySelector("#modal-confirmation-form").addEventListener("submit", e=>{
            e.preventDefault();
            if(Session.confirmAction) Session.confirmAction(true);
            $(document.querySelector("#modal-confirmation")).modal("hide");
        });
        confirmationModal.querySelector("#modal-confirmation-input").addEventListener("input", e=>{
            const btn = document.querySelector("#modal-confirmation .btn-action");
            if(e.target.value == Session.confirmActionText) {
                btn.removeAttribute("disabled");
            } else {
                btn.setAttribute("disabled", "disabled");
            }
        });
    }

    static resetConfirmationModal(confirmationModal)
    {
        if(Session.confirmAction) Session.confirmAction(false);
        Session.confirmAction = null;
        Session.confirmActionText = null;
        document.querySelector("#modal-confirmation #modal-confirmation-input").value = null;
        document.querySelector("#modal-confirmation .btn-action").setAttribute("disabled", "disabled");
    }

    static showConfirmationModal(requiredText, description, actionName, actionButtonClass)
    {
        const modal = document.querySelector("#modal-confirmation");
        modal.querySelector(":scope .description").textContent = description;
        const actionButton = modal.querySelector(":scope .btn-action");
        actionButton.textContent = actionName;
        actionButton.setAttribute("class", "btn btn-action " + actionButtonClass);
        modal.querySelector(":scope .requirement").textContent = requiredText;
        Session.confirmActionText = requiredText;
        const promise = new Promise((res, rej)=>{
            Session.confirmAction = res;
        });
        $(modal).modal();
        return promise;
    }

}
