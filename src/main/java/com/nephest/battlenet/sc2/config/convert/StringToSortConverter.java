// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert;

import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class StringToSortConverter
implements Converter<String, Sort>
{

    public static final String PREFIX_DESC = "-";
    public static final String PREFIX_ASC = "+";
    public static final String SUFFIX_DESC = "desc";
    public static final String SUFFIX_ASC = "asc";
    public static final String SUFFIX_DELIMITER = ":";
    public static final String DELIMITER = ",";

    @Override
    public Sort convert(@NotNull String source)
    {
        if(source.isBlank())
            throw new IllegalArgumentException("Sort parameter cannot be blank");

        return Arrays.stream(source.split(DELIMITER, -1))
            .map(String::trim)
            .map(this::convertField)
            .reduce(Sort.unsorted(), Sort::and);
    }

    private Sort convertField(String field)
    {
        return Optional.ofNullable(convertWithSuffix(field))
            .orElseGet(()->convertWithPrefix(field));
    }

    private Sort convertWithSuffix(@NotNull String param)
    {
        int delimiterIndex = param.indexOf(SUFFIX_DELIMITER);
        if(delimiterIndex == -1) return null;

        String field = param.substring(0, delimiterIndex);
        if (field.isBlank())
            throw new IllegalArgumentException("Sort field cannot be blank");

        String suffix = param.substring(delimiterIndex + 1).toLowerCase();
        Sort.Direction order = switch (suffix)
        {
            case SUFFIX_ASC -> Sort.Direction.ASC;
            case SUFFIX_DESC -> Sort.Direction.DESC;
            default -> throw new IllegalArgumentException("Invalid sort order: " + suffix);
        };
        return Sort.by(order, field);
    }

    private Sort convertWithPrefix(@NotNull String param)
    {
        Sort.Direction order = Sort.Direction.ASC;
        String field = param;

        if(param.startsWith(PREFIX_DESC))
        {
            order = Sort.Direction.DESC;
            field = param.substring(1);
        }
        else if(param.startsWith(PREFIX_ASC))
        {
            field = param.substring(1);
        }

        if (field.isBlank())
            throw new IllegalArgumentException("Sort field cannot be blank");

        return Sort.by(order, field);
    }

}
