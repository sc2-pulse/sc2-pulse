// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.navigation.CursorUtil;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import com.nephest.battlenet.sc2.model.navigation.Position;
import com.nephest.battlenet.sc2.util.SpringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CursorArgumentResolver
implements HandlerMethodArgumentResolver
{

    private final ObjectMapper objectMapper;

    @Autowired
    public CursorArgumentResolver(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter)
    {
        return SpringUtil.getClass(parameter).isAssignableFrom(Cursor.class);
    }

    @Override
    public Object resolveArgument
    (
        MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    )
    {
        for(NavigationDirection direction : NavigationDirection.values())
        {
            String value = webRequest.getParameter(direction.getRelativePosition());
            if(value == null) continue;

            Position position = CursorUtil.decodePosition(value, objectMapper);
            return new Cursor(position, direction);
        }
        return null;
    }

}
