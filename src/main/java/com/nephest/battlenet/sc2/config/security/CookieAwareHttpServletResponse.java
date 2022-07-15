// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import java.util.ArrayList;
import java.util.List;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

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
