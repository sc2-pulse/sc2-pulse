// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.navigation.CursorType;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import com.nephest.battlenet.sc2.util.SpringUtil;
import com.nephest.battlenet.sc2.web.service.StringService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.mapping.PropertyPath;
import org.springframework.data.mapping.PropertyReferenceException;
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
    private final ObjectMapper objectMapper;

    public KeysetScrollPositionArgumentResolver
    (
        StringService stringService,
        ObjectMapper objectMapper
    )
    {
        this.stringService = stringService;
        this.objectMapper = objectMapper;
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
                decode(value, getCursorType(parameter)),
                direction.toScrollPositionDirection()
            );
        }
        return ScrollPosition.keyset();
    }

    private static Class<?> getCursorType(MethodParameter parameter)
    {
        if(parameter == null) return null;

        CursorType cursorType = parameter
            .getParameterAnnotation(CursorType.class);
        return cursorType == null || cursorType.value() == void.class
            ? null
            : cursorType.value();
    }

    private Map<String, Object> decode(String value, Class<?> type)
    {
        if(type == null)
            return stringService.decode(value, new TypeReference<>(){});

        Map<String, JsonNode> rawKeys = stringService
            .decode(value, new TypeReference<>(){});
        if(rawKeys == null) return null;

        Map<String, Object> keys = new HashMap<>(rawKeys.size());
        for(Map.Entry<String, JsonNode> rawKey : rawKeys.entrySet())
            keys.put
            (
                rawKey.getKey(),
                convert(rawKey.getKey(), rawKey.getValue(), type)
            );
        return keys;
    }

    private Object convert(String key, JsonNode value, Class<?> type)
    {
        try
        {
            return objectMapper.treeToValue
            (
                value,
                PropertyPath.from(key, type).getLeafType()
            );
        }
        catch(JsonProcessingException | PropertyReferenceException ex)
        {
            throw new IllegalArgumentException
            (
                "Invalid cursor key: " + key,
                ex
            );
        }
    }

}
