// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nephest.battlenet.sc2.web.servlet.BodyLimitExceededException;
import com.nephest.battlenet.sc2.web.servlet.BoundedRequestWrapper;
import jakarta.servlet.FilterChain;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class BodyLimitFilterTest
{

    private static final int LIMIT = 10;

    private final BodyLimitFilter filter = new BodyLimitFilter(LIMIT);

    private static MockHttpServletRequest request
    (
        HttpMethod method,
        MediaType contentType,
        int size,
        boolean knownLength
    )
    {
        MockHttpServletRequest request = new MockHttpServletRequest
        (
            method.name(),
            "/api/x"
        )
        {
            @Override
            public long getContentLengthLong()
            {
                return knownLength ? super.getContentLengthLong() : -1;
            }
        };
        request.setContentType(contentType.toString());
        request.setContent("a".repeat(size).getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private static FilterChain readingChain(AtomicBoolean called)
    {
        return (req, res) ->
        {
            called.set(true);
            req.getInputStream().readAllBytes();
        };
    }

    public static Stream<Arguments> requests()
    {
        return Stream.of
        (
            //allowed
            Arguments.of
            (
                HttpMethod.POST,
                MediaType.APPLICATION_JSON,
                LIMIT,
                true,

                HttpStatus.OK,
                true
            ),
            Arguments.of
            (
                HttpMethod.PUT,
                MediaType.APPLICATION_JSON,
                LIMIT,
                true,

                HttpStatus.OK,
                true
            ),
            Arguments.of
            (
                HttpMethod.POST,
                MediaType.MULTIPART_FORM_DATA,
                LIMIT + 1,
                true,

                HttpStatus.OK,
                true
            ),
            Arguments.of
            (
                HttpMethod.GET,
                MediaType.APPLICATION_JSON,
                LIMIT + 1,
                true,

                HttpStatus.OK,
                true
            ),

            //rejected before the chain
            Arguments.of
            (
                HttpMethod.POST,
                MediaType.APPLICATION_JSON,
                LIMIT + 1,
                true,

                HttpStatus.PAYLOAD_TOO_LARGE,
                false
            ),
            Arguments.of
            (
                HttpMethod.PUT,
                MediaType.APPLICATION_JSON,
                LIMIT + 1,
                true,

                HttpStatus.PAYLOAD_TOO_LARGE,
                false
            ),
            //rejected on read
            Arguments.of
            (
                HttpMethod.POST,
                MediaType.APPLICATION_JSON,
                LIMIT + 1,
                false,

                HttpStatus.PAYLOAD_TOO_LARGE,
                true
            ),
            Arguments.of
            (
                HttpMethod.PUT,
                MediaType.APPLICATION_JSON,
                LIMIT + 1,
                false,

                HttpStatus.PAYLOAD_TOO_LARGE,
                true
            )
        );
    }

    @MethodSource("requests")
    @ParameterizedTest
    public void testFilter
    (
        HttpMethod method,
        MediaType contentType,
        int size,
        boolean knownLength,
        HttpStatus expectedStatus,
        boolean expectedChainCalled
    )
    throws Exception
    {
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean called = new AtomicBoolean();
        filter.doFilter
        (
            request(method, contentType, size, knownLength),
            response,
            readingChain(called)
        );
        assertEquals(expectedChainCalled, called.get());
        assertEquals(expectedStatus.value(), response.getStatus());
    }

    @Test
    public void whenReaderExceedsLimit_thenThrowLimitException()
    throws IOException
    {
        BoundedRequestWrapper wrapper = new BoundedRequestWrapper
        (
            request(HttpMethod.POST, MediaType.TEXT_PLAIN, LIMIT + 1, true),
            LIMIT
        );
        assertThrows
        (
            BodyLimitExceededException.class,
            () -> wrapper.getReader().readLine()
        );
    }

}
