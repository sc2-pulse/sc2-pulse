// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.CollectionVar;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.util.LogUtil;
import com.nephest.battlenet.sc2.web.util.RateLimitData;
import io.netty.channel.ChannelOption;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.TimeoutException;
import io.netty.handler.timeout.WriteTimeoutHandler;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.net.ssl.SSLException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.RetrySpec;

@Service
public class WebServiceUtil
{

    public static final Logger LOG = LoggerFactory.getLogger(WebServiceUtil.class);
    public static final String TRACE_EXCEPTION_LOG_TEMPLATE =
        "{} (x-trace-traceid: {}, x-trace-spanid: {}, x-trace-parentspanid: {})";
    public static final Duration DEFAULT_API_CACHE_DURATION = Duration.ofDays(7);
    public static final String DEFAULT_CACHE_HEADER
        = "private, max-age=604800, stale-while-revalidate=604800"; //7days fresh, 7days revalidate
    public static final String RATE_LIMIT_LIMIT_HEADER_NAME = "X-RateLimit-Limit";
    public static final String RATE_LIMIT_REMAINING_HEADER_NAME = "X-RateLimit-Remaining";
    public static final String RATE_LIMIT_RESET_HEADER_NAME = "X-RateLimit-Reset";
    public static final long RATE_LIMIT_RESET_TIMESTAMP_THRESHOLD = System.currentTimeMillis();
    public static final String API_PATH_PREFIX = "/api/";

    private WebServiceUtil(){}

    public static final int RETRY_COUNT = 1;
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
    public static final ConnectionProvider CONNECTION_PROVIDER =
        standardConfig("sc2-connection-provider")
            .maxConnections(800)
            .build();
    public static final ConnectionProvider CONNECTION_PROVIDER_MEDIUM =
        standardConfig("sc2-connection-provider_5")
            .maxConnections(5)
            .pendingAcquireMaxCount(200)
            .build();
    public static final ConnectionProvider CONNECTION_PROVIDER_SMALL =
        standardConfig("sc2-connection-provider_1")
            .maxConnections(1)
            .pendingAcquireMaxCount(200)
            .build();
    public static final SslContext INSECURE_SSL_CONTEXT;
    static
    {
        try
        {
            INSECURE_SSL_CONTEXT = SslContextBuilder
                .forClient()
                .trustManager(InsecureTrustManagerFactory.INSTANCE)
                .build();
        }
        catch (SSLException e)
        {
            throw new RuntimeException(e);
        }
    }
    public static final HttpStatus UPSTREAM_ERROR_STATUS = HttpStatus.INTERNAL_SERVER_ERROR;

    public static final Set<Class<? extends Throwable>> SIMPLE_EXCEPTIONS =
        Set.of(TimeoutException.class);

    public static Function<? super Throwable,? extends Mono<?>> LOG_ROOT_MESSAGE_AND_RETURN_EMPTY = t->{
        LOG.error(ExceptionUtils.getRootCauseMessage(t));
        return Mono.empty();
    };

    public static Set<? extends HttpStatusCode> CONTENT_STATUS_CODES = EnumSet.of(HttpStatus.OK);
    public static Set<? extends HttpStatusCode> EMPTY_STATUS_CODES = EnumSet.of
    (
        HttpStatus.NOT_FOUND
    );

    public static ConnectionProvider.Builder standardConfig(String name)
    {
        return ConnectionProvider.builder(name)
            .maxIdleTime(Duration.ofSeconds(30))
            .maxLifeTime(Duration.ofMinutes(10))
            .evictInBackground(Duration.ofSeconds(30))
            .lifo();
    }

    public static ClientHttpConnector getClientHttpConnector()
    {
        return getClientHttpConnector(CONNECT_TIMEOUT, IO_TIMEOUT);
    }

    public static ClientHttpConnector getClientHttpConnector
    (
        Duration connectTimeout,
        Duration ioTimeout
    )
    {
        return new ReactorClientHttpConnector(getHttpClient(connectTimeout, ioTimeout));
    }

