// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import com.nephest.battlenet.sc2.util.SpringUtil;
import com.nephest.battlenet.sc2.web.service.StringService;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class KeysetScrollPositionArgumentResolver
implements HandlerMethodArgumentResolver
{

    private final StringService stringService;

    public KeysetScrollPositionArgumentResolver(StringService stringService)
    {
        this.stringService = stringService;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter)
    {
        return SpringUtil.getClass(parameter)
            .isAssignableFrom(KeysetScrollPosition.class);
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
            String value = webRequest
                .getParameter(direction.getRelativePosition());
            if(value == null) continue;

            return ScrollPosition.of
            (
                stringService.decode(value, new TypeReference<>(){}),
                direction.toScrollPositionDirection()
            );
        }
        return null;
    }

}
