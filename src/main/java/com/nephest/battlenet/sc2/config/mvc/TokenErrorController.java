// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.mvc;

import java.util.List;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.authentication.rememberme.CookieTheftException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

/**
 * This error controller forwards to a proper error page when remember me token is invalidated
 * due to exception, it behaves like
 * {@link org.springframework.boot.autoconfigure.web.servlet.error.BasicErrorController}
 * otherwise. These exceptions can't be handled in
 * {@link org.springframework.security.web.AuthenticationEntryPoint} or
 * {@link org.springframework.web.bind.annotation.ExceptionHandler}, so it seems
 * this is the only place where it can be done.
 */
@Controller
public class TokenErrorController
extends BasicErrorController
{

    @Autowired
    public TokenErrorController
    (
        ErrorAttributes errorAttributes,
        ServerProperties serverProperties,
        List<ErrorViewResolver> errorViewResolvers
    )
    {
        super(errorAttributes, serverProperties.getError(), errorViewResolvers);
    }

    @RequestMapping
    @Override
    public ResponseEntity<Map<String,Object>> error(HttpServletRequest request)
    {
        return super.error(request);
    }

    @RequestMapping(produces="text/html")
    @Override
    public ModelAndView errorHtml(HttpServletRequest request, HttpServletResponse response)
    {
        Exception e = (Exception) request.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
        if(e == null) return super.errorHtml(request, response);

        if(e instanceof CookieTheftException || e.getMessage().startsWith("No persistent token found"))
            return new ModelAndView("error/remember-me-invalid-token");

        return super.errorHtml(request, response);
    }

}
