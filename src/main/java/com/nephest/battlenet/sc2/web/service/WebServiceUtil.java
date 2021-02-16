// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.function.Function;

@Service
public class WebServiceUtil
{

    private WebServiceUtil(){}

    public static final int RETRY_COUNT = 3;
    public static final Duration CONNECT_TIMEOUT = Duration.ofMillis(15000);
    public static final Duration IO_TIMEOUT = Duration.ofMillis(40000);
    public static final Duration RETRY_DURATION_MIN = Duration.ofMillis(300);
    public static final Duration RETRY_DURATION_MAX = Duration.ofMillis(1000);
    public static final Retry RETRY = Retry
        .backoff(RETRY_COUNT, RETRY_DURATION_MIN).maxBackoff(RETRY_DURATION_MAX)
        .filter(t->!(ExceptionUtils.getRootCause(t) instanceof NoRetryException))
        .transientErrors(true);
    public static final Retry RETRY_SKIP_NOT_FOUND = Retry
        .backoff(RETRY_COUNT, RETRY_DURATION_MIN).maxBackoff(RETRY_DURATION_MAX)
        .filter(t->!(ExceptionUtils.getRootCause(t) instanceof WebClientResponseException.NotFound)
            && !(ExceptionUtils.getRootCause(t) instanceof NoRetryException))
        .transientErrors(true);

    public static WebClient.Builder getWebClientBuilder(ObjectMapper objectMapper, int inMemorySize)
    {
        TcpClient timeoutClient = TcpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
            .doOnConnected
            (
                c-> c.addHandlerLast(new ReadTimeoutHandler((int) IO_TIMEOUT.toSeconds()))
                    .addHandlerLast(new WriteTimeoutHandler((int) IO_TIMEOUT.toSeconds()))
            );
        HttpClient httpClient = HttpClient.from(timeoutClient)
            .compress(true);
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .exchangeStrategies(ExchangeStrategies.builder().codecs(conf->
            {
                conf.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper));
                conf.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper));
                if(inMemorySize > 0) conf.defaultCodecs().maxInMemorySize(inMemorySize);
            }).build());
    }

    public static <T> Mono<T> getRateDelayedMono
    (Mono<T> mono, Function<? super Throwable,? extends Mono<? extends T>> fallback, int fullDelay)
    {
        long start = System.currentTimeMillis();
        return mono.delayUntil(r->Mono.just(r)
            .delaySubscription(Duration.ofMillis(Math.max(0, fullDelay - (System.currentTimeMillis() - start))))
        )
        .onErrorResume(t->Mono.empty()
            .delaySubscription(Duration.ofMillis(Math.max(0, fullDelay - (System.currentTimeMillis() - start))))
            .then(fallback.apply(t)));
    }

    public static <T> Mono<T> getRateDelayedMono(Mono<T> mono, int fullDelay)
    {
        return getRateDelayedMono(mono, t->Mono.empty(), fullDelay);
    }

}
