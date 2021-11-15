// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class FollowUtil
{

    static follow()
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        const profile = document.querySelector("#player-info");
        const id = profile.getAttribute("data-account-id");
        return Session.beforeRequest()
            .then(n=>fetch(ROOT_CONTEXT_PATH + "api/my/following/" + id, Util.addCsrfHeader({method: "POST"})))
            .then(Session.verifyResponse)
            .then
            (
                resp =>
                {
                    document.querySelector("#follow-button").classList.add("d-none");
                    document.querySelector("#unfollow-button").classList.remove("d-none");
                    return FollowUtil.getMyFollowing();
                }
            )
            .then(o => new Promise((res, rej)=>{Util.setGeneratingStatus(STATUS.SUCCESS); res();}))
            .catch(error => Session.onPersonalException(error));
    }

    static unfollow()
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        const profile = document.querySelector("#player-info");
        const id = profile.getAttribute("data-account-id");
        return Session.beforeRequest()
            .then(n=>fetch(ROOT_CONTEXT_PATH + "api/my/following/" + id, Util.addCsrfHeader({method: "DELETE"})))
            .then(Session.verifyResponse)
            .then
            (
                resp =>
                {
                    document.querySelector("#follow-button").classList.remove("d-none");
                    document.querySelector("#unfollow-button").classList.add("d-none");
                    const updatedFollowing = Model.DATA.get(VIEW.FOLLOWING_CHARACTERS).get(VIEW_DATA.SEARCH)
                        .filter(f=>f.members.account.id != id);
                    Model.DATA.get(VIEW.FOLLOWING_CHARACTERS).set(VIEW_DATA.SEARCH, updatedFollowing);
                    CharacterUtil.updateFollowingCharactersView();
                    return FollowUtil.getMyFollowing();
                }
            )
            .then(o => new Promise((res, rej)=>{Util.setGeneratingStatus(STATUS.SUCCESS); res();}))
            .catch(error => Session.onPersonalException(error));
    }

    static getMyFollowing()
    {
        Util.setGeneratingStatus(STATUS.BEGIN);
        return Session.beforeRequest()
            .then(n=>fetch(ROOT_CONTEXT_PATH + "api/my/following"))
            .then(Session.verifyJsonResponse)
            .then(json => new Promise((res, rej)=>{Session.currentFollowing = json; Util.setGeneratingStatus(STATUS.SUCCESS); res();}))
            .catch(error => Session.onPersonalException(error));
    }

    static enhanceFollowButtons()
    {
        document.querySelector("#follow-button").addEventListener("click", FollowUtil.follow);
        document.querySelector("#unfollow-button").addEventListener("click", FollowUtil.unfollow);
    }

}
