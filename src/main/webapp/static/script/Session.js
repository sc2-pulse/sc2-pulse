// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class Session
{

    static getMyInfo()
    {
        if(!document.cookie.includes("oauth-reg")) return Promise.resolve(1);
        return PersonalUtil.getMyAccount()
            .then(e=>{Session.updateMyInfoThen(); return e});
    }

    static onPersonalException(error)
    {
        if (error.message.startsWith("401") && document.cookie.includes("oauth-reg"))
        {
            return Session.renewBlizzardRegistration();
        }
        else
        {
            Util.setGeneratingStatus(STATUS.ERROR, error.message, error);
        }
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
        return BootstrapUtil.hideActiveModal().then(e=>BootstrapUtil.showGenericModal(
            "BattleNet authorization...",
            "Fetching your BattleNet identity and permissions. It usually takes ~5 seconds for BattleNet to respond, please standby.",
            true))
            .then(e=>{window.location.href=ROOT_CONTEXT_PATH + "oauth2/authorization/" + Util.getCookie("oauth-reg"); return "reauth"});
    }

    static updateMyInfoThen()
    {
        if (Session.currentAccount != null)
        {
            CharacterUtil.updatePersonalCharacters();
            FollowUtil.getMyFollowing();
            for(const e of document.querySelectorAll(".login-anonymous")) e.classList.add("d-none");
            for(const e of document.querySelectorAll(".login-user")) e.classList.remove("d-none");
        }
        else
        {
            for(const e of document.querySelectorAll(".login-anonymous")) e.classList.remove("d-none");
            for(const e of document.querySelectorAll(".login-user")) e.classList.add("d-none");
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
                elem.setAttribute("value", savedState);
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
        Session.theme = THEME.LIGHT;
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

Session.sectionParams = new Map();

class PersonalUtil
{
    static getMyAccount()
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        const request = ROOT_CONTEXT_PATH + "api/my/account";
        return fetch(request)
            .then(resp => {if (!resp.ok) throw new Error(resp.status + " " + resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{PersonalUtil.updateMyAccount(json); Util.setGeneratingStatus(STATUS.SUCCESS); res()}))
            .catch(error => Session.onPersonalException(error));
    }

    static updateMyAccount(account)
    {
        Session.currentAccount = account;
        const btagElem = document.querySelector("#login-battletag");
        if(!btagElem) return;

        btagElem.textContent = Session.currentAccount.battleTag;
    }
}
