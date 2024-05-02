// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.net;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.util.WebUtils;

@ControllerAdvice
public class GlobalControllerAdvice
{

    public static final String THEME_COOKIE_NAME = "theme";
    public static final String THEME_DEFAULT = "light";
    public static final String API_PREFIX = "/api/";

    @ModelAttribute(THEME_COOKIE_NAME)
    public String theme(HttpServletRequest request)
    {
        if(request.getRequestURI().startsWith(API_PREFIX)) return null;

        return Optional.ofNullable(WebUtils.getCookie(request, THEME_COOKIE_NAME))
            .map(Cookie::getValue)
            .orElse(THEME_DEFAULT);
    }

}
