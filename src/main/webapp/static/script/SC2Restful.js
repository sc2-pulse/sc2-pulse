// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class SC2Restful
{

    static baseStart()
    {
        Session.initThemes();
    }

    static start()
    {
        window.addEventListener("popstate", e=>{HistoryUtil.restoreState(e)});
        return new Promise
        (
            (res, rej)=>
            {
                SC2Restful.initAll();
                Session.restoreState();
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
            .then(o=>{if(o[0] != "reauth") Session.currentStateRestoration = HistoryUtil.restoreState(null);});
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
        CharacterUtil.enhanceMmrForm();
        LadderUtil.enhanceMyLadderForm();
        SeasonUtil.enhanceSeasonStateForm();
        FollowUtil.enhanceFollowButtons();
        BootstrapUtil.enhanceTabs();
        BootstrapUtil.setFormCollapsibleScroll("form-ladder");
        BootstrapUtil.setFormCollapsibleScroll("form-following-ladder");
        BootstrapUtil.enhanceTooltips();
        ElementUtil.enhanceFullscreenToggles();
        Session.enhanceSerializable();
        ChartUtil.enhanceZoomToggles();
        Session.enhanceThemeInputs();
        Session.refreshTheme();
    }

    static getPredefinedOrRandomColor(name, ix)
    {
        return SC2Restful.COLORS.get(name)
            || ( ix < SC2Restful.UNIQUE_COLORS.length ? SC2Restful.UNIQUE_COLORS[ix] : Util.getRandomRgbColorString());
    }

}

SC2Restful.COLORS = new Map
([
    ["global", "#007bff"],
    ["all", "#007bff"],
    ["old", "#dc3545"],
    ["new", "#28a745"],
    ["low", "#28a745"],
    ["medium", "#ffc107"],
    ["high", "#dc3545"],
    ["terran", "#295a91"],
    ["protoss", "#dec93e"],
    ["zerg", "#882991"],
    ["random", "#646464"],
    ["us", "#17a2b8"],
    ["eu", "#ffc107"],
    ["kr", "#6c757d"],
    ["cn", "#dc3545"],
    ["bronze", "#b9712d"],
    ["silver", "#737373"],
    ["gold", "#ffd700"],
    ["platinum", "#a5a4a3"],
    ["diamond", "#0d4594"],
    ["master", "#00b1fb"],
    ["grandmaster", "#ef3e00"]
]);

SC2Restful.UNIQUE_COLORS = [...new Set(SC2Restful.COLORS.values())];

SC2Restful.SITE_NAME = "SC2 Pulse";
SC2Restful.UNDEFINED_RANK = 2147483647;
SC2Restful.MMR_HISTORY_START_DATE = new Date("2021-01-19T00:00:00");
SC2Restful.MMR_HISTORY_DAYS_MAX = 90;