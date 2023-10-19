// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.helix.domain.Stream;
import com.github.twitch4j.helix.domain.StreamList;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.UserList;
import com.github.twitch4j.helix.domain.Video;
import com.github.twitch4j.helix.domain.VideoList;
import com.nephest.battlenet.sc2.twitch.Twitch;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import rx.RxReactiveStreams;

@Service
@Twitch
public class TwitchAPI
{

    public static final int USER_BATCH_SIZE = 100;
    public static final int STREAM_BATCH_SIZE = 100;

    private final TwitchClient client;

    @Autowired
    public TwitchAPI(TwitchClient client)
    {
        this.client = client;
    }

    public TwitchClient getClient()
    {
        return client;
    }

    public Flux<User> getUsersByIds(Set<String> ids)
    {
        return Flux.fromIterable(ids)
            .buffer(USER_BATCH_SIZE)
            .flatMap(idBatch->Flux.from(RxReactiveStreams.toPublisher(
                client.getHelix().getUsers(null, idBatch, null).toObservable())))
            .map(UserList::getUsers)
            .flatMapIterable(Function.identity());
    }

    public Flux<User> getUsersByLogins(Set<String> logins)
    {
        return Flux.fromIterable(logins)
            .buffer(USER_BATCH_SIZE)
            .flatMap(loginBatch->Flux.from(RxReactiveStreams.toPublisher(
                client.getHelix().getUsers(null, null, loginBatch).toObservable())))
            .map(UserList::getUsers)
            .flatMapIterable(Function.identity());
    }

    public Flux<Stream> getStreamsByGameId(String gameId, int limit)
    {
        if(limit < 1) return Flux.empty();
        if(limit > STREAM_BATCH_SIZE)
            return Flux.error(new UnsupportedOperationException("Pagination is not supported"));

        return Flux.from(RxReactiveStreams.toPublisher(client.getHelix().getStreams(
            null,
            null,
            null,
            limit,
            List.of(gameId),
            null,
            null,
            null
        ).toObservable()))
            .map(StreamList::getStreams)
            .flatMapIterable(Function.identity());
    }

    public Flux<Video> getVideosByUserId
    (
        String userId,
        Video.Type typeFilter,
        int size
    )
    {
        return Flux.from(RxReactiveStreams.toPublisher(client.getHelix().getVideos
        (
            null,
            null,
            userId,
            null,
            null,
            null,
            null,
            typeFilter,
            size,
            null,
            null
        ).toObservable()))
            .map(VideoList::getVideos)
            .flatMapIterable(Function.identity());
    }

}
