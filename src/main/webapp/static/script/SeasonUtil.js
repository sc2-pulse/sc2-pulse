// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class SeasonUtil
{

    static seasonIdTranslator(id)
    {
        const season = Session.currentSeasons.filter((s)=>s.battlenetId == id)[0];
        const seasonEnd = Util.parseIsoDate(season.end);
        const endDate = seasonEnd.getTime() - Date.now() > 0 ? new Date() : seasonEnd;
        return `${season.year} season ${season.number} (${season.battlenetId}) (${Util.MONTH_DATE_FORMAT.format(endDate)})`;
    }

    static getSeasons()
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return fetch("api/seasons")
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{SeasonUtil.updateSeasons(json); Util.setGeneratingStatus(STATUS.SUCCESS); res();}))
            .catch(error => Util.setGeneratingStatus(STATUS.ERROR, error.message, error));
    }

    static updateSeasons(seasons)
    {
        Session.currentSeasons = seasons;
        for(const season of seasons) SeasonUtil.updateSeasonMeta(season);
        SeasonUtil.updateSeasonsTabs(seasons);
        for(const seasonPicker of document.querySelectorAll(".season-picker"))
        {
            ElementUtil.removeChildren(seasonPicker);
            for(const season of seasons)
            {
                const option = document.createElement("option");
                option.setAttribute("label", season.descriptiveName);
                option.textContent = season.descriptiveName;
                option.setAttribute("value", season.battlenetId);
                seasonPicker.appendChild(option);
            }
        }
    }

    static updateSeasonDuration(season)
    {
        const startDate = Util.parseIsoDate(season.start);
        let endDate = Util.parseIsoDate(season.end);
        const now = new Date();
        if(now - endDate < 0) endDate = now;
        season["days"] = (endDate - startDate) / (1000 * 60 * 60 * 24);
    }

    static updateSeasonDescription(season)
    {
        season.descriptiveName = SeasonUtil.seasonIdTranslator(season.battlenetId);
    }

    static updateSeasonMeta(season)
    {
        SeasonUtil.updateSeasonDuration(season);
        SeasonUtil.updateSeasonDescription(season);
    }

    static updateSeasonsTabs(seasons)
    {
        const seasonPills = ElementUtil.createTabList(seasons.length, "character-teams-season", "4");
        seasonPills.nav.classList.add("d-none");
        const teamSection = document.getElementById("character-teams-section");
        BootstrapUtil.enhanceTabSelect(document.getElementById("teams-season-select"), seasonPills.nav);
        teamSection.appendChild(seasonPills.nav);
        for(const pane of seasonPills.pane.getElementsByClassName("tab-pane"))
        {
            const table = TableUtil.createTable(["Format", "Rank", "MMR", "League", "Region", "Team", "Games", "Win%"]);
            table.querySelector("table").id = pane.id + "-table";
            const headers = table.querySelectorAll(":scope thead th");
            TableUtil.hoverableColumnHeader(headers[1]);
            TableUtil.hoverableColumnHeader(headers[6]);
            table.getElementsByTagName("table")[0].setAttribute("data-ladder-format-show", "true");
            pane.appendChild(table);
        }
        teamSection.appendChild(seasonPills.pane);
    }

}
