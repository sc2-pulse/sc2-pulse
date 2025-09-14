// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.util.LogUtil;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.server.ResponseStatusException;
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

    @CsvSource({",404", "a,200"})
    @ParameterizedTest
    public void testNotFoundIfNull(String val, int code)
    {
        assertEquals(code, WebServiceUtil.notFoundIfNull(val).getStatusCode().value());
    }

    @CsvSource
    ({
        "/, false",
        "'', false",
        "/asd, false",
        "/asd/qwe, false",
        "/api, false",
        "/api/, true"
    })
    @ParameterizedTest
    public void testIsApiCall(String path, boolean expected)
    {
        HttpServletRequest req = mock(HttpServletRequest.class);
        when(req.getContextPath()).thenReturn("");
        when(req.getServletPath()).thenReturn(path);
        assertEquals(expected, WebServiceUtil.isApiCall(req));

        String contextPath = "/ctx";
        when(req.getContextPath()).thenReturn(contextPath);
        assertEquals(expected, WebServiceUtil.isApiCall(req));
    }

    public static Stream<Arguments> testCacheError()
    {
        Throwable notFound = mock(WebClientResponseException.NotFound.class);
        Throwable ise = mock(WebClientResponseException.InternalServerError.class);
        List<Class<? extends Throwable>> targetExceptions =
            List.of(WebClientResponseException.NotFound.class);
        return Stream.of
        (
            Arguments.of(notFound, ise, targetExceptions),
            Arguments.of(new RuntimeException("test", notFound), ise, targetExceptions)
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testCacheError
    (
        Throwable targetException,
        Throwable notTargetException,
        List<Class<? extends Throwable>> targetExceptions
    )
    {
        assertEquals
        (
            WebServiceUtil.DEFAULT_API_CACHE_DURATION,
            WebServiceUtil.cacheError
            (
                targetException,
                targetExceptions,
                WebServiceUtil.DEFAULT_API_CACHE_DURATION
            )
        );
        assertEquals
        (
            Duration.ZERO,
            WebServiceUtil.cacheError
            (
                notTargetException,
                targetExceptions,
                WebServiceUtil.DEFAULT_API_CACHE_DURATION
            )
        );
    }

    @Test
    public void testCacheNotFoundError()
    {
        Throwable notFound = mock(WebClientResponseException.NotFound.class);
        Throwable ise = mock(WebClientResponseException.InternalServerError.class);
        assertEquals
        (
            WebServiceUtil.DEFAULT_API_CACHE_DURATION,
            WebServiceUtil.cacheNotFoundError(notFound)
        );
        assertEquals
        (
            Duration.ZERO,
            WebServiceUtil.cacheNotFoundError(ise)
        );
    }

    @CsvSource
    ({
        "NOT_FOUND, body",
        "BAD_REQUEST,"
    })
    @ParameterizedTest
    public void testThrowException(HttpStatus status, String body)
    {
        try
        {
            WebServiceUtil.throwException(ResponseEntity.status(status).body(body));
        }
        catch (ResponseStatusException ex)
        {
            assertEquals(status, ex.getStatusCode());
            assertEquals(body, ex.getReason());
            return;
        }
        throw new IllegalStateException("Expected exception was not thrown");
    }

    @Test
    public void shouldNotThrowIfNull()
    {
        WebServiceUtil.throwException(null);
    }

}
