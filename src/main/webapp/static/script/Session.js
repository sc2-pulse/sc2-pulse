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
        document.cookie = "pre-auth-path=" + encodeURI(Util.getCurrentPathInContext() + window.location.search)
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

}

Session.isSilent = false;
Session.currentRequests = 0;
Session.currentSeasons = null;
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

Session.sectionParams = new Map();

class PersonalUtil
{
    static getMyAccount()
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        const request = "api/my/account";
        return fetch(request)
            .then(resp => {if (!resp.ok) throw new Error(resp.status + " " + resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{PersonalUtil.updateMyAccount(json); Util.setGeneratingStatus(STATUS.SUCCESS); res()}))
            .catch(error => Session.onPersonalException(error));
    }

    static updateMyAccount(account)
    {
        Session.currentAccount = account;
        document.querySelector("#login-battletag").textContent = Session.currentAccount.battleTag;
    }
}
