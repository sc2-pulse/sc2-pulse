// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class SeasonUtil
{

    static seasonIdTranslator(id)
    {
        const season = Session.currentSeasonsIdMap.get(parseInt(id))[0];
        return SeasonUtil.seasonTranslator(season);
    }

    static seasonTranslator(season)
    {
        const seasonEnd = season.end;
        const endDate = seasonEnd.getTime() - Date.now() > 0 ? new Date() : seasonEnd;
        return `${season.year} season ${season.number} (${season.battlenetId}) (${Util.MONTH_DATE_FORMAT.format(endDate)})`;
    }

    static getSeasons()
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return Session.beforeRequest()
            .then(n=>fetch(ROOT_CONTEXT_PATH + "api/season/list/all"))
            .then(Session.verifyJsonResponse)
            .then(json => {
                SeasonUtil.updateSeasons(json);
                Util.setGeneratingStatus(STATUS.SUCCESS);
            })
            .catch(error => Session.onPersonalException(error));
    }

    static updateSeasons(seasons)
    {
        const regionMap = Util.groupBy(seasons, s=>s.region);
        Session.currentSeasons = Array.from(regionMap.values()).reduce((max, cur)=>max.length > cur.length ? max : cur);
        const seasonMap = new Map();
        Array.from(regionMap.entries()).forEach(e=>seasonMap.set(e[0], Util.groupBy(e[1], s=>s.battlenetId)));
        Session.currentSeasonsMap = seasonMap;
        Session.currentSeasonsIdMap = Array.from(seasonMap.values()).reduce((max, cur)=>max.size > cur.size ? max : cur);
        for(const season of seasons) SeasonUtil.updateSeasonMeta(season);
        for(const seasonPicker of document.querySelectorAll(".season-picker"))
        {
            ElementUtil.removeChildren(seasonPicker);
            for(const season of Session.currentSeasons)
            {
                const option = document.createElement("option");
                option.setAttribute("label", season.descriptiveName);
                option.textContent = season.descriptiveName;
                option.setAttribute("value", season.battlenetId);
                seasonPicker.appendChild(option);
            }
            seasonPicker.value = Session.currentSeasons[0].battlenetId;
        }
    }

    static updateSeasonDuration(season)
    {
        season.durationProgress = season.nowOrEnd - season.start;
        season["daysProgress"] = season.durationProgress / (1000 * 60 * 60 * 24);
    }

    static updateSeasonDescription(season)
    {
        season.descriptiveName = SeasonUtil.seasonTranslator(season);
    }

    static updateSeasonDates(season)
    {
        season.start = Util.parseIsoDateOrDateTime(season.start);
        season.end = Util.parseIsoDateOrDateTime(season.end);
        const now = new Date();
        season.nowOrEnd = now - season.end < 0 ? now : season.end;
    }

    static updateSeasonMeta(season)
    {
        SeasonUtil.updateSeasonDates(season);
        SeasonUtil.updateSeasonDuration(season);
        SeasonUtil.updateSeasonDescription(season);
    }

    static updateSeasonState(searchParams)
    {
        searchParams.append("type", "online");
        const stringParams = searchParams.toString();
        const params = {params: stringParams};
        Util.setGeneratingStatus(STATUS.BEGIN);
        return SeasonUtil.updateSeasonStateModel(searchParams)
            .then(e=>{
                SeasonUtil.updateSeasonStateView(searchParams);
                Util.setGeneratingStatus(STATUS.SUCCESS);
                if(!Session.isHistorical) HistoryUtil.pushState(params, document.title, "?" + stringParams + "#online");
                Session.currentSearchParams = stringParams;
                if(!Session.isHistorical) HistoryUtil.updateActiveTabs();
            })
            .catch(error => Session.onPersonalException(error));
    }

    static updateSeasonStateModel(searchParams)
    {
        const request = `${ROOT_CONTEXT_PATH}api/season/state/${searchParams.get("to")}/${searchParams.get("period")}`;
        return Session.beforeRequest()
            .then(n=>fetch(request))
            .then(Session.verifyJsonResponse)
            .then(json => {
                Model.DATA.get(VIEW.ONLINE).set(VIEW_DATA.SEARCH, json);
                return json;
            });
    }

    static updateSeasonStateView(searchParams)
    {
        SeasonUtil.updateSeasonStateViewPart(searchParams, "online-players-table", "playerCount");
        SeasonUtil.updateSeasonStateViewPart(searchParams, "online-games-table", "gamesPlayed");
        document.querySelector("#online-data").classList.remove("d-none");
    }

    static updateSeasonStateViewPart(searchParams, tableId, param)
    {
        const searchResult = Model.DATA.get(VIEW.ONLINE).get(VIEW_DATA.SEARCH);
        const data = {};
        for(const state of searchResult)
        {
            if(data[state.seasonState.periodStart] == null) data[state.seasonState.periodStart] = {};
            data[state.seasonState.periodStart][state.season.region] = state.seasonState[param] < 0 ? 0 : state.seasonState[param];
        }
        ChartUtil.CHART_RAW_DATA.set(tableId, {});
        TableUtil.updateVirtualColRowTable
        (
            document.getElementById(tableId),
            data,
            (tableData=>ChartUtil.CHART_RAW_DATA.get(tableId).data = tableData),
            (a, b)=>EnumUtil.enumOfName(a, REGION).order - EnumUtil.enumOfName(b, REGION).order,
            (name)=>EnumUtil.enumOfName(name, REGION).name,
            (dateTime)=>new Date(dateTime).getTime()
        );
    }

    static enhanceSeasonStateForm()
    {
        const form = document.querySelector("#form-online");
        document.querySelector("#online-to").valueAsNumber = Date.now();
        form.addEventListener("submit", evt=>{
            evt.preventDefault();
            const fd = new FormData(form);
            //the API expects exclusive "to", but user expects inclusive "to"
            const date = new Date(document.querySelector("#online-to").valueAsNumber);
            date.setHours(0, 0, 0, 0);
            date.setDate(date.getDate() + 1)
            fd.set("to", date.getTime());
            SeasonUtil.updateSeasonState(new URLSearchParams(Util.urlencodeFormData(fd)));
        });
    }

    static isCurrentSeason(season)
    {
        if(SeasonUtil.maxSeason == null) SeasonUtil.maxSeason
            = Math.max.apply(Math, Array.from(Session.currentSeasonsIdMap.keys()));
        return season == SeasonUtil.maxSeason;
    }

    static calculateAbnormalSeasons(seasons)
    {
        if(!seasons) seasons = Array.from(Session.currentSeasonsIdMap.values()).map(v=>v[0]);
        const seasonDurations = seasons.map(s=>s.durationProgress);
        const meanDuration = seasonDurations.reduce((p, c)=>p + c, 0) / seasonDurations.length;
        //calculate only short seasons, ignore long
        const stDev = Util.stDev(seasonDurations, true) * -1;
        return new Set(seasons
            .filter(season=>season.durationProgress - meanDuration < stDev)
            .map(season=>season.battlenetId));
    }

    static isAbnormalSeason(season)
    {
        if(!SeasonUtil.abnormalSeasons) SeasonUtil.abnormalSeasons = SeasonUtil.calculateAbnormalSeasons();
        return SeasonUtil.abnormalSeasons.has(parseInt(season));
    }

}
