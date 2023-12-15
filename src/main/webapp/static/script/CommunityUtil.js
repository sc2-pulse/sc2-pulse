// Copyright (C) 2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

class CommunityUtil
{

    static enhance()
    {
        CommunityUtil.enhanceStreams();
    }

    static enhanceStreams()
    {
        const streamUpdater = new IntervalExecutor(
            ()=>{
                ElementUtil.setLoadingIndicator(document.querySelector("#search-stream"), LOADING_STATUS.NONE);
                return Util.load(document.querySelector("#search-stream"), ()=>CommunityUtil.updateStreams());
            },
            ()=>window.location.hash == "#search-stream",
            60000,
            3
        );
        ElementUtil.DOCUMENT_VISIBILITY_TASKS.set("#search-stream", (visible)=>{
            if(!visible) {
                streamUpdater.stop();
            } else {
                streamUpdater.executeAndReschedule();
            }
        });
        ElementUtil.ELEMENT_TASKS.set("search-stream-tab", streamUpdater.executeAndReschedule.bind(streamUpdater));

        const sortCtl = document.querySelector("#stream-sort-by");
        if(sortCtl) sortCtl.addEventListener("change", e=>window.setTimeout(()=>{
            CommunityUtil.updateStreamSorting();
            CommunityUtil.updateStreamView();
        }, 1));

        CommunityUtil.enhanceStreamSearchLinks();
    }

    static enhanceFeaturedStreams()
    {
        const featuredStreamUpdater = new IntervalExecutor(
            CommunityUtil.updateFeaturedStreams,
            ()=>!document.hidden,
            60000,
            3
        );
        featuredStreamUpdater.executeAndReschedule();
        document.addEventListener("visibilitychange", () => {
            if(document.hidden){
                featuredStreamUpdater.stop();
            } else {
                featuredStreamUpdater.executeAndReschedule();
            }
        });
    }

    static enhanceStreamSearchLinks()
    {
        document.querySelectorAll(".link-stream-search")
            .forEach(link=>link.addEventListener("click", CommunityUtil.onStreamSearchLinkClick));
    }

    static onStreamSearchLinkClick(evt)
    {
        const search = document.querySelector("#search-stream-tab");
        if(search == null) return;

        evt.preventDefault();
        BootstrapUtil.showTab("search-all-tab")
            .then(e=>BootstrapUtil.showTab("search-stream-tab"));
    }

    static updateFeaturedStreams()
    {
        return CommunityUtil.getFeaturedStreams()
            .then(CommunityUtil.updateFeaturedStreamView);
    }

