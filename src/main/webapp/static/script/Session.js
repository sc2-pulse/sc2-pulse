// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class Session
{

    static getMyInfo()
    {
        if(!Session.isAuthenticated()) return Promise.resolve(1);
        return PersonalUtil.getMyAccount()
            .then(e=>{
                Session.updateMyInfoThen();
                Session.sessionStartTimestamp = Date.now();
                return e
            });
    }

    static onPersonalException(error)
    {
        if(error.message.startsWith(Session.INVALID_API_VERSION_CODE)) {
            Session.updateApplicationVersion();
            return;
        }
        Util.setGeneratingStatus(STATUS.ERROR, error.message, error);
    }

    static beforeRequest()
    {
        return Promise.resolve();
    }

    static verifyResponse(resp)
    {
        if (!resp.ok) throw new Error(resp.status + " " + resp.statusText);
        const versionHeader = resp.headers.get("X-Application-Version");
        const cacheHeader = resp.headers.get("Cache-Control");
        if((!cacheHeader || cacheHeader.toLowerCase().includes("max-age=0")) && versionHeader && versionHeader != Session.APPLICATION_VERSION)
            throw new Error(Session.INVALID_API_VERSION_CODE + " API version has changed");
        return Promise.resolve(resp);
    }

    static verifyJsonResponse(resp)
    {
        return Session.verifyResponse(resp)
            .then(resp=>resp.json());
    }

    static updateApplicationVersion()
    {
        Util.setGeneratingStatus(STATUS.SUCCESS);
        $("#application-version-update").modal();
    }


    static renewBlizzardRegistration()
    {
        if(Session.currentAccount != null)
        {
            Util.setGeneratingStatus(STATUS.SUCCESS);
            $("#error-session").modal();
        }
        else
        {
            return Session.doRenewBlizzardRegistration();
        }
    }

    static doRenewBlizzardRegistration()
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        Session.isSilent = true;
        document.cookie = "pre-auth-path=" + encodeURI(Util.getCurrentPathInContext() + window.location.search + window.location.hash)
            + ";path=" + ROOT_CONTEXT_PATH
            + ";max-age=300"
            + ";secure;SameSite=Lax";
        return BootstrapUtil.showGenericModal(
            "BattleNet authorization...",
            "Fetching your BattleNet identity and permissions. It usually takes ~5 seconds for BattleNet to respond, please standby.",
            true)
            .then(e=>{window.location.href=ROOT_CONTEXT_PATH + "oauth2/authorization/" + Util.getCookie("oauth-reg"); return "reauth"});
    }

    static updateMyInfoThen()
    {
        if (Session.currentAccount != null)
        {
            CharacterUtil.updatePersonalCharactersView();
            CharacterUtil.updateFollowingCharactersView();
            CharacterUtil.updateAllCharacterReports();
            for(const e of document.querySelectorAll(".login-anonymous")) e.classList.add("d-none");
            for(const e of document.querySelectorAll(".login-user")) e.classList.remove("d-none");
        }
        else
        {
            for(const e of document.querySelectorAll(".login-anonymous")) e.classList.remove("d-none");
            for(const e of document.querySelectorAll(".login-user")) e.classList.add("d-none");
        }
    }

    static updateReportsNotifications()
    {
        if(!Session.currentRoles || !Session.currentRoles.find(r=>r == "MODERATOR")) return;

        const reports = Model.DATA.get(VIEW.CHARACTER_REPORTS).get("reports");
        if(!reports || reports.length == 0) return;

        let unreviewedReports = 0;
        reports.flatMap(r=>r.evidence).forEach(e=>{
            if(!e.votes || !e.votes.find(v=>v.vote.voterAccountId == Session.currentAccount.id)) unreviewedReports++;
        })
        const alerts = [
            document.querySelector("#all-character-reports-tab .tab-alert"),
            document.querySelector("#personal-tab .tab-alert")
        ];
        for(const alert of alerts) {
        alert.textContent = unreviewedReports;
            if(unreviewedReports > 0) {
                alert.classList.remove("d-none");
            } else {
                alert.classList.add("d-none");
            }
        }

    }

    static locationSearch()
    {
        return Session.isHistorical ? Session.currentRestorationSearch : window.location.search;
    }

    static locationHash()
    {
        return Session.isHistorical ? Session.currentRestorationHash : window.location.hash;
    }

    static restoreState()
    {
        for(const elem of document.querySelectorAll(".serializable"))
        {
            const savedState = localStorage.getItem(elem.id);
            if(savedState == null) continue;

            if(elem.getAttribute("type") == "checkbox" || elem.getAttribute("type") == "radio") {
                if(savedState == "true") {
                    elem.setAttribute("checked", "checked");
                } else {
                    elem.removeAttribute("checked");
                }
            } else {
                elem.value = savedState;
            }
        }
    }

    static enhanceSerializable()
    {
        for(const elem of document.querySelectorAll(".serializable"))
        {
            switch(elem.getAttribute("type"))
            {
                case "checkbox":
                    elem.addEventListener("change", e=>localStorage.setItem(elem.id, elem.checked));
                    break;
                case "radio":
                    elem.addEventListener("click", e=>e.target.closest("form")
                        .querySelectorAll(':scope input[name="' + e.target.getAttribute("name") + '"]')
                        .forEach(r=>localStorage.setItem(r.id, r.checked)));
                    break;
                default:
                    elem.addEventListener("change", e=>localStorage.setItem(elem.id, elem.value));
                    break;
            }
        }
    }

    static enhanceCsrfForms()
    {
        document.querySelectorAll(".form-csrf").forEach(f=>{
            f.addEventListener("submit", e=>{
                e.preventDefault();
                Util.updateCsrfForm(e.target);
                e.target.submit();
            });
        });
    }

    static setTheme(theme)
    {
        if(Session.theme == theme) return;

        Session.setDocumentTheme(theme);
        Session.setChartTheme(theme);
        Session.theme = theme;

        document.cookie = "theme=" + theme.name
            + ";path=" + ROOT_CONTEXT_PATH
            + ";max-age=" + "315360000"
            + ";secure;SameSite=Lax";
    }

    static setDocumentTheme(theme)
    {
        const body = document.querySelector("body");
        const themesToRemove = [];
        let themeAdded;
        for(const cTheme of Object.values(THEME)) {
            if(cTheme == theme) {
                body.classList.add("theme-" + cTheme.name);
                themeAdded = Session.themeLinks.get(cTheme);
                themeAdded.onload = e=>themesToRemove.forEach(t=>t.remove());
                const themeOverride = document.querySelector("#bootstrap-theme-override");
                if(themeOverride.previousElementSibling != themeAdded)
                    document.querySelector("head").insertBefore(themeAdded, themeOverride);
            } else {
                body.classList.remove("theme-" + cTheme.name);
                const themeLink = document.querySelector("#bootstrap-theme-" + cTheme.name);
                if(themeLink != null) themesToRemove.push(themeLink);
            }
        }

        const prev = ElementUtil.INPUT_TIMEOUTS.get("bootstrap-theme-cleanup-timeout");
        if(prev != null)  window.clearTimeout(prev);
        ElementUtil.INPUT_TIMEOUTS.set("bootstrap-theme-cleanup-timeout", window.setTimeout(e=>themesToRemove.forEach(t=>t.remove()), 5000));
    }

    static setChartTheme(theme)
    {
        const color = theme == THEME.DARK ? "#242a30" : "rgba(0,0,0,0.1)";
        for(const chart of ChartUtil.CHARTS.values()) {
            chart.config.options.scales.y.grid.color = color;
            chart.config.options.scales.y.grid.zeroLineColor = color;
            chart.update();
        }
    }

    static initThemes()
    {
        const themeCookie = Util.getCookie("theme");
        Session.theme = themeCookie ? EnumUtil.enumOfName(themeCookie, THEME) : THEME.LIGHT;
        Session.themeLinks.set(THEME.LIGHT, document.querySelector("#bootstrap-theme-light"));
        let darkThemeLink = document.querySelector("#bootstrap-theme-dark");
        if(!darkThemeLink)
        {
            darkThemeLink = document.createElement("link");
            darkThemeLink.id = "bootstrap-theme-dark";
            darkThemeLink.setAttribute("rel", "stylesheet");
            darkThemeLink.setAttribute("href", RESOURCE_PATH + "bootstrap-dark.min.css");
        }
        Session.themeLinks.set(THEME.DARK, darkThemeLink);
    }

    static enhanceThemeInputs()
    {
        const handler = e=>window.setTimeout(Session.refreshTheme, 1);
        document.querySelector("#theme-device").addEventListener("click", handler);
        document.querySelector("#theme-light").addEventListener("click", handler);
        document.querySelector("#theme-dark").addEventListener("click", handler);
    }

    static refreshTheme()
    {
        window.matchMedia('(prefers-color-scheme: dark)').removeEventListener('change', Session.deviceThemeCallback);
        if(localStorage.getItem("theme-light") == "true") {
            Session.setTheme(THEME.LIGHT);
        } else if(localStorage.getItem("theme-dark") == "true") {
            Session.setTheme(THEME.DARK);
        } else {
            window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', Session.deviceThemeCallback);
            Session.deviceThemeCallback();
        }
    }

    static getStyleOverride()
    {
        let style = document.querySelector("#personal-style-override");
        if(!style) {
            style = document.createElement("style");
            style.id = "personal-style-override";
            document.head.appendChild(style);
        }
        return style;
    }

    static updateCheaterVisibility()
    {
        const visible = localStorage.getItem("cheaters-visible") || "false";
        const sheet = Session.getStyleOverride().sheet;
        for(let i = 0; i < sheet.cssRules.length; i++) if(sheet.cssRules[i].cssText.startsWith("#cheater-visibility")) sheet.deleteRule(i);
        if(visible != "true") sheet.insertRule("#cheater-visibility, #ladder .team-cheater {display: none !important;}", 0);
    }

    static enhanceCheaterVisibilityInput()
    {
        document.querySelector("#cheaters-visible").addEventListener("click", e=>window.setTimeout(Session.updateCheaterVisibility, 1));
    }

    static updateStyleOverride()
    {
        Session.updateCheaterVisibility();
    }

    static isAuthenticated()
    {
        return AUTHENTICATED == "anonymousUser" || AUTHENTICATED == "" || AUTHENTICATED == null ? false : true;
    }

}

