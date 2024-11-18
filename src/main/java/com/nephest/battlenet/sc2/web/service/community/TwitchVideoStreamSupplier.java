// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.User;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.twitch.Twitch;
import com.nephest.battlenet.sc2.web.service.TwitchAPI;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

@Twitch
@Service
public class TwitchVideoStreamSupplier
implements VideoStreamSupplier
{

    private static final Logger LOG = LoggerFactory.getLogger(TwitchVideoStreamSupplier.class);

    public static final String SC2_GAME_ID = "490422";
    public static final String PROFILE_IMAGE_URL_DIMENSIONS
        = CommunityService.STREAM_PROFILE_IMAGE_WIDTH
        + "x" + CommunityService.STREAM_PROFILE_IMAGE_HEIGHT;
    private static final String PROFILE_IMAGE_URL_REPLACE_WITH
        = "profile_image-" + PROFILE_IMAGE_URL_DIMENSIONS + ".";
    public static final int LIMIT = 100;

    private final TwitchAPI api;

    @Autowired
    public TwitchVideoStreamSupplier(TwitchAPI api)
    {
        this.api = api;
    }

    @Override
    public SocialMedia getService()
    {
        return SocialMedia.TWITCH;
    }

    @Override
    public Flux<VideoStream> getStreams()
    {
        return api.getStreamsByGameId(SC2_GAME_ID, LIMIT)
            .collectList()
            .flatMap(this::zip)
            .flatMapIterable(TwitchVideoStreamSupplier::from);
    }

    private Mono<Tuple2<List<Stream>, Map<String, User>>> zip(List<Stream> streams)
    {
        Set<String> ids = streams.stream()
            .map(Stream::getUserId)
            .collect(Collectors.toSet());
        return Mono.just(streams)
            .zipWith(api.getUsersByIds(ids)
                .collect(Collectors.toMap(User::getId, Function.identity())))
            .doOnNext(TwitchVideoStreamSupplier::logStreamsWithoutUsers);
    }

    private static void logStreamsWithoutUsers(Tuple2<List<Stream>, Map<String, User>> data)
    {
        if(data.getT1().size() == data.getT2().size()) return;

        data.getT1().stream()
            .filter(s->data.getT2().get(s.getUserId()) == null)
            .forEach(s->LOG.warn("Couldn't find user {} {}", s.getUserId(), s.getUserLogin()));
    }

    private static List<VideoStream> from(Tuple2<List<Stream>, Map<String, User>> data)
    {
        return data.getT1().stream()
            .map(stream->from(stream, data.getT2().get(stream.getUserId())))
            .collect(Collectors.toList());
    }

    public static VideoStream from(Stream stream, @Nullable User user)
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
            user != null
                ? normalizeStreamProfileImageUrlDimensions(user.getProfileImageUrl())
                : null,
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

    public static String normalizeStreamProfileImageUrlDimensions(String profileImageUrl)
    {
        return profileImageUrl.replaceAll
        (
            "profile_image-\\d*x\\d*\\.",
            PROFILE_IMAGE_URL_REPLACE_WITH
        );
    }

}
