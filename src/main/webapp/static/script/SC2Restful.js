// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class SC2Restful
{

    static baseStart()
    {
        Session.initThemes();
    }

    static start(mode = START_MODE.FULL)
    {
        window.addEventListener("popstate", e=>{HistoryUtil.restoreState(e)});
        return new Promise
        (
            (res, rej)=>
            {
                SC2Restful.initAll();
                Session.restoreState();
                SC2Restful.enhance(mode);
                SC2Restful.afterEnhance(mode);
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
        Util.formatDateTimes();
    }

    static enhance(mode = START_MODE.FULL)
    {
        switch(mode)
        {
            case START_MODE.FULL:
                LadderUtil.enhanceLadderForm();
                CharacterUtil.enhanceSearchForm();
                CharacterUtil.enhanceAutoClanSearch();
                CharacterUtil.enhanceLoadAllCharacterReportsButton();
                LadderUtil.enhanceMyLadderForm();
                SeasonUtil.enhanceSeasonStateForm();
                BootstrapUtil.setFormCollapsibleScroll("form-ladder");
                BootstrapUtil.setFormCollapsibleScroll("form-following-ladder");
                Session.enhanceThemeInputs();
                ChartUtil.enhanceHeightControls();
            case START_MODE.MINIMAL:
                BootstrapUtil.init();
                BootstrapUtil.enhanceModals();
                BootstrapUtil.enhanceCollapsibles();
                CharacterUtil.enhanceMmrForm();
                CharacterUtil.enhanceReportForm();
                CharacterUtil.updateReportForm();
                CharacterUtil.enhanceMatchesHistoricalMmrInput();
                CharacterUtil.enhanceLoadMoreMatchesInput();
                FollowUtil.enhanceFollowButtons();
                BootstrapUtil.enhanceTabs();
                TeamUtil.enhanceTeamBuffer();
                BootstrapUtil.enhanceTooltips();
                ElementUtil.enhanceFullscreenToggles();
                Session.enhanceSerializable();
                ChartUtil.enhanceZoomToggles();
                ChartUtil.updateAspectRatioFromLocalStorage();
                Session.refreshTheme();
        }
    }

    static afterEnhance(mode = START_MODE.FULL)
    {
        CharacterUtil.afterEnhance();
        TeamUtil.afterEnhance();
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

SC2Restful.REM = parseInt(getComputedStyle(document.documentElement).fontSize);

SC2Restful.IMAGES = new Map
([
    ["bronze", ElementUtil.createImage("league/", "bronze", "icon-chart table-image table-image-square", SC2Restful.REM, SC2Restful.REM / 2)],
    ["silver", ElementUtil.createImage("league/", "silver", "icon-chart table-image table-image-square", SC2Restful.REM, SC2Restful.REM / 1.33)],
    ["gold", ElementUtil.createImage("league/", "gold", "icon-chart table-image table-image-square", SC2Restful.REM)],
    ["platinum", ElementUtil.createImage("league/", "platinum", "icon-chart table-image table-image-square",  SC2Restful.REM)],
    ["diamond", ElementUtil.createImage("league/", "diamond", "icon-chart table-image table-image-square",  SC2Restful.REM, SC2Restful.REM / 1.6 )],
    ["master", ElementUtil.createImage("league/", "master", "icon-chart table-image table-image-square", SC2Restful.REM)],
    ["grandmaster", ElementUtil.createImage("league/", "grandmaster", "icon-chart table-image table-image-square", SC2Restful.REM)],

    ["terran", ElementUtil.createImage("race/", "terran", "icon-chart table-image table-image-square", SC2Restful.REM)],
    ["protoss", ElementUtil.createImage("race/", "protoss", "icon-chart table-image table-image-square", SC2Restful.REM)],
    ["zerg", ElementUtil.createImage("race/", "zerg", "icon-chart table-image table-image-square", SC2Restful.REM)],
    ["random", ElementUtil.createImage("race/", "random", "icon-chart table-image table-image-square", SC2Restful.REM)],

    ["us", ElementUtil.createImage("flag/", "us", "icon-chart table-image table-image-long", SC2Restful.REM)],
    ["eu", ElementUtil.createImage("flag/", "eu", "icon-chart table-image table-image-long", SC2Restful.REM)],
    ["kr", ElementUtil.createImage("flag/", "kr", "icon-chart table-image table-image-long", SC2Restful.REM)],
    ["cn", ElementUtil.createImage("flag/", "cn", "icon-chart table-image table-image-long", SC2Restful.REM)]
]);