Session.isSilent = false;
Session.currentRequests = 0;
Session.currentSeasons = null;
Session.currentSeasonsMap = null;
Session.currentSeason = null;
Session.currentTeamFormat = null;
Session.currentTeamType = null;
Session.currentPersonalSeason = -1;
Session.currentPersonalTeamFormat = null;
Session.currentPersonalTeamType = null;
Session.currentAccount = null;
Session.currentFollowing = null;
Session.currentRoles = null;
Session.currentSearchParams = null;
Session.isHistorical = false;
Session.currentStateRestoration = Promise.resolve();
Session.currentRestorationSearch = null;
Session.currentRestorationHash = null;
Session.lastNonModalParams = "?#stats";
Session.lastNonModalTitle = "Stats";
Session.titleAndUrlHistory = [["Stats", "?#stats"]];
Session.theme = null;
Session.themeLinks = new Map();
Session.deviceThemeCallback=function(e){Session.setTheme(window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
    ? THEME.DARK : THEME.LIGHT)};
Session.sessionStartTimestamp = null;
Session.INVALID_API_VERSION_CODE = 112233;

Session.sectionParams = new Map();

class PersonalUtil
{
    static getMyAccount()
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        const request = ROOT_CONTEXT_PATH + "api/my/common";
        return Session.beforeRequest()
            .then(e=>fetch(request))
            .then(Session.verifyJsonResponse)
            .then(json => new Promise((res, rej)=>{
                Model.DATA.get(VIEW.PERSONAL_CHARACTERS).set(VIEW_DATA.SEARCH, json.characters);
                Model.DATA.get(VIEW.FOLLOWING_CHARACTERS).set(VIEW_DATA.SEARCH, json.followingCharacters);
                Session.currentFollowing = json.accountFollowings;
                Session.currentRoles = json.roles;
                PersonalUtil.updateMyAccount(json); Util.setGeneratingStatus(STATUS.SUCCESS); res()}))
            .catch(error => Session.onPersonalException(error));
    }

    static updateMyAccount(data)
    {
        Session.currentAccount = data.account;
        const btagElem = document.querySelector("#login-battletag");
        if(btagElem) btagElem.textContent = Session.currentAccount.battleTag;
        const myTab = document.querySelector("#personal-tab");
        if(myTab) myTab.querySelector(":scope .tab-name").textContent = Session.currentAccount.battleTag;

        const rolesElem = document.querySelector("#login-roles");
        if(rolesElem) {
            rolesElem.textContent = data.roles.sort((a, b)=>a.localeCompare(b)).join(", ");
            if(data.roles.includes("SERVER_WATCHER")) {
                const adminLink = document.createElement("a");
                adminLink.setAttribute("href", ROOT_CONTEXT_PATH + "sba/");
                adminLink.setAttribute("id", "server-info-panel-link");
                adminLink.setAttribute("target", "_blank");
                adminLink.textContent = "Server info panel";
                document.querySelector("#account-additional-info").appendChild(adminLink);
            }
        }
        PersonalUtil.updateAccountConnections(data);
    }

    static updateAccountConnections(data)
    {
        const table = document.querySelector("#account-linked-accounts-table");
        if(!table) return;

        table.querySelectorAll(".dynamic").forEach(ElementUtil.removeChildren);
        const section = document.querySelector("#account-linked-accounts");
        if(!data.characters) {
            section.querySelectorAll(":scope .eligible").forEach(e=>e.classList.add("d-none"));
            section.querySelectorAll(":scope .ineligible").forEach(e=>e.classList.remove("d-none"));
        } else {
            section.querySelectorAll(":scope .eligible").forEach(e=>e.classList.remove("d-none"));
            section.querySelectorAll(":scope .ineligible").forEach(e=>e.classList.add("d-none"));
            PersonalUtil.updateDiscordConnection(table, data);
        }
    }

    static updateDiscordConnection(table, data)
    {
        const tr = table.querySelector(":scope #account-connection-discord");
        if(!data.discordUser) {
            tr.querySelector(":scope .account-connection-action").appendChild(ElementUtil.createElement(
                "a",
                null,
                "btn btn-outline-success",
                "Link",
                [["href", ROOT_CONTEXT_PATH + "oauth2/authorization/discord-lg"]]
            ));
        } else {
            tr.querySelector(":scope .account-connection-name").textContent =
                data.discordUser.user.name + "#" + data.discordUser.user.discriminator;

            const action = ElementUtil.createElement("a", null, "btn btn-outline-danger", "Unlink", [["href", "#"]]);
            action.addEventListener("click", PersonalUtil.unlinkDiscordAccount);
            tr.querySelector(":scope .account-connection-action").appendChild(action);

            const publicCtl = ElementUtil.createElement("input", null, "", null, [["type", "checkbox"]]);
            ElementUtil.changeInputValue(publicCtl, data.discordUser.meta.public);
            publicCtl.addEventListener("click", PersonalUtil.updateDiscordAccountVisibility);
            tr.querySelector(":scope .account-connection-public").appendChild(publicCtl);
        }
    }

    static unlinkDiscordAccount()
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return Session.beforeRequest()
            .then(n=>fetch(ROOT_CONTEXT_PATH + "api/my/discord/unlink", Util.addCsrfHeader({method: "POST"})))
            .then(Session.verifyResponse)
            .then(Session.getMyInfo)
            .then(Util.successStatusPromise)
            .catch(error => Session.onPersonalException(error));
    }

    static updateDiscordAccountVisibility(evt)
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return Session.beforeRequest()
            .then(n=>fetch(ROOT_CONTEXT_PATH + "api/my/discord/public/" + evt.target.checked, Util.addCsrfHeader({method: "POST"})))
            .then(Session.verifyResponse)
            .then(Session.getMyInfo)
            .then(Util.successStatusPromise)
            .catch(error => Session.onPersonalException(error));
    }

}
