// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.util.LogUtil;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

public class WebServiceUtilTest
{

    @Test
    public void verifyRunnableElasticThread()
    {
        String[] threadName = new String[1];
        WebServiceUtil
            .blockingRunnable(()->threadName[0] = Thread.currentThread().getName())
            .block();
        assertTrue(threadName[0].toLowerCase().contains("elastic"));
    }

    @Test
    public void verifyCallableElasticThread()
    {
        assertTrue
        (
            WebServiceUtil.blockingCallable(()->Thread.currentThread().getName())
                .block()
                .toLowerCase()
                .contains("elastic")
        );
    }

    @Test
    public void whenExceptionIsThrownInSkipErrorsFlux_thenComplete()
    {
        AtomicBoolean run = new AtomicBoolean(false);
        List<Integer> ints = WebServiceUtil.getOnErrorLogAndSkipFlux(Flux.concat(
            Flux.just(1, 2),
            Flux.error(new IllegalStateException("test")),
            Flux.just(4)
        ), t->run.set(true), t-> LogUtil.LogLevel.ERROR)
            .collectList()
            .block();
        Assertions.assertThat(ints)
            .isEqualTo(List.of(1, 2));
        assertTrue(run.get());
    }

}
