// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.userdetails.UserDetails;

public interface AutoLoginCookieProcessor
{

    UserDetails doProcessAutoLoginCookie
    (
        String[] cookieTokens,
        HttpServletRequest request,
        HttpServletResponse response
    );

}
