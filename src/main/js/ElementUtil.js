// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class ElementUtil
{

    static resolveElementPromise(id)
    {
        const resolver = ElementUtil.ELEMENT_RESOLVERS.get(id);
        if(resolver != null)
        {
            resolver(id);
            ElementUtil.ELEMENT_RESOLVERS.delete(id);
            return true;
        }
        return false;
    }

    static createTabList(count, prefix, hLevel, fade = false)
    {
        const nav = document.createElement("nav");
        const panes = document.createElement("div");
        panes.classList.add("tab-content");
        const ul = document.createElement("ul");
        ul.classList.add("nav", "nav-pills", "mb-3", "justify-content-center");
        ul.setAttribute("role", "tablist");
        nav.appendChild(ul);
        nav.setAttribute("id", prefix + "-nav")
        for(let i = 0; i < count; i++)
        {
            const li = document.createElement("li");
            li.classList.add("nav-item");
            const a = document.createElement("a");
            const name = prefix + "-" + i;
            a.classList.add("nav-link");
            a.setAttribute("id", name + '-link');
            a.setAttribute("data-toggle", "pill");
            a.setAttribute("href", "#" + name);
            a.setAttribute("role", "tab");
            a.setAttribute("aria-controls", name)
            a.setAttribute("aria-selected", "false");
            $(a).on("click", function(e) {
                e.preventDefault();
                $(this).tab("show");
            });
            li.appendChild(a);
            ul.appendChild(li);

            const pane = document.createElement("section");
            pane.setAttribute("id", name);
            pane.classList.add("tab-pane");
            if(fade) pane.classList.add("fade");
            const h = document.createElement("h" + hLevel);
            pane.appendChild(h);
            panes.appendChild(pane);
        }
        return {nav: nav, pane: panes};
    }

    static updateTabSelect(select, navs)
    {
        ElementUtil.removeChildren(select);
        for (const nav of navs)
        {
            if(!nav.classList.contains("d-none"))
            {
                const link = nav.getElementsByClassName("nav-link")[0];
                const option = document.createElement("option");
                option.textContent = link.textContent;
                option.value = link.textContent;
                option.setAttribute("data-tab", link.getAttribute("id"));
                select.appendChild(option);
                if(link.getAttribute("aria-selected") == "true") select.value = option.value;
            }
        }
    }

    static createImage(prefix, name, classes)
    {
        const img = document.createElement("img");
        img.setAttribute("src", `${RESOURCE_PATH}icon/${prefix}${name}.svg`);
        img.setAttribute("alt", name);
        img.setAttribute("title", name);
        for(const clazz of classes) img.classList.add(clazz);
        return img;
    }

    static createNoRaceImage()
    {
        const noRace = document.createElement("span");
        noRace.classList.add("race-percentage", "race-percentage-none", "text-secondary", "table-image", "table-image-square");
        noRace.setAttribute("title", "no specific race");
        return noRace;
    }

    static setElementsVisibility(elems, visible)
    {
        for (const elem of elems)
        {
            const clazz = elem.getAttribute("data-hide-mode") === "hide"
                ? "invisible"
                : "d-none";
            if(!visible)
            {
                elem.classList.add(clazz);
            }
            else
            {
                elem.classList.remove(clazz);
            }
        }
    }

    static disableElements(elems, disable)
    {
        for (var i = 0; i < elems.length; i++)
        {
            if (disable)
            {
                elems[i].setAttribute("disabled", "disabled");
            }
            else
            {
                elems[i].removeAttribute("disabled");
            }
        }
    }

    static removeChildren(node)
    {
        while(node.hasChildNodes())
        {
            node.removeChild(node.lastChild);
        }
    }

    static createPlayerStatsCards(container)
    {
        for(const teamFormat of Object.values(TEAM_FORMAT))
        {
            for(const teamType of Object.values(TEAM_TYPE))
            {
                container.appendChild(ElementUtil.createPlayerStatsCard(teamFormat, teamType));
            }
        }
    }

    static createPlayerStatsCard(teamFormat, teamType)
    {
        const card = document.createElement("div");
        card.classList.add("card", "card-equal", "player-stats-section", "player-stats-dynamic", "mb-3");
        card.setAttribute("id", "player-stats-" + teamFormat.name + "-" + teamType.name)
        const cardBody = document.createElement("div");
        cardBody.classList.add("card-body");
        card.appendChild(cardBody);
        const cardHeader = document.createElement("h4");
        cardHeader.textContent = teamFormat.name + " " + teamType.name;
        cardHeader.classList.add("card-title");
        const table = TableUtil.createTable(["Race", "MMR", "League", "Games"], false);
        table.classList.add("player-stats-table");
        const tableCaption = document.createElement("caption");
        tableCaption.appendChild(cardHeader);
        table.prepend(tableCaption);
        const tbody = table.getElementsByTagName("tbody")[0];
        for(const race of Object.values(RACE)) tbody.appendChild(ElementUtil.createPlayerStatsRaceRow(race.name));
        tbody.appendChild(ElementUtil.createPlayerStatsRaceRow("all"));
        cardBody.appendChild(table);
        return card;
    }

    static createPlayerStatsRaceRow(raceName)
    {
        const raceRow = document.createElement("tr");
        raceRow.classList.add("player-stats-" + raceName, "player-stats-dynamic");
        const raceRace = TableUtil.createRowTh(raceRow);
        raceRace.classList.add("player-stats-race", "player-stats-" + raceName + "-race");
        if(raceName === "all")
        {
            raceRace.appendChild(ElementUtil.createNoRaceImage());
        }
        else
        {
            raceRace.appendChild(ElementUtil.createImage("race/", raceName, ["table-image", "table-image-square"]));
        }
        raceRow.insertCell().classList.add("player-stats-mmr", "player-stats-" + raceName + "-mmr");
        raceRow.insertCell().classList.add("player-stats-league", "player-stats-" + raceName + "-league");
        raceRow.insertCell().classList.add("player-stats-games", "player-stats-" + raceName + "-games");
        return raceRow;
    }

    static getTabTitle(params)
    {
        const tabs = params.getAll("t");
        if(tabs.length < 1) return "SC2 Ladder Generator";

        const tab = tabs[tabs.length - 1];
        return document.querySelector("#" + tab).getAttribute("data-view-title");
    }

    static generateLadderTitle(params)
    {
        return `${Session.currentTeamType.name} ${Session.currentTeamFormat.name} ${ElementUtil.getTabTitle(params)}, ${SeasonUtil.seasonIdTranslator(Session.currentSeason)}`;
    }

    static generateCharacterTitle(params)
    {
        const name = document.querySelector("#player-info-title-name").textContent;
        return `${name} ${ElementUtil.getTabTitle(params)}`;
    }

}

ElementUtil.ELEMENT_RESOLVERS = new Map();
ElementUtil.TITLE_CONSTRUCTORS = new Map();
ElementUtil.NEGATION_PREFIX = "neg-";
