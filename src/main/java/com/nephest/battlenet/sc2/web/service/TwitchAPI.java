// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.UserList;
import com.nephest.battlenet.sc2.twitch.Twitch;
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

    private final TwitchClient client;

    @Autowired
    public TwitchAPI(TwitchClient client)
    {
        this.client = client;
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

}
