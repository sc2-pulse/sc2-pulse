// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.servlet;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

/**
 * Request wrapper that throws {@link BodyLimitExceededException} when more
 * than {@code limit} bytes are read from the body.
 */
public class BoundedRequestWrapper
extends HttpServletRequestWrapper
{

    private final long limit;
    private BoundedInputStream stream;
    private BufferedReader reader;
    private boolean streamRequested;

    public BoundedRequestWrapper(HttpServletRequest request, long limit)
    {
        super(request);
        this.limit = limit;
    }

    public long getLimit()
    {
        return limit;
    }

    @Override
    public ServletInputStream getInputStream()
    throws IOException
    {
        if(reader != null) throw new IllegalStateException
            ("getReader() has already been called");

        streamRequested = true;
        return getBoundedStream();
    }

    @Override
    public BufferedReader getReader()
    throws IOException
    {
        if(streamRequested) throw new IllegalStateException
            ("getInputStream() has already been called");

        if(reader == null)
        {
            reader = new BufferedReader
            (
                new InputStreamReader
                (
                    getBoundedStream(),
                    ServletUtil.getCharset(this)
                )
            );
        }
        return reader;
    }

    private BoundedInputStream getBoundedStream()
    throws IOException
    {
        if(stream == null)
            stream = new BoundedInputStream(super.getInputStream(), limit);
        return stream;
    }

    private static final class BoundedInputStream
    extends ServletInputStream
    {

        private final ServletInputStream delegate;
        private final long limit;
        private long count;

        private BoundedInputStream(ServletInputStream delegate, long limit)
        {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read()
        throws IOException
        {
            int b = delegate.read();
            if(b != -1) add(1);
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len)
        throws IOException
        {
            int read = delegate.read(b, off, len);
            if(read > 0) add(read);
            return read;
        }

        private void add(int bytes)
        throws BodyLimitExceededException
        {
            count += bytes;
            if(count > limit) throw new BodyLimitExceededException(limit);
        }

        @Override
        public boolean isFinished()
        {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady()
        {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener)
        {
            delegate.setReadListener(readListener);
        }

        @Override
        public void close()
        throws IOException
        {
            delegate.close();
        }

    }

}