    static getStreams()
    {
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/revealed/stream`))
            .then(resp=>Session.verifyJsonResponse(resp, [200, 404, 502]))
    }

    static getFeaturedStreams()
    {
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/revealed/stream/featured`))
            .then(resp=>Session.verifyJsonResponse(resp, [200, 404, 502]))
    }

    static updateStreamModel()
    {
        return CommunityUtil.getStreams()
            .then(streams=>{
                Model.DATA.get(VIEW.STREAM_SEARCH).set(VIEW_DATA.SEARCH, streams);
                CommunityUtil.updateStreamSorting();
                return {data: streams, status: streams.errors.length == 0 ? LOADING_STATUS.COMPLETE : LOADING_STATUS.ERROR};
            });
    }

    static updateStreamSorting()
    {
        const data = Model.DATA.get(VIEW.STREAM_SEARCH).get(VIEW_DATA.SEARCH);
        if(!data) return;

        if(localStorage.getItem("stream-sort-by") === "mmr") {
            data.streams.sort((a, b)=>(b.team != null ? b.team.rating : -Infinity)
                - (a.team != null ? a.team.rating : -Infinity));
        } else {
            data.streams.sort((a, b)=>b.stream.viewerCount - a.stream.viewerCount);
        }
    }

    static updateStreamView()
    {
        const data = Model.DATA.get(VIEW.STREAM_SEARCH).get(VIEW_DATA.SEARCH);
        CommunityUtil.updateStreamContainer(data, document.querySelector("#search-stream .streams"));
        return {data: data, status: data.errors.length == 0 ? LOADING_STATUS.COMPLETE : LOADING_STATUS.ERROR};
    }
    
    static updateStreams()
    {
        return CommunityUtil.updateStreamModel()
            .then(CommunityUtil.updateStreamView);
    }

    static updateFeaturedStreamView(streams)
    {
        document.querySelectorAll(".streams-featured")
            .forEach(container=>CommunityUtil.updateStreamContainer(streams, container, true));
    }

    static updateStreamContainer(streams, container, featured = false, clear = true)
    {
        if(clear) ElementUtil.removeChildren(container);

        if(streams.streams)
            for(const stream of streams.streams)
                container.appendChild(CommunityUtil.renderStream(stream, featured));
    }

    static renderStream(stream, featured = false)
    {
        const container = ElementUtil.createElement
        (
            "article",
            "stream-" + stream.stream.service + "-" + stream.stream.id,
            "stream"
        );

        const link = ElementUtil.createElement
        (
            "a",
            null,
            "unstyled" + (featured && stream.featured != null ? " analytics-featured-" + stream.featured.toLowerCase() : ""),
            null,
            [["href", stream.stream.url], ["target", "_blank"]]
        );
        link.appendChild(CommunityUtil.renderStreamThumbnail(stream));
        link.appendChild(CommunityUtil.renderStreamBody(stream, featured));
        container.appendChild(link);
        return container;
    }

    static renderStreamBody(stream, featured = false)
    {
        const body = ElementUtil.createElement("div", null, "body d-flex");
        body.appendChild(CommunityUtil.renderStreamProfile(stream));
        const textBody = ElementUtil.createElement("div", null, "body-text");
        textBody.appendChild(CommunityUtil.renderStreamHeader(stream));
        textBody.appendChild(CommunityUtil.renderStreamMetaData(stream, featured));
        body.appendChild(textBody);
        return body;
    }

    static renderStreamHeader(stream)
    {
        const header = ElementUtil.createElement("header");
        header.appendChild(CommunityUtil.renderStreamTitle(stream));
        header.appendChild(CommunityUtil.renderStreamStreamer(stream));
        header.appendChild(ElementUtil.createElement("div", null, "viewers", stream.stream.viewerCount, [["title", "viewers"]]));
        return header;
    }

    static renderStreamTitle(stream)
    {
        return ElementUtil.createElement
        (
            "h4",
            null,
            "title font-weight-bold text-truncate",
            stream.stream.title,
            [["title", stream.stream.title]]
        );
    }

    static renderStreamThumbnail(stream)
    {
        return CommunityUtil.addRegionalCdnAttributes(stream, ElementUtil.addLoadClassWatcher(ElementUtil.createElement
        (
            "img",
             null,
             "thumbnail",
             null,
             [
                ["loading", "lazy"],
                ["src", stream.stream.thumbnailUrl],
                ["alt", "Stream preview"]
             ]
        )));
    }

    static renderStreamProfile(stream)
    {
        return CommunityUtil.addRegionalCdnAttributes(stream, ElementUtil.addLoadClassWatcher(ElementUtil.createElement
        (
            "img",
             null,
             "profile",
             null,
             [
                ["loading", "lazy"],
                ["src", stream.stream.profileImageUrl],
                ["alt", "Profile img"]
             ]
        )));
    }

    static addRegionalCdnAttributes(stream, img)
    {
        if(stream.stream.service == "BILIBILI") img.setAttribute("referrerpolicy", "same-origin");
        return img;
    }

    static renderStreamStreamer(stream)
    {
        const streamer = stream.proPlayer
            ? CharacterUtil.renderLadderProPlayer(stream.proPlayer)
            : stream.stream.userName;
        return ElementUtil.createElement
        (
            "address",
            null,
            "streamer text-truncate",
            streamer,
            [["title", streamer]]
        );
    }

    static renderStreamMetaData(stream, featured = false)
    {
        const container = ElementUtil.createElement("footer", null, "meta");
        if(stream.team != null) container.appendChild(CommunityUtil.renderStreamTeamLink(stream));

        const tags = ElementUtil.createElement("div", null, "tags");
        tags.appendChild(ElementUtil.createElement(
            "div", null, "icofont-" + stream.stream.service.toLowerCase(), null, [["title", stream.stream.service]]));
        if(stream.stream.language != null) {
            const languageName = Util.LANGUAGE_NAMES.of(stream.stream.language);
            tags.appendChild(ElementUtil.createElement(
                "div",
                null,
                "language language-code text-secondary",
                stream.stream.language,
                [["title", languageName]]
            ));
            tags.appendChild(ElementUtil.createElement(
                "div",
                null,
                "language language-name text-secondary",
                languageName,
                [["title", "language"]]
            ));
        }
        if(featured && stream.featured != null)
            tags.appendChild(ElementUtil.createElement("div", null, "featured text-info", stream.featured));
        container.appendChild(tags);

        return container;
    }

    static renderStreamTeamLink(stream)
    {
        const url = TeamUtil.getTeamMmrHistoryHref([stream.team]);
        const link = ElementUtil.createElement("a", null, "unstyled team-link", null, [["href", url], ["target", "_blank"]]);
        link.appendChild(CommunityUtil.renderStreamTeam(stream));
        return link;
    }

    static renderStreamTeam(stream)
    {
        const team = stream.team;
        if(!team) return null;

        const container = ElementUtil.createElement
        (
            "div",
            null,
            "team d-flex flex-wrap-gap-05 align-items-center container-m-0"
                + (team.lastPlayed == null
                    || Date.now() - Util.parseIsoDateTime(team.lastPlayed).getTime()
                        > CURRENT_FEATURED_TEAM_MAX_DURATION_OFFSET ? " text-secondary" : "")
        );
        const format = ElementUtil.createElement
        (
            "span",
            null,
            "format",
            EnumUtil.enumOfId(team.queueType, TEAM_FORMAT).name
        );
        const region = ElementUtil.createImage("flag/", team.region.toLowerCase(), "table-image-long");
        const league = TeamUtil.createLeagueDiv(team);
        league.classList.add("d-flex", "flex-wrap-gap-05", "align-items-center");
        const member = team.members.find(member=>member.proNickname == stream.proPlayer.proPlayer.nickname);
        const race = TeamUtil.createRacesElem(member);
        const mmr = ElementUtil.createElement("span", null, "mmr", team.rating + " MMR");

        container.appendChild(format);
        container.appendChild(region);
        container.appendChild(league);
        container.appendChild(race);
        container.appendChild(mmr);
        return container;
    }

}
