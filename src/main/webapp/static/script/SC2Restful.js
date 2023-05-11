// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class SC2Restful
{

    static baseStart()
    {
        Session.initThemes();
        Session.updateStyleOverride();
        Session.APPLICATION_VERSION = APPLICATION_VERSION;
    }

    static start(mode = START_MODE.FULL)
    {
        if(mode !== START_MODE.BARE)
            window.addEventListener("popstate", e=>{HistoryUtil.restoreState(e)});
        if(mode == START_MODE.BARE) {
            Util.formatDateTimes();
            ChartUtil.init();
            SC2Restful.enhance(mode);
            return Promise.resolve(START_MODE.BARE);
        }
        else if(mode == START_MODE.ESSENTIAL) {
            SC2Restful.initAll();
            Session.restoreState();
            SC2Restful.enhance(mode);
            ChartUtil.observeChartables();
            HistoryUtil.initActiveTabs();
            ChartUtil.observeCharts();
            return Promise.resolve(1);
        } else {
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
    }

    static initAll()
    {
        Model.init();
        ChartUtil.init();
        Util.formatDateTimes();
    }

    static enhance(mode = START_MODE.FULL)
    {
        switch(mode)
        {
            case START_MODE.FULL:
                LadderUtil.enhanceLadderForm();
                CharacterUtil.enhanceSearchForm();
                ClanUtil.enhanceClanSearchForm();
                VODUtil.enhance();
                CharacterUtil.enhanceAutoClanSearch();
                CharacterUtil.enhanceAllCharacterReportsControls();
                LadderUtil.enhanceMyLadderForm();
                SeasonUtil.enhanceSeasonStateForm();
                StatsUtil.enhanceRaceControls();
                StatsUtil.enhanceMatchUpControls();
                StatsUtil.enhanceSettings();
                StatsUtil.addMatchUpLegend();
                BootstrapUtil.setFormCollapsibleScroll("form-ladder");
                BootstrapUtil.setFormCollapsibleScroll("form-following-ladder");
                BootstrapUtil.enhanceEmbedBackdropCloseControls();
                Session.enhanceThemeInputs();
                Session.enhanceCheaterVisibilityInput();
                Session.enhanceCsrfForms();
                ChartUtil.enhanceHeightControls();
                ChartUtil.enhanceBeginAtZeroControls();
                ChartUtil.enhanceMmrAnnotationControls();
                StatsUtil.updateGamesStatsVisibility();
            case START_MODE.MINIMAL:
                CharacterUtil.enhanceMmrForm();
                CharacterUtil.enhanceReportForm();
                CharacterUtil.updateReportForm();
                CharacterUtil.enhanceMatchesHistoricalMmrInput();
                CharacterUtil.enhanceLoadMoreMatchesInput();
                CharacterUtil.enhanceMatchTypeInput();
                CharacterUtil.enhanceDynamicCharacterData();
                RevealUtil.enhanceCtl();
                VersusUtil.enhance();
                FollowUtil.enhanceFollowButtons();
                BufferUtil.enhance();
            case START_MODE.ESSENTIAL:
                BootstrapUtil.enhanceTabs();
            case START_MODE.BARE:
                BootstrapUtil.init();
                BootstrapUtil.enhanceModals();
                BootstrapUtil.enhanceCollapsibles();
                FormUtil.enhanceFormInputGroupFilters();
                FormUtil.enhanceFormGroups();
                FormUtil.initInputStateLinks();
                FormUtil.linkInputStateBindings();
                FormUtil.enhanceFormConfirmations();
                BootstrapUtil.enhanceTooltips();
                ElementUtil.enhanceFullscreenToggles();
                ElementUtil.enhanceCopyToClipboard();
                Session.enhanceSerializable();
                ChartUtil.enhanceZoomToggles();
                ChartUtil.enhanceTimeAxisToggles();
                ChartUtil.updateHeightFromLocalStorage();
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
    ["terran", "rgba(1, 90, 145, 1)"],
    ["protoss", "rgba(222, 201, 62, 1)"],
    ["zerg", "rgba(136, 41, 145, 1)"],
    ["random", "rgba(150, 150, 150, 1)"],
    ["us", "rgba(23, 162, 184, 1)"],
    ["eu", "rgba(255, 193, 7, 1)"],
    ["kr", "rgba(108, 117, 125, 1)"],
    ["cn", "rgba(220, 53, 69, 1)"],
    ["bronze", "rgba(185, 113, 45, 1)"],
    ["silver", "rgba(115, 115, 115, 1)"],
    ["gold", "rgba(255, 215, 0, 1)"],
    ["platinum", "rgba(165, 164, 163, 1)"],
    ["diamond", "rgba(13, 69, 148, 1)"],
    ["master", "rgba(0, 177, 251, 1)"],
    ["grandmaster", "rgba(239, 62, 0, 1)"]
]);
SC2Restful.MULTI_COLORS = new Map
([
    ["tvt", [SC2Restful.COLORS.get("terran"), SC2Restful.COLORS.get("terran")]],
    ["tvp", [SC2Restful.COLORS.get("terran"), SC2Restful.COLORS.get("protoss")]],
    ["tvz", [SC2Restful.COLORS.get("terran"), SC2Restful.COLORS.get("zerg")]],
    ["tvr", [SC2Restful.COLORS.get("terran"), SC2Restful.COLORS.get("random")]],
    ["pvt", [SC2Restful.COLORS.get("protoss"), SC2Restful.COLORS.get("terran")]],
    ["pvp", [SC2Restful.COLORS.get("protoss"), SC2Restful.COLORS.get("protoss")]],
    ["pvz", [SC2Restful.COLORS.get("protoss"), SC2Restful.COLORS.get("zerg")]],
    ["pvr", [SC2Restful.COLORS.get("protoss"), SC2Restful.COLORS.get("random")]],
    ["zvt", [SC2Restful.COLORS.get("zerg"), SC2Restful.COLORS.get("terran")]],
    ["zvp", [SC2Restful.COLORS.get("zerg"), SC2Restful.COLORS.get("protoss")]],
    ["zvz", [SC2Restful.COLORS.get("zerg"), SC2Restful.COLORS.get("zerg")]],
    ["zvr", [SC2Restful.COLORS.get("zerg"), SC2Restful.COLORS.get("random")]],
    ["rvt", [SC2Restful.COLORS.get("random"), SC2Restful.COLORS.get("terran")]],
    ["rvp", [SC2Restful.COLORS.get("random"), SC2Restful.COLORS.get("protoss")]],
    ["rvz", [SC2Restful.COLORS.get("random"), SC2Restful.COLORS.get("zerg")]],
    ["rvr", [SC2Restful.COLORS.get("random"), SC2Restful.COLORS.get("random")]]
]);

SC2Restful.UNIQUE_COLORS = [...new Set(SC2Restful.COLORS.values())];

SC2Restful.SITE_NAME = "SC2 Pulse";
SC2Restful.MMR_HISTORY_START_DATE = new Date("2021-01-19T00:00:00");
SC2Restful.MMR_HISTORY_DAYS_MAX = 90;
SC2Restful.REDIRECT_PAGE_TIMEOUT_MILLIS = 3500;
SC2Restful.GM_COUNT = 200;

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

    ["tier-1", ElementUtil.createImage("league/", "tier-1", "icon-chart table-image table-image-square", SC2Restful.REM, SC2Restful.REM)],
    ["tier-2", ElementUtil.createImage("league/", "tier-2", "icon-chart table-image table-image-square", SC2Restful.REM, SC2Restful.REM)],
    ["tier-3", ElementUtil.createImage("league/", "tier-3", "icon-chart table-image table-image-square", SC2Restful.REM, SC2Restful.REM)],

    ["terran", ElementUtil.createImage("race/", "terran", "icon-chart table-image table-image-square", SC2Restful.REM)],
    ["protoss", ElementUtil.createImage("race/", "protoss", "icon-chart table-image table-image-square", SC2Restful.REM)],
    ["zerg", ElementUtil.createImage("race/", "zerg", "icon-chart table-image table-image-square", SC2Restful.REM)],
    ["random", ElementUtil.createImage("race/", "random", "icon-chart table-image table-image-square", SC2Restful.REM)],

    ["us", ElementUtil.createImage("flag/", "us", "icon-chart table-image table-image-long", SC2Restful.REM)],
    ["eu", ElementUtil.createImage("flag/", "eu", "icon-chart table-image table-image-long", SC2Restful.REM)],
    ["kr", ElementUtil.createImage("flag/", "kr", "icon-chart table-image table-image-long", SC2Restful.REM)],
    ["cn", ElementUtil.createImage("flag/", "cn", "icon-chart table-image table-image-long", SC2Restful.REM)]
]);
