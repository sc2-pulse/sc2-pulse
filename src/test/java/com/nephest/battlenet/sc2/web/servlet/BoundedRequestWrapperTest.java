// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.servlet;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class BoundedRequestWrapperTest
{

    private static BoundedRequestWrapper wrapper()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent("a".getBytes(StandardCharsets.UTF_8));
        return new BoundedRequestWrapper(request, 10);
    }

    @Test
    public void whenReaderAfterInputStream_thenThrow()
    throws Exception
    {
        BoundedRequestWrapper wrapper = wrapper();
        wrapper.getInputStream();
        assertThrows(IllegalStateException.class, wrapper::getReader);
    }

    @Test
    public void whenInputStreamAfterReader_thenThrow()
    throws Exception
    {
        BoundedRequestWrapper wrapper = wrapper();
        wrapper.getReader();
        assertThrows(IllegalStateException.class, wrapper::getInputStream);
    }

    @Test
    public void whenUnsupportedEncoding_thenThrowUnsupportedEncoding()
    {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCharacterEncoding("unknown-charset");
        BoundedRequestWrapper wrapper = new BoundedRequestWrapper(request, 10);
        assertThrows(UnsupportedEncodingException.class, wrapper::getReader);
    }

    @Test
    public void whenSameAccessorRepeated_thenReturnSameInstance()
    throws Exception
    {
        BoundedRequestWrapper stream = wrapper();
        assertSame(stream.getInputStream(), stream.getInputStream());
        BoundedRequestWrapper reader = wrapper();
        assertSame(reader.getReader(), reader.getReader());
    }

}