    public static WebClient.Builder getWebClientBuilder
    (
        ConnectionProvider connectionProvider,
        ObjectMapper objectMapper,
        int inMemorySize,
        Function<HttpClient, HttpClient> clientCustomizer,
        MediaType... codecTypes
    )
    {
        MediaType[] finalTypes = codecTypes.length == 0 ? new MediaType[]{MediaType.APPLICATION_JSON} : codecTypes;
        return WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(clientCustomizer.apply(
                getHttpClient(connectionProvider, CONNECT_TIMEOUT, IO_TIMEOUT))))
            .exchangeStrategies(ExchangeStrategies.builder().codecs(conf->
            {
                conf.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(objectMapper, finalTypes));
                conf.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(objectMapper, finalTypes));
                if(inMemorySize > 0) conf.defaultCodecs().maxInMemorySize(inMemorySize);
            }).build());
    }

    public static WebClient.Builder getWebClientBuilder
    (
        ConnectionProvider connectionProvider,
        ObjectMapper objectMapper,
        int inMemorySize,
        MediaType... codecTypes
    )
    {
        return getWebClientBuilder
        (
            connectionProvider,
            objectMapper,
            inMemorySize,
            Function.identity(),
            codecTypes
        );
    }

    public static WebClient.Builder getWebClientBuilder
    (ObjectMapper objectMapper, int inMemorySize, MediaType... codecTypes)
    {
        return getWebClientBuilder
        (
            CONNECTION_PROVIDER,
            objectMapper,
            inMemorySize,
            codecTypes
        );
    }

    public static WebClient.Builder getWebClientBuilder
    (
        ConnectionProvider connectionProvider,
        ObjectMapper objectMapper
    )
    {
        return getWebClientBuilder
        (
            connectionProvider,
            objectMapper,
            -1
        );
    }

    public static HttpClient getHttpClient
    (
        ConnectionProvider connectionProvider,
        Duration connectTimeout,
        Duration ioTimeout
    )
    {
        return HttpClient.create(connectionProvider)
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis())
            .doOnConnected
                (
                    c-> c.addHandlerLast(new ReadTimeoutHandler((int) ioTimeout.toSeconds()))
                        .addHandlerLast(new WriteTimeoutHandler((int) ioTimeout.toSeconds()))
                )
            .compress(true);
    }

    public static HttpClient getHttpClient(Duration connectTimeout, Duration ioTimeout)
    {
        return getHttpClient(CONNECTION_PROVIDER, connectTimeout, ioTimeout);
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
                logException(t, logLevelFunction.apply(t));
                return Mono.empty();
            },
            onError);
    }

    public static <T> Flux<T> getOnErrorLogAndSkipFlux(Flux<T> flux)
    {
        return getOnErrorLogAndSkipFlux(flux, null, t->LogUtil.LogLevel.ERROR);
    }

    public static <T> Flux<T> getOnErrorLogAndSkipFlux
    (
        Flux<T> flux,
        Consumer<? super Throwable> onError,
        Function<Throwable, LogUtil.LogLevel> logLevelFunction
    )
    {
        return flux
            .doOnError(t->{
                logException(t, logLevelFunction.apply(t));
                if(onError != null) onError.accept(t);
            })
            .onErrorComplete();
    }

    private static void logException(Throwable t, LogUtil.LogLevel logLevel)
    {
        if(t instanceof TemplatedException te)
        {
            LogUtil.log(LOG, logLevel, te.getLogTemplate(), te.getLogArgs());
        }
        else if(t instanceof WebClientResponseException)
        {
            logWebClientException((WebClientResponseException) t, logLevel);
        }
        else if(ExceptionUtils.getRootCause(t) instanceof WebClientResponseException)
        {
            logWebClientException((WebClientResponseException) ExceptionUtils.getRootCause(t), logLevel);
        }
        else
        {
            if(shouldLogStackTrace(t))
            {
                LogUtil.log(LOG, logLevel, t);
            }
            else
            {
                LogUtil.log
                (
                    LOG,
                    logLevel,
                    ExceptionUtils.getRootCauseMessage(t)
                );
            }
        }
    }

    /**
     * Checks if supplied {@link java.lang.Throwable} is simple from the logging perspective.
     * Simple throwable is a throwable which has all useful information contained in its message,
     * and there is no point in logging its stack trace.
     *
     * @param throwable in
     * @return true if simple, false otherwise
     */
    public static boolean isSimple(Throwable throwable)
    {
        return SIMPLE_EXCEPTIONS.stream()
            .anyMatch(simple->ExceptionUtils.indexOfType(throwable, simple) != -1);
    }

    private static boolean shouldLogStackTrace(Throwable throwable)
    {
        return !isSimple(throwable);
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

    public static boolean isClientResponseException(Throwable t)
    {
        return ExceptionUtils.indexOfType(t, WebClientResponseException.class) > -1;
    }

    public static boolean isClientResponseNotFound(Throwable t)
    {
        return ExceptionUtils.indexOfType(t, WebClientResponseException.NotFound.class) > -1;
    }

    public static void logWebClientException
    (WebClientResponseException wcre, LogUtil.LogLevel logLevel)
    {
        if
        (
            wcre.getHeaders().containsKey("x-trace-traceid")
            && wcre.getHeaders().containsKey("x-trace-spanid")
            && wcre.getHeaders().containsKey("x-trace-parentspanid")
        )
        {
            LogUtil.log(LOG, logLevel, TRACE_EXCEPTION_LOG_TEMPLATE,
                ExceptionUtils.getRootCauseMessage(wcre),
                wcre.getHeaders().get("x-trace-traceid").get(0),
                wcre.getHeaders().get("x-trace-spanid").get(0),
                wcre.getHeaders().get("x-trace-parentspanid").get(0));
        } else
        {
            LogUtil.log(LOG, logLevel, ExceptionUtils.getRootCauseMessage(wcre));
        }
    }

    public static Mono<Void> blockingRunnable(Runnable runnable)
    {
        return Mono.fromRunnable(runnable)
            .subscribeOn(Schedulers.boundedElastic())
            .then();
    }

    public static <T> Mono<T> blockingCallable(Callable<T> callable)
    {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }

    public static CollectionVar<Set<Region>, Region> loadRegionSetVar(VarDAO varDAO, String key, String error)
    {
        CollectionVar<Set<Region>, Region> setVar = new CollectionVar<>
        (
            varDAO, key,
            r->r == null ? null : String.valueOf(r.getId()),
            s->s == null || s.isEmpty() ? null : Region.from(Integer.parseInt(s)),
            HashSet::new,
            Collectors.toSet(),
            false
        );
        //catch errors to allow autowiring in tests
        try
        {
            setVar.load();
            if(!setVar.getValue().isEmpty()) LOG.warn(error, setVar.getValue());
        }
        catch (Exception ex)
        {
            LOG.error(ex.getMessage(), ex);
        }
        return setVar;
    }

    public static String getFirstHeaderValue(HttpHeaders headers, String name)
    {
        String value = headers.getFirst(name);
        Objects.requireNonNull(value);
        return value;
    }

    /**
     * This method uses {@link #RATE_LIMIT_RESET_TIMESTAMP_THRESHOLD} value to determine if the
     * header value is a timestamp or an offset. If the value is lower than
     * {@link #RATE_LIMIT_RESET_TIMESTAMP_THRESHOLD}, then it is treated as an offset, in other
     * cases the value is parsed as a timestamp.
     *
     * @param headers headers
     * @return parsed {@link java.time.Instant}
     */
    public static Instant parseResetHeader(HttpHeaders headers, String headerName)
    {
        long resetHeader = RateLimitData
            .parseMillis(WebServiceUtil.getFirstHeaderValue(headers, headerName));
        return resetHeader >= RATE_LIMIT_RESET_TIMESTAMP_THRESHOLD
            ? Instant.ofEpochMilli(resetHeader)
            : parseDateHeader(headers).plusMillis(resetHeader);
    }

    public static Instant parseResetHeader(HttpHeaders headers)
    {
        return parseResetHeader(headers, RATE_LIMIT_RESET_HEADER_NAME);
    }

    public static Instant parseDateHeader(HttpHeaders headers)
    {
        long timestamp = headers.getFirstDate("date");
        if(timestamp == -1) throw new IllegalArgumentException("Date header not found");
        return Instant.ofEpochMilli(timestamp);
    }

    public static RateLimitData parseRateLimit
    (
        HttpHeaders headers,
        String limitHeaderName,
        String remainingHeaderName,
        String resetHeaderName
    )
    {
        return new RateLimitData
        (
            Integer.parseInt(WebServiceUtil.getFirstHeaderValue(headers, limitHeaderName)),
            Integer.parseInt(WebServiceUtil.getFirstHeaderValue(headers, remainingHeaderName)),
            WebServiceUtil.parseResetHeader(headers, resetHeaderName)
        );
    }

    public static RateLimitData parseRateLimit(HttpHeaders headers)
    {
        return parseRateLimit
        (
            headers,
            RATE_LIMIT_LIMIT_HEADER_NAME,
            RATE_LIMIT_REMAINING_HEADER_NAME,
            RATE_LIMIT_RESET_HEADER_NAME
        );
    }

    public static <T> Mono<T> errorOnErrorCode(ClientResponse response, Class<T> clazz)
    {
        return response.statusCode().isError()
            ? response.createException().flatMap(Mono::error)
            : Mono.empty();
    }

    public static <T> Mono<T> bodyToMonoErrorOnErrorCode(ClientResponse response, Class<T> clazz)
    {
        return errorOnErrorCode(response, clazz)
            .switchIfEmpty(response.bodyToMono(clazz));
    }

    public static <T> Flux<T> bodyToFluxErrorOnErrorCode(ClientResponse response, Class<T> clazz)
    {
        return errorOnErrorCode(response, clazz)
            .flux()
            .switchIfEmpty(response.bodyToFlux(clazz));
    }

    public static <T> Mono<T> wrapSecurity(Mono<T> mono)
    {
        return mono.contextWrite(ReactiveSecurityContextHolder.withAuthentication(
            SecurityContextHolder.getContext().getAuthentication()));
    }

    public static <T> Flux<T> wrapSecurity(Flux<T> flux)
    {
        return flux.contextWrite(ReactiveSecurityContextHolder.withAuthentication(
            SecurityContextHolder.getContext().getAuthentication()));
    }

    public static boolean isRestricted(HttpStatusCode status)
    {
        return status.value() == 401 || status.value() == 403;
    }

    public static boolean isOauth2ClientMissing(Throwable t)
    {
        return t.getMessage().startsWith("OAuth2AuthorizedClient not found")
            ||
            (
                t instanceof WebClientResponseException
                && WebServiceUtil.isRestricted(((WebClientResponseException) t).getStatusCode())
            );
    }

    public static <T extends Collection<?>> ResponseEntity<Object> notFoundIfEmpty(T collection)
    {
        return collection.isEmpty()
            ? ResponseEntity.notFound().build()
            : ResponseEntity.ok(collection);
    }

    public static <T extends Map<?, ?>> ResponseEntity<Object> notFoundIfEmpty(T map)
    {
        return map.isEmpty()
            ? ResponseEntity.notFound().build()
            : ResponseEntity.ok(map);
    }

    public static <T> ResponseEntity<T> notFoundIfNull(T t)
    {
        return t == null
            ? ResponseEntity.notFound().build()
            : ResponseEntity.ok(t);
    }

    public static boolean isApiCall(HttpServletRequest req)
    {
        return req.getServletPath().startsWith(API_PATH_PREFIX);
    }

    public static Duration cacheError
    (
        Throwable error,
        Collection<Class<? extends Throwable>> targetErrors,
        Duration duration
    )
    {
        return targetErrors.stream()
            .anyMatch(targetError->ExceptionUtils.indexOfType(error, targetError) != -1)
                ? duration
                : Duration.ZERO;
    }

    public static Duration cacheNotFoundError(Throwable error)
    {
        return cacheError
        (
            error,
            List.of(WebClientResponseException.NotFound.class),
            DEFAULT_API_CACHE_DURATION
        );
    }

    public static <T> Mono<T> handleMonoResponse
    (
        ClientResponse response,
        Class<T> targetClass,
        Set<? extends HttpStatusCode> contentStatuses,
        Set<? extends HttpStatusCode> emptyStatuses
    )
    {
        return contentStatuses.contains(response.statusCode())
            ? response.bodyToMono(targetClass)
            : emptyStatuses.contains(response.statusCode())
                ? Mono.empty()
                : response.createException().flatMap(Mono::error);
    }

    public static <T> Mono<T> handleMonoResponse
    (
        ClientResponse response,
        Class<T> targetClass
    )
    {
        return handleMonoResponse(response, targetClass, CONTENT_STATUS_CODES, EMPTY_STATUS_CODES);
    }

    public static <T> Flux<T> handleFluxResponse
    (
        ClientResponse response,
        Class<T> targetClass,
        Set<? extends HttpStatusCode> contentStatuses,
        Set<? extends HttpStatusCode> emptyStatuses
    )
    {
        return contentStatuses.contains(response.statusCode())
            ? response.bodyToFlux(targetClass)
            : emptyStatuses.contains(response.statusCode())
                ? Flux.empty()
                : response.createException().flatMapMany(Flux::error);
    }

    public static <T> Flux<T> handleFluxResponse
    (
        ClientResponse response,
        Class<T> targetClass
    )
    {
        return handleFluxResponse(response, targetClass, CONTENT_STATUS_CODES, EMPTY_STATUS_CODES);
    }

}
