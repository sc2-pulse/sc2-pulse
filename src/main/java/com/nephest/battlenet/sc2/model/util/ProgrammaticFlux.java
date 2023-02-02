// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.util;

import java.util.function.Consumer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

public class ProgrammaticFlux<T>
{

    private final Flux<T> flux;
    private FluxSink<T> sink;

    public ProgrammaticFlux()
    {
        this(null);
    }

    public ProgrammaticFlux(Consumer<T> defaultConsumer)
    {
        this.flux = Flux.create(sink->this.sink = sink);
        if(defaultConsumer != null) flux.subscribe(defaultConsumer);
    }

    public Flux<T> getFlux()
    {
        return flux;
    }

    public FluxSink<T> getSink()
    {
        return sink;
    }

}
