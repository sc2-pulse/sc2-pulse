// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

class FollowUtil
{

    static follow()
    {
        Util.setGeneratingStatus("begin");
        const profile = document.querySelector("#player-info");
        const id = profile.getAttribute("data-account-id");
        return fetch("api/my/following/" + id, {method: "POST"})
            .then
            (
                resp =>
                {
                    if (!resp.ok) throw new Error(resp.statusText);
                    document.querySelector("#follow-button").classList.add("d-none");
                    document.querySelector("#unfollow-button").classList.remove("d-none");
                    return FollowUtil.getMyFollowing();
                }
            )
            .then(o => new Promise((res, rej)=>{Util.setGeneratingStatus("success"); res();}))
            .catch(error => Util.setGeneratingStatus("error", error.message));
    }

    static unfollow()
    {
        Util.setGeneratingStatus("begin");
        const profile = document.querySelector("#player-info");
        const id = profile.getAttribute("data-account-id");
        return fetch("api/my/following/" + id, {method: "DELETE"})
            .then
            (
                resp =>
                {
                    if (!resp.ok) throw new Error(resp.statusText);
                    document.querySelector("#follow-button").classList.remove("d-none");
                    document.querySelector("#unfollow-button").classList.add("d-none");
                    return FollowUtil.getMyFollowing();
                }
            )
            .then(o => new Promise((res, rej)=>{Util.setGeneratingStatus("success"); res();}))
            .catch(error => Util.setGeneratingStatus("error", error.message));
    }

    static getMyFollowing()
    {
        Util.setGeneratingStatus("begin");
        return fetch("api/my/following")
            .then(resp => {if (!resp.ok) throw new Error(resp.status + " " + resp.statusText); return resp.json();})
            .then(json => new Promise((res, rej)=>{Session.currentFollowing = json; Util.setGeneratingStatus("success"); res();}))
            .catch(error => Util.setGeneratingStatus("error", error.message));
    }

    static enhanceFollowButtons()
    {
        document.querySelector("#follow-button").addEventListener("click", FollowUtil.follow);
        document.querySelector("#unfollow-button").addEventListener("click", FollowUtil.unfollow);
    }

}
