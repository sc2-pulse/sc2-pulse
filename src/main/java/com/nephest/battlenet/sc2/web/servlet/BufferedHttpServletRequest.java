// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.servlet;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Objects;

/**
 * Request wrapper that reads and buffers the whole request body. Each
 * {@link #getInputStream()} call returns a new stream over the buffered
 * bytes.
 */
public class BufferedHttpServletRequest
extends HttpServletRequestWrapper
{

    private final byte[] body;

    private BufferedHttpServletRequest(HttpServletRequest request, byte[] body)
    {
        super(request);
        this.body = body;
    }

    /**
     * Wraps the request, reading its body. Returns the supplied request if it
     * is already a buffered wrapper.
     *
     * @param request request to wrap
     * @return buffered request
     * @throws IOException if the body can't be read
     */
    public static BufferedHttpServletRequest from(HttpServletRequest request)
    throws IOException
    {
        if(request instanceof BufferedHttpServletRequest buffered) return buffered;

        return new BufferedHttpServletRequest
        (
            request,
            request.getInputStream().readAllBytes()
        );
    }

    @Override
    public ServletInputStream getInputStream()
    {
        return new ByteArrayServletInputStream(body);
    }

    @Override
    public BufferedReader getReader()
    throws UnsupportedEncodingException
    {
        return new BufferedReader
        (
            new InputStreamReader
            (
                getInputStream(),
                ServletUtil.getCharset(this)
            )
        );
    }

    private static final class ByteArrayServletInputStream
    extends ServletInputStream
    {

        private final ByteArrayInputStream delegate;
        private ReadListener readListener;

        private ByteArrayServletInputStream(byte[] body)
        {
            this.delegate = new ByteArrayInputStream(body);
        }

        @Override
        public int read()
        {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len)
        {
            return delegate.read(b, off, len);
        }

        @Override
        public int available()
        {
            return delegate.available();
        }

        @Override
        public boolean isFinished()
        {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady()
        {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener)
        {
            Objects.requireNonNull(readListener);
            if(this.readListener != null) throw new IllegalStateException
                ("ReadListener has already been set");

            this.readListener = readListener;
            try
            {
                if(!isFinished()) readListener.onDataAvailable();
                readListener.onAllDataRead();
            }
            catch(IOException | RuntimeException ex)
            {
                readListener.onError(ex);
            }
        }

    }

}
