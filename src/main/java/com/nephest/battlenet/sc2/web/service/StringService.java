// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingFunction;

@Service
public class StringService
{

    private final ObjectMapper objectMapper;

    public StringService(ObjectMapper objectMapper)
    {
        this.objectMapper = objectMapper;
    }

    public String encode(Object value)
    {
        if(value == null) return null;

        try
        {
            byte[] json = objectMapper.writeValueAsBytes(value);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
        }
        catch(JsonProcessingException e)
        {
            throw new IllegalArgumentException("Unable to encode value", e);
        }
    }

    private <T> T decode(String value, ThrowingFunction<byte[], T> function)
    {
        if(value == null) return null;

        try
        {
            return function.apply(Base64.getUrlDecoder().decode(value));
        }
        catch(Exception e)
        {
            throw new IllegalArgumentException("Invalid encoded value: " + value, e);
        }
    }

    public <T> T decode(String value, Class<T> type)
    {
        return decode
        (
            value,
            json->objectMapper.readValue(json, type)
        );
    }

    public <T> T decode(String value, TypeReference<T> type)
    {
        return decode
        (
            value,
            json->objectMapper.readValue(json, type)
        );
    }

}
