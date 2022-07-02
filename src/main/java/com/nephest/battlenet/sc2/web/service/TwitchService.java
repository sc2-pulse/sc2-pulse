// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.helix.domain.User;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.dao.MatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.MatchParticipantDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.dao.SocialMediaLinkDAO;
import com.nephest.battlenet.sc2.model.twitch.TwitchUser;
import com.nephest.battlenet.sc2.model.twitch.TwitchVideo;
import com.nephest.battlenet.sc2.model.twitch.dao.TwitchUserDAO;
import com.nephest.battlenet.sc2.model.twitch.dao.TwitchVideoDAO;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class TwitchService
{

    private static final Logger LOG = LoggerFactory.getLogger(TwitchService.class);

    public static final int USER_BATCH_SIZE = 100;
    public static final int VIDEO_BATCH_SIZE = 100;
    public static final Duration LINK_VIDEO_OFFSET = Duration.ofDays(1);
    public static final String VIDEO_TYPE_FILTER = "archive";
    public static final String VIDEO_VIEWABLE_FILTER = "public";

    private final TwitchUserDAO twitchUserDAO;
    private final TwitchVideoDAO twitchVideoDAO;
    private final ProPlayerDAO proPlayerDAO;
    private final SocialMediaLinkDAO socialMediaLinkDAO;
    private final MatchDAO matchDAO;
    private final MatchParticipantDAO matchParticipantDAO;
    private final TwitchClient twitchClient;
    private final ExecutorService webExecutorService;

    @Autowired
    public TwitchService
    (
        TwitchUserDAO twitchUserDAO,
        TwitchVideoDAO twitchVideoDAO,
        ProPlayerDAO proPlayerDAO,
        SocialMediaLinkDAO socialMediaLinkDAO,
        MatchDAO matchDAO,
        MatchParticipantDAO matchParticipantDAO,
        TwitchClient twitchClient,
        @Qualifier("webExecutorService") ExecutorService webExecutorService
    )
    {
        this.twitchUserDAO = twitchUserDAO;
        this.twitchVideoDAO = twitchVideoDAO;
        this.proPlayerDAO = proPlayerDAO;
        this.socialMediaLinkDAO = socialMediaLinkDAO;
        this.matchDAO = matchDAO;
        this.matchParticipantDAO = matchParticipantDAO;
        this.twitchClient = twitchClient;
        this.webExecutorService = webExecutorService;
    }

    /**
     * Give a hint to the service that it's a good time to update. It's up to the service to
     * decide what to do and how to do it.
     */
    public void update()
    {
        webExecutorService.submit(this::doUpdate);
    }

    private void doUpdate()
    {
        updateTwitchData();
        proPlayerDAO.linkTwitchUsers();
        OffsetDateTime from = OffsetDateTime.now().minus(LINK_VIDEO_OFFSET);
        matchParticipantDAO.linkTwitchVideo(from);
        matchDAO.updateTwitchVodStats(from);
        LOG.info("Updated twitch data");
    }

    private void updateTwitchData()
    {
        List<String> twitchLogins = socialMediaLinkDAO.findByTypes(SocialMedia.TWITCH).stream()
            .map(l->l.getUrl().substring(l.getUrl().lastIndexOf("/") + 1))
            .collect(Collectors.toList());
        if(twitchLogins.isEmpty()) return;

        int batchIx = 0;
        while(batchIx < twitchLogins.size())
        {
            List<String> batch = twitchLogins.subList(batchIx, Math.min(batchIx + USER_BATCH_SIZE, twitchLogins.size()));
            List<User> userBatch = twitchClient.getHelix().getUsers(null, null, batch)
                .execute().getUsers();
            List<TwitchUser> localUserBatch = userBatch.stream()
                .map(TwitchUser::of)
                .collect(Collectors.toList());
            twitchUserDAO.merge(localUserBatch.toArray(TwitchUser[]::new));
            userBatch.forEach(this::updateVideos);
            batchIx += batch.size();
            LOG.trace("Twitch users progress: {}/{} ", batchIx, twitchLogins.size());
        }
        twitchVideoDAO.removeExpired();
    }

    private void updateVideos(User user)
    {
        TwitchVideo[] videos = twitchClient.getHelix().getVideos
        (
            null,
            null,
            user.getId(),
            null,
            null,
            null,
            null,
            VIDEO_TYPE_FILTER,
            null,
            null,
            VIDEO_BATCH_SIZE
        ).execute().getVideos().stream()
            .filter(v->v.getViewable().equals(VIDEO_VIEWABLE_FILTER))
            .map(v->TwitchVideo.of(user, v))
            .toArray(TwitchVideo[]::new);
        twitchVideoDAO.merge(videos);
        LOG.debug("Saved {} twitch videos for user {}", videos.length, user.getLogin());
    }

}
