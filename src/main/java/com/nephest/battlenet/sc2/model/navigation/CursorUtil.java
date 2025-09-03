// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.navigation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.SortingOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;

public final class CursorUtil
{

    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();
    private static final Charset CHARSET = StandardCharsets.UTF_8;

    public static String encodePosition(Position position, ObjectMapper objectMapper)
    {
        if(position == null) return null;

        try
        {
            String json = objectMapper.writeValueAsString(position);
            return BASE64_ENCODER.encodeToString(json.getBytes(CHARSET));
        }
        catch (JsonProcessingException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static Position decodePosition(String position, ObjectMapper objectMapper)
    {
        if (position == null) return null;

        try
        {
            String json = new String(BASE64_DECODER.decode(position), CHARSET);
            return objectMapper.readValue(json, Position.class);
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalArgumentException("Invalid cursor position token: " + position, e);
        }
    }

    public static String[] getCursorNavigableQueryFormatArguments
    (
        SortingOrder order,
        NavigationDirection direction,
        boolean orderFirst,
        String... additionalArguments
    )
    {
        Objects.requireNonNull(order);
        Objects.requireNonNull(direction);

        String[] args = new String[2 + additionalArguments.length];
        args[orderFirst ? 0 : 1] = direction == NavigationDirection.FORWARD
            ? order.getSqlKeyword()
            : order.reverse().getSqlKeyword();
        args[orderFirst ? 1 : 0] = direction.getOperator(order);
        if(additionalArguments.length > 0)
            System.arraycopy(additionalArguments, 0, args, 2, additionalArguments.length);
        return args;
    }

    public static String formatCursorNavigableQuery
    (
        String template,
        SortingOrder order,
        NavigationDirection direction,
        boolean orderFirst,
        String... additionalArguments
    )
    {
        Objects.requireNonNull(template);
        Objects.requireNonNull(order);
        Objects.requireNonNull(direction);

        return template.formatted((Object[]) getCursorNavigableQueryFormatArguments(
            order,
            direction,
            orderFirst,
            additionalArguments
        ));
    }

}
