// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class SeasonUtil
{

    static seasonIdTranslator(id)
    {
        const season = Session.currentSeasons.filter((s)=>s.id == id)[0];
        return `${season.year} season ${season.number} (${season.id})`;
    }

    static getSeasons()
    {
        Util.setGeneratingStatus("begin");
        return fetch("api/seasons")
            .then(resp => {if (!resp.ok) throw new Error(resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{SeasonUtil.updateSeasons(json); Util.setGeneratingStatus("success"); res();}))
            .catch(error => Util.setGeneratingStatus("error", error.message));
    }

    static updateSeasons(seasons)
    {
        Session.currentSeasons = seasons;
        SeasonUtil.updateSeasonsTabs(seasons);
        for(const seasonPicker of document.querySelectorAll(".season-picker"))
        {
            ElementUtil.removeChildren(seasonPicker);
            for(const season of seasons)
            {
                const option = document.createElement("option");
                option.setAttribute("label", season.year + " s" + season.number);
                option.textContent = `${season.year} s${season.number}`;
                option.setAttribute("value", season.id);
                seasonPicker.appendChild(option);
            }
        }
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
            const tableCaption = document.createElement("caption");
            tableCaption.appendChild(document.createElement("h4"));
            table.childNodes[0].prepend(tableCaption);
            pane.appendChild(table);
        }
        teamSection.appendChild(seasonPills.pane);
    }

}
