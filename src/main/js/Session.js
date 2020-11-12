// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class Session
{

    static getMyInfo()
    {
        if(!document.cookie.includes("oauth-reg")) return Promise.resolve(1);
        return PersonalUtil.getMyAccount()
            .then(e=>Session.updateMyInfoThen());
    }

    static onPersonalException(error)
    {
        if (error.message.startsWith("401") && document.cookie.includes("oauth-reg"))
        {
            Session.renewBlizzardRegistration();
        }
        else
        {
            Util.setGeneratingStatus(STATUS.ERROR, error.message);
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
            Session.doRenewBlizzardRegistration();
        }
    }

    static doRenewBlizzardRegistration()
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        window.location.href=ROOT_CONTEXT_PATH + "oauth2/authorization/" + Util.getCookie("oauth-reg");
    }

    static updateMyInfoThen()
    {
        if (Session.currentAccount != null)
        {
            CharacterUtil.updatePersonalCharacters();
            FollowUtil.getMyFollowing();
            for(e of document.querySelectorAll(".login-anonymous")) e.classList.add("d-none");
            for(e of document.querySelectorAll(".login-user")) e.classList.remove("d-none");
        }
        else
        {
            for(e of document.querySelectorAll(".login-anonymous")) e.classList.remove("d-none");
            for(e of document.querySelectorAll(".login-user")) e.classList.add("d-none");
        }
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
Session.currentStateRestoration = null;
Session.lastNonModalParams = "?t=stats";
Session.lastNonModalTitle = "Stats";
Session.titleAndUrlHistory = [["Stats", "?t=stats"]];

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
