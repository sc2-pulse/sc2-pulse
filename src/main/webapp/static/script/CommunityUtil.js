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
        CommunityUtil.STREAM_UPDATER = streamUpdater;
        ElementUtil.DOCUMENT_VISIBILITY_TASKS.set("#search-stream", (visible)=>{
            if(!visible) {
                streamUpdater.stop();
            } else {
                streamUpdater.executeAndReschedule();
            }
        });
        ElementUtil.ELEMENT_TASKS.set("search-stream-tab", streamUpdater.executeAndReschedule.bind(streamUpdater));

        CommunityUtil.enhanceStreamFilters("#stream-filters",
            CommunityUtil.STREAM_UPDATER.executeAndReschedule.bind(CommunityUtil.STREAM_UPDATER));
    }

    static enhanceFeaturedStreams()
    {
        const featuredStreamUpdater = new IntervalExecutor(
            CommunityUtil.updateFeaturedStreams,
            ()=>!document.hidden,
            60000,
            3
        );
        CommunityUtil.FEATURED_STREAM_UPDATER = featuredStreamUpdater;
        featuredStreamUpdater.executeAndReschedule();
        document.addEventListener("visibilitychange", () => {
            if(document.hidden){
                featuredStreamUpdater.stop();
            } else {
                featuredStreamUpdater.executeAndReschedule();
            }
        });
        CommunityUtil.enhanceStreamFilters("#stream-filters-featured",
            CommunityUtil.FEATURED_STREAM_UPDATER.executeAndReschedule.bind(CommunityUtil.FEATURED_STREAM_UPDATER));
    }

    static enhanceStreamFilters(containerId, func)
    {
        document.querySelectorAll(containerId + " .stream-filter-ctl").forEach(ctl=>ctl.addEventListener(
            ctl.tagName == "SELECT" ? "change" : "click", e=>window.setTimeout(func, 1)));
        document.querySelectorAll(containerId + " .ctl-delay").forEach(ctl=>ctl.addEventListener(
            "input", e=>ElementUtil.clearAndSetInputTimeout(e.target.id, func)));
        document.querySelectorAll(containerId).forEach(form=>form.addEventListener("submit", e=>e.preventDefault()));
    }

    static updateAllStreams()
    {
        if(CommunityUtil.STREAM_UPDATER) CommunityUtil.STREAM_UPDATER.executeAndReschedule();
        if(CommunityUtil.FEATURED_STREAM_UPDATER) CommunityUtil.FEATURED_STREAM_UPDATER.executeAndReschedule();
    }

    static updateFeaturedStreams()
    {
        return CommunityUtil.getStreams(
            CommunityUtil.getStreamServices("-featured"),
            localStorage.getItem("stream-sort-by-featured") || "TOP_PERCENT_REGION",
            localStorage.getItem("stream-identified-only-featured") || "true",
            CommunityUtil.getStreamRaces("-featured"),
            localStorage.getItem("stream-language-preferred-featured") === "false" ? null : Util.getPreferredLanguages(),
            CommunityUtil.getStreamTeamFormats("-featured"),
            localStorage.getItem("stream-rating-min-featured"), localStorage.getItem("stream-rating-max-featured"),
            localStorage.getItem("stream-limit-player-featured") || 5,
            localStorage.getItem("stream-lax-featured") || "false"
        )
            .then(result=>{
                result.streams = CommunityUtil.filterSecondaryPlayerStreams(result.streams);
                CommunityUtil.updateFeaturedStreamView(result);
            });
    }

    static filterSecondaryPlayerStreams(streams)
    {
        const playerIds = new Set();
        return streams.filter(stream=>{
            if(stream.proPlayer == null) return true;
            if(playerIds.has(stream.proPlayer.proPlayer.id)) return false;
            playerIds.add(stream.proPlayer.proPlayer.id);
            return true;
        });
    }

    static createStreamUrlParameters(
        services,
        sorting,
        identifiedOnly,
        races,
        languages,
        teamFormats,
        ratingMin, ratingMax,
        limitPlayer,
        lax
    )
    {
        const params = new URLSearchParams();
        if(services != null) services.forEach(service=>params.append("service", service));
        if(sorting != null) params.append("sort", sorting);
        if(identifiedOnly != null) params.append("identifiedOnly", identifiedOnly);
        if(races != null) races.forEach(race=>params.append("race", race.fullName));
        if(languages != null) languages.forEach(language=>params.append("language", language));
        if(teamFormats != null) teamFormats.forEach(teamFormat=>params.append("teamFormat", teamFormat.formatName));
        if(ratingMin != null) params.append("ratingMin", ratingMin);
        if(ratingMax != null) params.append("ratingMax", ratingMax);
        if(limitPlayer != null) params.append("limitPlayer", limitPlayer);
        if(lax != null) params.append("lax", lax);
        return params;
    }

    static getStreamServices(idSuffix = "")
    {
        return STREAM_SERVICES.filter(service=>localStorage.getItem("stream-service-" + service + idSuffix) !== "false");
    }

    static getStreamRaces(idSuffix = "")
    {
        return Object.values(RACE)
            .filter(race=>localStorage.getItem("stream-race-" + race.fullName + idSuffix) !== "false");
    }

    static getStreamTeamFormats(idSuffix = "")
    {
        const val = localStorage.getItem("stream-team-format" + idSuffix) || "all";
        switch(val) {
            case "all":
                return Object.values(TEAM_FORMAT);
            default:
                return EnumUtil.enumOfName(val, TEAM_FORMAT_TYPE).teamFormats;
        }
    }

    static getStreams
    (
        services,
        sorting,
        identifiedOnly,
        races,
        languages,
        teamFormats,
        ratingMin, ratingMax,
        limitPlayer,
        lax
    )
    {
        const params = CommunityUtil.createStreamUrlParameters(
            services,
            sorting,
            identifiedOnly,
            races,
            languages,
            teamFormats,
            ratingMin, ratingMax,
            limitPlayer,
            lax
        );
        return Session.beforeRequest()
            .then(n=>fetch(`${ROOT_CONTEXT_PATH}api/streams?${params.toString()}`))
            .then(resp=>Session.verifyJsonResponse(resp, [200, 404, 500]))
    }

    static updateStreamModel
    (
        services,
        sorting,
        identifiedOnly,
        races,
        languages,
        teamFormats,
        ratingMin, ratingMax,
        limitPlayer,
        lax
    )
    {
        return CommunityUtil.getStreams(
            services,
            sorting,
            identifiedOnly,
            races,
            languages,
            teamFormats,
            ratingMin, ratingMax,
            limitPlayer,
            lax
        )
            .then(streams=>{
                Model.DATA.get(VIEW.STREAM_SEARCH).set(VIEW_DATA.SEARCH, streams);
                return {data: streams, status: streams.errors.length == 0 ? LOADING_STATUS.COMPLETE : LOADING_STATUS.ERROR};
            });
    }

    static updateStreamView()
    {
        const data = Model.DATA.get(VIEW.STREAM_SEARCH).get(VIEW_DATA.SEARCH);
        CommunityUtil.updateStreamContainer(data, document.querySelector("#search-stream .streams"));
        return {data: data, status: data.errors.length == 0 ? LOADING_STATUS.COMPLETE : LOADING_STATUS.ERROR};
    }
    
    static updateStreams()
    {
        if(!FormUtil.verifyForm(document.querySelector("#stream-filters"), document.querySelector("#search-stream .error-out")))
            return Promise.resolve({data: null, status: LOADING_STATUS.COMPLETE});
        return CommunityUtil.updateStreamModel(
            CommunityUtil.getStreamServices(),
            localStorage.getItem("stream-sort-by") || "RATING",
            localStorage.getItem("stream-identified-only") || "false",
            CommunityUtil.getStreamRaces(),
            localStorage.getItem("stream-language-preferred") === "true" ? Util.getPreferredLanguages() : null,
            CommunityUtil.getStreamTeamFormats(),
            localStorage.getItem("stream-rating-min"), localStorage.getItem("stream-rating-max"),
            localStorage.getItem("stream-limit-player"),
            localStorage.getItem("stream-lax") || "true")
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
        return stream.stream.profileImageUrl
            ? CommunityUtil.addRegionalCdnAttributes(stream, ElementUtil.addLoadClassWatcher(ElementUtil.createElement
                (
                    "img",
                     null,
                     "profile",
                     null,
                     [
                        ["loading", "lazy"],
                        ["src", stream.stream.profileImageUrl ],
                        ["alt", "Profile img"]
                     ]
                )))
            : ElementUtil.createElement("span", null, "profile c-empty", null, [["title", "No profile image"]]);
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
            "div", null, "service icofont-" + stream.stream.service.toLowerCase(), null, [["title", stream.stream.service]]));
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
