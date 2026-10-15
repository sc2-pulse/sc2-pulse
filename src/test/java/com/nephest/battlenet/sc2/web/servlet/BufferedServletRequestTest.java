// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.servlet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class BufferedServletRequestTest
{

    private static final String BODY_STRING = "test body";
    private static final byte[] BODY = BODY_STRING.getBytes
        (StandardCharsets.UTF_8);

    @Test
    public void testInputStreamIsReadableMultipleTimes()
    throws IOException
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(BODY);
        BufferedServletRequest buffered = BufferedServletRequest.from(request);

        assertArrayEquals(BODY, buffered.getInputStream().readAllBytes());
        assertArrayEquals(BODY, buffered.getInputStream().readAllBytes());
    }

    @Test
    public void testReader()
    throws IOException
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCharacterEncoding("UTF-8");
        request.setContent(BODY);
        BufferedServletRequest buffered = BufferedServletRequest.from(request);

        assertEquals(BODY_STRING, buffered.getReader().readLine());
        assertEquals(BODY_STRING, buffered.getReader().readLine());
    }

    @Test
    public void whenRequestIsAlreadyBuffered_thenReturnSameInstance()
    throws IOException
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(BODY);
        BufferedServletRequest buffered = BufferedServletRequest.from(request);

        assertSame(buffered, BufferedServletRequest.from(buffered));
    }

    @Test
    public void whenBodyIsEmpty_thenStreamIsFinished()
    throws IOException
    {
        BufferedServletRequest buffered = BufferedServletRequest.from
            (new MockHttpServletRequest());

        assertEquals(0, buffered.getInputStream().readAllBytes().length);
        assertTrue(buffered.getInputStream().isFinished());
    }

    @Test
    public void testReadListener()
    throws IOException
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(BODY);
        ServletInputStream in = BufferedServletRequest.from(request)
            .getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean[] done = new boolean[1];

        in.setReadListener(new ReadListener()
        {
            @Override
            public void onDataAvailable()
            throws IOException
            {
                while(in.isReady() && !in.isFinished()) out.write(in.read());
            }

            @Override
            public void onAllDataRead()
            {
                done[0] = true;
            }

            @Override
            public void onError(Throwable t)
            {
                throw new IllegalStateException(t);
            }
        });

        assertArrayEquals(BODY, out.toByteArray());
        assertTrue(done[0]);
    }

    @Test
    public void whenReadListenerIsSetTwice_thenThrowException()
    throws IOException
    {
        ServletInputStream in = BufferedServletRequest
            .from(new MockHttpServletRequest())
            .getInputStream();
        ReadListener listener = new ReadListener()
        {
            @Override
            public void onDataAvailable()
            {
            }

            @Override
            public void onAllDataRead()
            {
            }

            @Override
            public void onError(Throwable t)
            {
            }
        };
        in.setReadListener(listener);

        assertThrows
        (
            IllegalStateException.class,
            ()->in.setReadListener(listener)
        );
    }

}
