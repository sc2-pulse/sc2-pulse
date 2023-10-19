// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.github.twitch4j.helix.domain.Video;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.SocialMediaLinkDAO;
import com.nephest.battlenet.sc2.model.twitch.TwitchUser;
import com.nephest.battlenet.sc2.model.twitch.TwitchVideo;
import com.nephest.battlenet.sc2.model.twitch.dao.TwitchUserDAO;
import com.nephest.battlenet.sc2.model.twitch.dao.TwitchVideoDAO;
import com.nephest.battlenet.sc2.service.EventService;
import com.nephest.battlenet.sc2.twitch.Twitch;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Twitch
public class TwitchService
{

    private static final Logger LOG = LoggerFactory.getLogger(TwitchService.class);

    public static final int USER_BATCH_SIZE = 100;
    public static final int VIDEO_BATCH_SIZE = 50;
    public static final int CONCURRENCY = 1;
    public static final Duration LINK_VIDEO_OFFSET = Duration.ofDays(1);
    public static final Video.Type VIDEO_TYPE_FILTER = Video.Type.ARCHIVE;
    public static final String VIDEO_VIEWABLE_FILTER = "public";

    private final TwitchUserDAO twitchUserDAO;
    private final TwitchVideoDAO twitchVideoDAO;
    private final SocialMediaLinkDAO socialMediaLinkDAO;
    private final MatchDAO matchDAO;
    private final MatchParticipantDAO matchParticipantDAO;
    private final TwitchAPI twitchAPI;

    @Autowired
    public TwitchService
    (
        TwitchUserDAO twitchUserDAO,
        TwitchVideoDAO twitchVideoDAO,
        SocialMediaLinkDAO socialMediaLinkDAO,
        MatchDAO matchDAO,
        MatchParticipantDAO matchParticipantDAO,
        TwitchAPI twitchAPI,
        EventService eventService
    )
    {
        this.twitchUserDAO = twitchUserDAO;
        this.twitchVideoDAO = twitchVideoDAO;
        this.socialMediaLinkDAO = socialMediaLinkDAO;
        this.matchDAO = matchDAO;
        this.matchParticipantDAO = matchParticipantDAO;
        this.twitchAPI = twitchAPI;
        subToEvents(eventService);
    }

    private void subToEvents(EventService eventService)
    {
        eventService.getMatchUpdateEvent()
            .doOnNext(uc->LOG.trace("Received match update event, updating twitch data"))
            .flatMap(uc->updateTwitchData())
            .subscribe();
    }

    private Mono<Void> updateTwitchData()
    {
        return WebServiceUtil
            .blockingCallable(()->socialMediaLinkDAO.findByTypes(EnumSet.of(SocialMedia.TWITCH)))
            .flatMapIterable(Function.identity())
            .map(SocialMediaLink::getServiceUserId)
            .filter(Objects::nonNull)
            .buffer(USER_BATCH_SIZE, HashSet::new)
            .flatMap(twitchAPI::getUsersByIds, CONCURRENCY)
            .map(TwitchUser::of)
            .buffer(USER_BATCH_SIZE, HashSet::new)
            .flatMap(users->WebServiceUtil.blockingCallable(()->twitchUserDAO.merge(users)))
            .doOnNext(users->LOG.trace("Saved {} users", users.size()))
            .flatMapIterable(Function.identity())
            .flatMap
            (
                user->
                twitchAPI.getVideosByUserId
                (
                    Long.toUnsignedString(user.getId()),
                    VIDEO_TYPE_FILTER,
                    VIDEO_BATCH_SIZE
                ),
                CONCURRENCY
            )
            .filter(video->video.getViewable().equals(VIDEO_VIEWABLE_FILTER))
            .map(TwitchVideo::of)
            .buffer(VIDEO_BATCH_SIZE, HashSet::new)
            .flatMap(videos->WebServiceUtil.blockingCallable(()->twitchVideoDAO.merge(videos)))
            .doOnNext(videos->LOG.trace("Saved {} videos", videos.size()))
            .then(WebServiceUtil.blockingCallable(twitchVideoDAO::removeExpired))
            .then(WebServiceUtil.blockingRunnable(this::updateVod))
            .doOnSuccess(v->LOG.info("Updated twitch data"));
    }

    private void updateVod()
    {
        OffsetDateTime from = OffsetDateTime.now().minus(LINK_VIDEO_OFFSET);
        matchParticipantDAO.linkTwitchVideo(from);
        matchDAO.updateTwitchVodStats(from);
    }

}
