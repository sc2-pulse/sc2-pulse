// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import static com.nephest.battlenet.sc2.model.web.SortParameter.PREFIX_ASC;
import static com.nephest.battlenet.sc2.model.web.SortParameter.PREFIX_DESC;
import static com.nephest.battlenet.sc2.model.web.SortParameter.SUFFIX_ASC;
import static com.nephest.battlenet.sc2.model.web.SortParameter.SUFFIX_DELIMITER;
import static com.nephest.battlenet.sc2.model.web.SortParameter.SUFFIX_DESC;

import com.nephest.battlenet.sc2.model.SortingOrder;
import com.nephest.battlenet.sc2.model.web.SortParameter;
import java.util.Optional;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class SortParameterConverter
implements Converter<String, SortParameter>
{

    @Override
    public SortParameter convert(@NotNull String source)
    {
        if(source.isBlank())
            throw new IllegalArgumentException("Sort parameter cannot be blank");

        String trimmed = source.trim();
        return Optional.ofNullable(convertWithSuffix(trimmed))
            .orElseGet(()->convertWithPrefix(trimmed));
    }

    private SortParameter convertWithSuffix(@NotNull String param)
    {
        int colonIndex = param.indexOf(SUFFIX_DELIMITER);
        if(colonIndex == -1) return null;

        String field = param.substring(0, colonIndex);
        if (field.isBlank())
            throw new IllegalArgumentException("Sort field cannot be blank");

        String suffix = param.substring(colonIndex + 1).toLowerCase();
        SortingOrder order = switch (suffix)
        {
            case SUFFIX_ASC -> SortingOrder.ASC;
            case SUFFIX_DESC -> SortingOrder.DESC;
            default -> throw new IllegalArgumentException("Invalid sort order: " + suffix);
        };
        return new SortParameter(field, order);
    }

    private SortParameter convertWithPrefix(@NotNull String param)
    {
        SortingOrder order = SortingOrder.ASC;
        String field = param;

        if(param.startsWith(PREFIX_DESC))
        {
            order = SortingOrder.DESC;
            field = param.substring(1);
        }
        else if(param.startsWith(PREFIX_ASC))
        {
            field = param.substring(1);
        }

        if (field.isBlank())
            throw new IllegalArgumentException("Sort field cannot be blank");

        return new SortParameter(field, order);
    }

}

