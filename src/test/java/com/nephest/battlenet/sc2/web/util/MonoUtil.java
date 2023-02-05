// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import java.util.concurrent.atomic.AtomicBoolean;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public final class MonoUtil
{

    private MonoUtil(){}


    public static <T> Tuple2<Mono<T>, AtomicBoolean> verifiableMono(T val)
    {
        AtomicBoolean wasCalled = new AtomicBoolean(false);
        Mono<T> mono = Mono.create(sink->{
            wasCalled.set(true);
            if(val != null)
            {
                sink.success(val);
            }
            else
            {
                sink.success();
            }
        });
        return Tuples.of(mono, wasCalled);
    }

    public static <T> Tuple2<Mono<T>, AtomicBoolean> verifiableMono()
    {
        return verifiableMono(null);
    }

}
