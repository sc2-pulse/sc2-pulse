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
            a.setAttribute("data-target", "#" + name);
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

    static updateTabLinks(params)
    {
        for(link of document.querySelectorAll(".nav-pills:not(.nav-pills-main) a[data-target]"))
            link.setAttribute("href", link.getAttribute("data-target"));

        const paramsNoTabs = new URLSearchParams(params);
        paramsNoTabs.delete("t");
        const paramsNoTabsStr = paramsNoTabs.toString();

        let root = "";
        switch(paramsNoTabs.get("type"))
        {
            case "ladder":
                root = "#stats";
                break;
            case "character":
                root = "#player-info";
                break;
            default:
                return;
        }

        for(link of document.querySelectorAll(root + " .nav-pills a"))
        {
            const curParams = new URLSearchParams(paramsNoTabsStr);
            const tabChain = [];
            let curTab = document.querySelector(link.getAttribute("data-target"));
            while(curTab != null)
            {
                tabChain.push(curTab.id);
                curTab = curTab.parentElement.closest(".tab-pane");
            }
            for(let i = tabChain.length - 1; i > -1; i--)
                curParams.append("t", tabChain[i]);
            link.setAttribute("href", "?" + curParams.toString());
        }
    }

    static getTabTitle(params)
    {
        const tabs = params.getAll("t");
        if(tabs.length < 1) return "";

        const tab = tabs[tabs.length - 1];
        return document.querySelector("#" + tab).getAttribute("data-view-title");
    }

    static generateLadderTitle(params, includeSeason = true)
    {
        return `${Session.currentTeamType.secondaryName} ${Session.currentTeamFormat.name} ${ElementUtil.getTabTitle(params)}${includeSeason ? ", " + SeasonUtil.seasonIdTranslator(Session.currentSeason) : ""}`;
    }

    static generateCharacterTitle(params)
    {
        const name = document.querySelector("#player-info-title-name").textContent;
        const nameAdditional = document.querySelector("#player-info-title-name-additional").textContent;
        return `${name}${nameAdditional} ${ElementUtil.getTabTitle(params)}`;
    }

    static generateLadderDescription(params, includeSeason = true)
    {
        let desc = ElementUtil.getTabTitle(params);

        if(desc == "MMR Ladder")
        {
            const pageFrom = params.get("page");
            const count = params.get("count");
            const forward = params.get("forward");
            const page = forward ? pageFrom + count : pageFrom - count;
            const rankFrom = ((page - 1) * 100) + 1;
            desc += ", rank " +  rankFrom + "-" + (rankFrom + 99);
        }

        desc += ". Regions: ";
        let regionAdded = false;
        for(const region of Object.values(REGION))
        {
            if(params.get(region.name))
            {
                if(regionAdded) desc += ", ";
                desc += region.name;
                regionAdded = true;
            }
        }

        let leagueAdded = false;
        desc += ". Leagues: ";
        for(const league of Object.values(LEAGUE))
        {
            if(params.get(league.name.substring(0, 3)))
            {
                if(leagueAdded) desc += ", ";
                desc += league.name;
                leagueAdded = true;
            }
        }

        desc += ". " + Session.currentTeamType.secondaryName + " " + Session.currentTeamFormat.name
        + (includeSeason ? (", " + SeasonUtil.seasonIdTranslator(Session.currentSeason)) : "")
        +  ".";
        return desc;
    }

    static generateCharacterDescription(params)
    {
        const name = document.querySelector("#player-info-title-name").textContent;
        const battletag = document.querySelector("#player-info-battletag").textContent;

        return `${name}/${battletag} career best MMR for all brackets/races. BattleNet profile and all seasons history`;
    }

    static generateGenericTitle(constructors, params, dataTarget, attrSuffix)
    {
        const titleConstructor = constructors.get(dataTarget);
        return titleConstructor != null
            ? titleConstructor(params)
            : (document.querySelector(dataTarget).getAttribute("data-view-" + attrSuffix)
                || document.querySelector(dataTarget).getAttribute("data-view-title"));
    }

    static updateTitleAndDescription(params, dataTarget)
    {
        const generatedTitle = ElementUtil.generateGenericTitle(ElementUtil.TITLE_CONSTRUCTORS, params, dataTarget, "title");
        document.title = generatedTitle ? (generatedTitle + " - " + SC2Restful.SITE_NAME) : SC2Restful.SITE_NAME;
        document.querySelector('meta[name="description"]').setAttribute("content",
            ElementUtil.generateGenericTitle(ElementUtil.DESCRIPTION_CONSTRUCTORS, params, dataTarget, "description"));
    }

    static setMainContent(id)
    {
        for(const main of document.querySelectorAll('*[role="main"]')) main.removeAttribute("role");
        document.querySelector(id).setAttribute("role", "main");
    }

    static removeNofollowRels(rootId)
    {
        for(a of document.getElementById(rootId).querySelectorAll(':scope a[rel~="nofollow"]'))
            a.relList.remove("nofollow");
    }

    static removeParentAndChildrenAttributes(elem, attrs)
    {
        for(attr of attrs)
        {
            elem.removeAttribute(attr);
            for(e of elem.querySelectorAll(":scope [" + attr + "]")) e.removeAttribute(attr);
        }
    }

}

ElementUtil.ELEMENT_RESOLVERS = new Map();
ElementUtil.TITLE_CONSTRUCTORS = new Map();
ElementUtil.DESCRIPTION_CONSTRUCTORS = new Map();
ElementUtil.NEGATION_PREFIX = "neg-";
