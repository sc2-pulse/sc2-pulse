// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

public interface OAuth2RateLimiter
{

    String getClientRegistrationId();

    <T> Flux<T> withLimiter(Publisher<T> publisher, boolean localLimiter);

}
