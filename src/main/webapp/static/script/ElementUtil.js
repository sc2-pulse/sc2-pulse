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

    static createImage(prefix, name, classes, width, height = width)
    {
        const img = document.createElement("img");
        img.setAttribute("src", `${RESOURCE_PATH}icon/${prefix}${name}.svg`);
        img.setAttribute("alt", name);
        img.setAttribute("title", name);
        img.setAttribute("class", classes);
        if(width) {
            img.width = width;
            img.height = height;
        }
        return img;
    }

    static createNoRaceImage()
    {
        const noRace = document.createElement("span");
        noRace.classList.add("race-percentage", "race-percentage-none", "text-secondary", "table-image", "table-image-square");
        noRace.setAttribute("title", "no specific race");
        return noRace;
    }

    static createTagButton(tag, classes)
    {
        const elem = document.createElement(tag);
        elem.setAttribute("role", "button");
        elem.setAttribute("class", classes);
        return elem;
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
        cardHeader.textContent = Util.getTeamFormatAndTeamTypeString(teamFormat, teamType);
        cardHeader.classList.add("card-title");
        const table = TableUtil.createTable(["Race", "Best League", "Best MMR", "Total Games", "Current MMR", "Current Games"], true);
        table.classList.add("player-stats-table");
        const tableCaption = document.createElement("caption");
        tableCaption.appendChild(cardHeader);
        table.querySelector(":scope table").prepend(tableCaption);
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
            raceRace.appendChild(ElementUtil.createImage("race/", raceName, "table-image table-image-square"));
        }
        raceRow.insertCell().classList.add("player-stats-league", "player-stats-" + raceName + "-league");
        raceRow.insertCell().classList.add("player-stats-mmr", "player-stats-" + raceName + "-mmr");
        raceRow.insertCell().classList.add("player-stats-games", "player-stats-" + raceName + "-games");
        raceRow.insertCell().classList.add("player-stats-mmr", "player-stats-" + raceName + "-mmr-current");
        raceRow.insertCell().classList.add("player-stats-games", "player-stats-" + raceName + "-games-current");
        return raceRow;
    }

    static getTabTitle(hash)
    {
        if(hash == null) return "";

        return document.querySelector(hash).getAttribute("data-view-title");
    }

    static generateLadderTitle(params, hash, includeSeason = true)
    {
        return `${Util.getTeamFormatAndTeamTypeString(Session.currentTeamFormat, Session.currentTeamType)} ${ElementUtil.getTabTitle(hash)}${includeSeason ? ", " + Session.currentSeasons.find(s=>s.battlenetId == Session.currentSeason).descriptiveName : ""}`;
    }

    static generateCharacterTitle(params, hash)
    {
        const clanEl = document.querySelector("#player-info-title-clan");
        const teamEl = document.querySelector("#player-info-title-team");
        const clanAdditionalEl = document.querySelector("#player-info-title-clan-additional");
        const name = document.querySelector("#player-info-title-name").textContent;
        const clan = clanEl.classList.contains("d-none") ? "" : "[" + clanEl.textContent + "]";
        const team = teamEl.classList.contains("d-none") ? "" : "[" + teamEl.textContent + "]";
        const clanAdditional = clanAdditionalEl.classList.contains("d-none") ? "" : "[" + clanAdditionalEl.textContent + "]";
        const nameAdditional = document.querySelector("#player-info-title-name-additional").textContent;
        return `${clan}${team}${name}(${clanAdditional}${nameAdditional}) ${ElementUtil.getTabTitle(hash)}`;
    }

    static generateOnlineTitle(params, hash)
    {
        if(!params.get("to") || !params.get("period")) return "Online";
        const to = new Date(parseInt(params.get("to")));
        const period = EnumUtil.enumOfName(params.get("period"), PERIOD);
        var from = new Date(to.getTime());
        switch(period)
        {
            case PERIOD.DAY:
                from.setDate(to.getDate() - 1);
                break;
            case PERIOD.WEEK:
                from.setDate(to.getDate() - 7);
                break;
            case PERIOD.MONTH:
                from.setMonth(to.getMonth() - 1);
                break;
        }

        return `Online ${from.toISOString()} - ${to.toISOString()}`;
    }

    static generateLadderDescription(params, hash, includeSeason = true)
    {
        let desc = ElementUtil.getTabTitle(hash);

        if(desc == "MMR Ladder")
        {
            const pageFrom = params.get("page");
            const count = params.get("count");
            const page = pageFrom + count;
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
        + (includeSeason ? (", " + Session.currentSeasons.find(s=>s.battlenetId == Session.currentSeason).descriptiveName) : "")
        +  ".";
        return desc;
    }

    static generateCharacterDescription(params, hash)
    {
        const name = document.querySelector("#player-info-title-name").textContent;
        const battletag = document.querySelector("#link-battletag span").textContent;

        return `${name}/${battletag} career best MMR for all brackets/races, all seasons teams, mmr history, profile links. Social media links, match history, personal info for pro players`;
    }

    static generateGenericTitle(constructors, params, hash, dataTarget, attrSuffix)
    {
        const titleConstructor = constructors.get(dataTarget);
        return titleConstructor != null
            ? titleConstructor(params, hash)
            : (document.querySelector(dataTarget).getAttribute("data-view-" + attrSuffix)
                || document.querySelector(dataTarget).getAttribute("data-view-title"));
    }

    static updateTitleAndDescription(params, hash, dataTarget)
    {
        const generatedTitle = ElementUtil.generateGenericTitle(ElementUtil.TITLE_CONSTRUCTORS, params, hash, dataTarget, "title");
        document.title = generatedTitle ? (generatedTitle + " - " + SC2Restful.SITE_NAME) : SC2Restful.SITE_NAME;
        document.querySelector('meta[name="description"]').setAttribute("content",
            ElementUtil.generateGenericTitle(ElementUtil.DESCRIPTION_CONSTRUCTORS, params, hash, dataTarget, "description"));
    }

    static setMainContent(id)
    {
        for(const main of document.querySelectorAll('*[role="main"]')) main.removeAttribute("role");
        document.querySelector(id).setAttribute("role", "main");
    }

    static removeNofollowRels(rootId)
    {
        for(const a of document.getElementById(rootId).querySelectorAll(':scope a[rel~="nofollow"]'))
            a.relList.remove("nofollow");
    }

    static removeParentAndChildrenAttributes(elem, attrs)
    {
        for(const attr of attrs)
        {
            elem.removeAttribute(attr);
            for(const e of elem.querySelectorAll(":scope [" + attr + "]")) e.removeAttribute(attr);
        }
    }

    static enhanceFullscreenToggles()
    {
        for(const toggle of document.querySelectorAll(".fullscreen-toggle")) toggle.addEventListener("click", ElementUtil.onFullScreenToggle);
    }

    static onFullScreenToggle(evt)
    {
        if(document.fullscreenElement == null)
        {
            const el = document.getElementById(evt.target.getAttribute("data-target"));
            for(const t of document.querySelectorAll(".fullscreen-required")) el.prepend(t)
            el.requestFullscreen();
        }
        else
        {
            for(const t of document.querySelectorAll(".fullscreen-required"))
                document.querySelector(t.getAttribute("data-original-parent")).appendChild(t);
            document.exitFullscreen();
        }
    }

    static changeInputValue(input, val)
    {
        switch(input.getAttribute("type"))
        {
            case "date":
            {
                const date = new Date(parseInt(val));
                if(input.getAttribute("data-exclusive")) date.setDate(date.getDate() - 1);
                input.valueAsNumber = date.getTime() - new Date().getTimezoneOffset() * 60 * 1000;
                break;
            }
            case "checkbox":
            case "radio":
                input.checked = val;
                break;
            default:
                input.value = val;
                break;
        }
        input.dispatchEvent(new Event("change"));
    }

    static createCheaterFlag()
    {
        const cheaterFlag = document.createElement("span");
        cheaterFlag.classList.add("player-flag", "player-flag-cheater");
        cheaterFlag.textContent = "CHEATER";
        cheaterFlag.title="This player has a confirmed evidence of cheating. See 'Player' and 'Characters' tabs for more info.";
        return cheaterFlag;
    }

    static createProFlag()
    {
        const flag = document.createElement("span");
        flag.classList.add("player-flag", "player-flag-pro");
        flag.textContent = "PRO";
        flag.title="This player is identified by sc2revealed.com as a pro player. See 'Player' tab for more info";
        return flag;
    }

}

ElementUtil.ELEMENT_RESOLVERS = new Map();
ElementUtil.INPUT_TIMEOUTS = new Map();
ElementUtil.TITLE_CONSTRUCTORS = new Map();
ElementUtil.DESCRIPTION_CONSTRUCTORS = new Map();
ElementUtil.NEGATION_PREFIX = "neg-";
ElementUtil.INPUT_TIMEOUT = 1000;
