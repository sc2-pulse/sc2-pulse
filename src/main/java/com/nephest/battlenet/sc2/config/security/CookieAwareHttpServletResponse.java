// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.util.ArrayList;
import java.util.List;

public class CookieAwareHttpServletResponse
extends HttpServletResponseWrapper
{

    private List<Cookie> cookies;

    public CookieAwareHttpServletResponse(HttpServletResponse response)
    {
        super(response);
    }

    @Override
    public void addCookie(Cookie cookie)
    {
        if(cookies == null) cookies = new ArrayList<>();
        cookies.add(cookie);
        super.addCookie(cookie);
    }

    public List<Cookie> getCookies()
    {
        return cookies;
    }

}
