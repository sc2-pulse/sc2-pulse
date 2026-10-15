// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.servlet;

import jakarta.servlet.ServletRequest;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public final class ServletUtil
{

    private ServletUtil(){}

    /**
     * Returns the request body charset. Falls back to ISO-8859-1 when the
     * request doesn't specify an encoding, as defined by the servlet spec.
     *
     * @param request request
     * @return request charset
     * @throws UnsupportedEncodingException if the encoding is not supported
     */
    public static Charset getCharset(ServletRequest request)
    throws UnsupportedEncodingException
    {
        String encoding = request.getCharacterEncoding();
        if(encoding == null) return StandardCharsets.ISO_8859_1;

        try
        {
            return Charset.forName(encoding);
        }
        catch(IllegalArgumentException ex)
        {
            throw new UnsupportedEncodingException(encoding);
        }
    }

}
