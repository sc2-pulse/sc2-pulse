// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import java.io.IOException;
import java.util.Arrays;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;

public class ResetSessionInformationExpiredStrategy
implements SessionInformationExpiredStrategy
{

    private final String sessionCookieName, redirectUrl, restPrefix;

    public ResetSessionInformationExpiredStrategy
    (
        String sessionCookieName,
        String redirectUrl,
        String restPrefix
    )
    {
        this.sessionCookieName = sessionCookieName;
        this.redirectUrl = redirectUrl;
        this.restPrefix = restPrefix;
    }

    @Override
    public void onExpiredSessionDetected(SessionInformationExpiredEvent event)
    throws IOException
    {
        deleteSessionCookies(sessionCookieName, event.getRequest(), event.getResponse());
        event.getResponse().setHeader("X-Application-Version", "-1");
        if(!event.getRequest().getServletPath().startsWith(restPrefix))
            event.getResponse().sendRedirect(redirectUrl);
    }

    private static void deleteSessionCookies
    (
        String sessionCookieName,
        HttpServletRequest request,
        HttpServletResponse response
    )
    {
        if(request.getCookies() == null) return;

        Arrays.stream(request.getCookies())
            .filter(c->c.getName().equals(sessionCookieName))
            .forEach(c->
            {
                c.setMaxAge(0);
                response.addCookie(c);
            });
    }

}
