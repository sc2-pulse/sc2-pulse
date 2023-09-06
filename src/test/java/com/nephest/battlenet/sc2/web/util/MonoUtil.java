// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

public final class MonoUtil
{

    private MonoUtil(){}


    public static <T> Tuple2<Mono<T>, Mono<Void>> verifiableMono(T val)
    {
        Sinks.One<Void> complete = Sinks.one();
        Mono<T> mono = Mono.create(sink->{
            complete.emitEmpty(Sinks.EmitFailureHandler.FAIL_FAST);
            if(val != null)
            {
                sink.success(val);
            }
            else
            {
                sink.success();
            }
        });
        return Tuples.of(mono, complete.asMono());
    }

    public static <T> Tuple2<Mono<T>, Mono<Void>> verifiableMono()
    {
        return verifiableMono(null);
    }

}
