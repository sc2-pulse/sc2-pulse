// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Component
public class SameSiteRememberMeAuthenticationSuccessfulHandler
extends SavedRequestAwareAuthenticationSuccessHandler
{

    public static final String PRE_AUTH_PATH_COOKIE_NAME = "pre-auth-path";
    public static final String COOKIE_NAME = "oauth-reg";
    //30 days according to a bluepost
    public static final int COOKIE_MAX_AGE = 3600 * 24 * 30;

    @Autowired
    private ServletContext servletContext;

    @Value("${spring.security.oauth2.client.provider.battlenet-eu.issuer-uri:#{''}}")
    private String issuerEu;

    @Value("${spring.security.oauth2.client.provider.battlenet-us.issuer-uri:#{''}}")
    private String issuerUs;

    @Value("${spring.security.oauth2.client.provider.battlenet-kr.issuer-uri:#{''}}")
    private String issuerKr;

    @Value("${spring.security.oauth2.client.provider.battlenet-cn.issuer-uri:#{''}}")
    private String issuerCn;

    @Value("${spring.security.oauth2.client.registration.sc2-lg-eu:#{''}}")
    private String regEu;

    @Value("${spring.security.oauth2.client.registration.sc2-lg-us:#{''}}")
    private String regUs;

    @Value("${spring.security.oauth2.client.registration.sc2-lg-kr:#{''}}")
    private String regKr;

    @Value("${spring.security.oauth2.client.registration.sc2-lg-cn:#{''}}")
    private String regCn;

    private Map<String, String> issuers;

    public SameSiteRememberMeAuthenticationSuccessfulHandler()
    {
    }

    @PostConstruct
    public void init()
    {
        Map<String, String> iss = new HashMap<>();
        iss.put(regEu, issuerEu);
        iss.put(regUs, issuerUs);
        iss.put(regKr, issuerKr);
        iss.put(regCn, issuerCn);
        issuers = Collections.unmodifiableMap(iss);

        setRedirectStrategy((req, resp, url)->
        {
            Cookie preAuthPathCookie = WebUtils.getCookie(req, PRE_AUTH_PATH_COOKIE_NAME);
            if(preAuthPathCookie == null)
            {
                addSameSiteCookieAttribute(resp);
                resp.sendRedirect(servletContext.getContextPath() + "/?#personal-characters");
            }
            else
            {
                Cookie deleteCookie = new Cookie(PRE_AUTH_PATH_COOKIE_NAME, null);
                deleteCookie.setMaxAge(0);
                deleteCookie.setPath(servletContext.getContextPath() + "/");
                deleteCookie.setSecure(true);
                resp.addCookie(deleteCookie);
                addSameSiteCookieAttribute(resp);
                resp.sendRedirect(resp.encodeRedirectURL(servletContext.getContextPath() + preAuthPathCookie.getValue()));
            }
        });
    }

    @Override
    public void onAuthenticationSuccess
    (
        HttpServletRequest request,
        HttpServletResponse response,
        Authentication authentication
    )
    throws IOException, ServletException
    {
        BlizzardOidcUser user = (BlizzardOidcUser) authentication.getPrincipal();
        String reg = null;
        String iss = user.getAttribute("iss").toString();
        for(Map.Entry<String, String> entry : issuers.entrySet())
            if(iss.startsWith(entry.getValue())) {reg = entry.getKey(); break;}
        if(reg == null || reg.isEmpty())
            throw new IllegalStateException("ISS/reg not found. Invalid security configuration? ISS: " + iss);

        Cookie cookie = new Cookie(COOKIE_NAME, reg);
            cookie.setHttpOnly(false); //needed by js + nothing secret about it
            cookie.setMaxAge(COOKIE_MAX_AGE);
            cookie.setPath(request.getContextPath() + "/"); // + / for correctly deleting the cookie in security config
        response.addCookie(cookie);
        super.onAuthenticationSuccess(request, response, authentication);
    }

    private void addSameSiteCookieAttribute(HttpServletResponse response) {
        Collection<String> headers = response.getHeaders(HttpHeaders.SET_COOKIE);
        boolean firstHeader = true;
        for (String header : headers) {
            if (firstHeader) {
                response.setHeader(HttpHeaders.SET_COOKIE, String.format("%s; %s", header, "SameSite=Lax"));
                firstHeader = false;
                continue;
            }
            response.addHeader(HttpHeaders.SET_COOKIE, String.format("%s; %s", header, "SameSite=Lax"));
        }
    }

}
