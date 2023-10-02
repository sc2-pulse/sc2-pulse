// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import com.github.twitch4j.helix.domain.Stream;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.twitch.Twitch;
import com.nephest.battlenet.sc2.web.service.TwitchAPI;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Twitch
@Service
public class TwitchVideoStreamSupplier
implements VideoStreamSupplier
{

    public static final String SC2_GAME_ID = "490422";
    public static final int LIMIT = 100;

    private final TwitchAPI api;

    @Autowired
    public TwitchVideoStreamSupplier(TwitchAPI api)
    {
        this.api = api;
    }

    @Override
    public Flux<VideoStream> getStreams()
    {
        return api.getStreamsByGameId(SC2_GAME_ID, LIMIT)
            .map(TwitchVideoStreamSupplier::from);
    }

    public static VideoStream from(Stream stream)
    {
        return new VideoStreamImpl
        (
            SocialMedia.TWITCH,
            stream.getId(),
            stream.getUserId(),
            stream.getUserName(),
            stream.getTitle(),
            Locale.forLanguageTag(stream.getLanguage()),
            generateStreamUrl(stream),
            stream.getThumbnailUrl
            (
                CommunityService.STREAM_THUMBNAIL_TARGET_WIDTH,
                CommunityService.STREAM_THUMBNAIL_TARGET_HEIGHT
            ),
            stream.getViewerCount()
        );
    }

    public static String generateStreamUrl(Stream stream)
    {
        return SocialMedia.TWITCH.getBaseUserUrl() + "/" + stream.getUserLogin();
    }

}
