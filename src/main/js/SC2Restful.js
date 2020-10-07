// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class SC2Restful
{

    static start()
    {
        window.addEventListener("popstate", e=>{Session.currentStateRestoration = Session.currentStateRestoration.then(r=>HistoryUtil.restoreState(e))});
        return new Promise
        (
            (res, rej)=>
            {
                SC2Restful.initAll();
                SC2Restful.enhanceAll();
                ChartUtil.observeChartables();
                PaginationUtil.createPaginations();
                ElementUtil.createPlayerStatsCards(document.getElementById("player-stats-container"));
                HistoryUtil.initActiveTabs();
                ChartUtil.observeCharts();
                res();
            }
        )
            .then(e=>Promise.all([Session.getMyInfo(), SeasonUtil.getSeasons()]))
            .then(o=>{Session.currentStateRestoration = HistoryUtil.restoreState(null);});
    }

    static initAll()
    {
        Model.init();
    }

    static enhanceAll()
    {
        BootstrapUtil.init();
        BootstrapUtil.enhanceModals();
        BootstrapUtil.enhanceCollapsibles();
        LadderUtil.enhanceLadderForm();
        CharacterUtil.enhanceSearchForm();
        LadderUtil.enhanceMyLadderForm();
        FollowUtil.enhanceFollowButtons();
        BootstrapUtil.enhanceTabs();
        BootstrapUtil.setFormCollapsibleScroll("form-ladder");
        BootstrapUtil.setFormCollapsibleScroll("form-following-ladder");
        BootstrapUtil.enhanceTooltips();
    }

}

SC2Restful.COLORS = new Map
([
    ["global", "#007bff"],
    ["old", "#dc3545"],
    ["new", "#28a745"],
    ["low", "#28a745"],
    ["medium", "#ffc107"],
    ["high", "#dc3545"],
    ["terran", "#295a91"],
    ["protoss", "#dec93e"],
    ["zerg", "#882991"],
    ["random", "#646464"],
    //["us", "#3c3b6e"],
    //["eu", "#003399"],
    ["us", "#003399"],
    ["eu", "#fc0"],
    ["kr", "#141414"],
    ["cn", "#de2910"],
    ["bronze", "#b9712d"],
    ["silver", "#737373"],
    ["gold", "#ffd700"],
    ["platinum", "#a5a4a3"],
    ["diamond", "#0d4594"],
    ["master", "#00b1fb"],
    ["grandmaster", "#ef3e00"]
]);

SC2Restful.SITE_NAME = "SC2 Pulse";
SC2Restful.UNDEFINED_RANK = 2147483647;