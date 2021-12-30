// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.util.LogUtil;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;
import reactor.util.retry.RetrySpec;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Function;

@Service
public class WebServiceUtil
{

    public static final Logger LOG = LoggerFactory.getLogger(WebServiceUtil.class);
    public static final String TRACE_EXCEPTION_LOG_TEMPLATE =
        "{} (x-trace-traceid: {}, x-trace-spanid: {}, x-trace-parentspanid: {})";

    private WebServiceUtil(){}

    public static final int RETRY_COUNT = 2;
    public static final Duration CONNECT_TIMEOUT = Duration.ofMillis(10000);
    public static final Duration IO_TIMEOUT = Duration.ofMillis(30000);
    public static final RetrySpec RETRY = RetrySpec
        .max(RETRY_COUNT)
        .filter(t->!(ExceptionUtils.getRootCause(t) instanceof NoRetryException))
        .transientErrors(true);
    public static final RetrySpec RETRY_SKIP_NOT_FOUND = RetrySpec
        .max(RETRY_COUNT)
        .filter(t->!(ExceptionUtils.getRootCause(t) instanceof WebClientResponseException.NotFound)
            && !(ExceptionUtils.getRootCause(t) instanceof NoRetryException))
        .transientErrors(true);
    public static final RetrySpec RETRY_NEVER = RetrySpec.max(0);
    public static final RetrySpec RETRY_ONCE = RetrySpec.max(1);
    public static final ConnectionProvider CONNECTION_PROVIDER = ConnectionProvider.builder("sc2-connection-provider")
        .maxConnections(800)
        .maxIdleTime(Duration.ofSeconds(30))
        .maxLifeTime(Duration.ofMinutes(10))
        .evictInBackground(Duration.ofSeconds(30))
        .lifo()
        .build();
    public static final LoopResources LOOP_RESOURCES =
        LoopResources.create("sc2-http", Math.max(Runtime.getRuntime().availableProcessors(), 7), true);
    public static Function<? super Throwable,? extends Mono<?>> LOG_ROOT_MESSAGE_AND_RETURN_EMPTY = t->{
        LOG.error(ExceptionUtils.getRootCauseMessage(t));
        return Mono.empty();
    };

    public static ClientHttpConnector getClientHttpConnector()
    {
        return new ReactorClientHttpConnector(getHttpClient(CONNECT_TIMEOUT, IO_TIMEOUT));
    }

    public static WebClient.Builder getWebClientBuilder
    (ObjectMapper objectMapper, int inMemorySize, MediaType... codecTypes)
    {
        MediaType[] finalTypes = codecTypes.length == 0 ? new MediaType[]{MediaType.APPLICATION_JSON} : codecTypes;
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(getHttpClient(CONNECT_TIMEOUT, IO_TIMEOUT)))
            .exchangeStrategies(ExchangeStrategies.builder().codecs(conf->
            {
                conf.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper, finalTypes));
                conf.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper, finalTypes));
                if(inMemorySize > 0) conf.defaultCodecs().maxInMemorySize(inMemorySize);
            }).build());
    }

    public static HttpClient getHttpClient(Duration connectTimeout, Duration ioTimeout)
    {
        return HttpClient.create(CONNECTION_PROVIDER)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
            .doOnConnected
                (
                    c-> c.addHandlerLast(new ReadTimeoutHandler((int) ioTimeout.toSeconds()))
                        .addHandlerLast(new WriteTimeoutHandler((int) ioTimeout.toSeconds()))
                )
            .runOn(LOOP_RESOURCES)
            .compress(true);
    }

    public static WebClient.Builder getWebClientBuilder(ObjectMapper objectMapper)
    {
        return getWebClientBuilder(objectMapper, -1);
    }

    public static <T> Mono<T> decorateMono
    (
        Mono<T> mono,
        Function<? super Throwable,? extends Mono<? extends T>> fallback,
        Consumer<? super Throwable> onError
    )
    {
        if(onError != null) mono = mono.doOnError(onError);
        return mono.onErrorResume(fallback);
    }

    public static <T> Mono<T> getOnErrorLogAndSkipMono
    (Mono<T> mono, Consumer<? super Throwable> onError, Function<Throwable, LogUtil.LogLevel> logLevelFunction)
    {
        return decorateMono(
            mono,
            t->{
                if(t instanceof TemplatedException) {
                    TemplatedException te = (TemplatedException) t;
                    LogUtil.log(LOG, logLevelFunction.apply(t), te.getLogTemplate(), te.getLogArgs());
                }
                else if(t instanceof WebClientResponseException)
                {
                    logWebClientException((WebClientResponseException) t, logLevelFunction);
                }
                else if(ExceptionUtils.getRootCause(t) instanceof WebClientResponseException)
                {
                    logWebClientException((WebClientResponseException) ExceptionUtils.getRootCause(t), logLevelFunction);
                }
                else
                {
                    LogUtil.log(LOG, logLevelFunction.apply(t), ExceptionUtils.getRootCauseMessage(t));
                }
                return Mono.empty();
            },
            onError);
    }

    public static <T> Mono<T> getOnErrorLogAndSkipLogLevelMono
    (Mono<T> mono, Function<Throwable, LogUtil.LogLevel> logLevelFunction)
    {
        return getOnErrorLogAndSkipMono(mono, null, logLevelFunction);
    }

    public static <T> Mono<T> getOnErrorLogAndSkipMono(Mono<T> mono, Consumer<? super Throwable> onError)
    {
        return getOnErrorLogAndSkipMono(mono, onError, (t)->LogUtil.LogLevel.ERROR);
    }

    public static <T> Mono<T> getOnErrorLogAndSkipMono(Mono<T> mono)
    {
        return getOnErrorLogAndSkipMono(mono, null);
    }

    public static void logWebClientException
    (WebClientResponseException wcre, Function<Throwable, LogUtil.LogLevel> logLevelFunction)
    {
        if
        (
            wcre.getHeaders().containsKey("x-trace-traceid")
            && wcre.getHeaders().containsKey("x-trace-spanid")
            && wcre.getHeaders().containsKey("x-trace-parentspanid")
        )
        {
            LogUtil.log(LOG, logLevelFunction.apply(wcre), TRACE_EXCEPTION_LOG_TEMPLATE,
                ExceptionUtils.getRootCauseMessage(wcre),
                wcre.getHeaders().get("x-trace-traceid").get(0),
                wcre.getHeaders().get("x-trace-spanid").get(0),
                wcre.getHeaders().get("x-trace-parentspanid").get(0));
        } else
        {
            LogUtil.log(LOG, logLevelFunction.apply(wcre), ExceptionUtils.getRootCauseMessage(wcre));
        }
    }

}
