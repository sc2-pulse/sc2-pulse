// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.web;

import com.nephest.battlenet.sc2.model.SortingOrder;
import jakarta.validation.constraints.NotNull;

public record SortParameter(@NotNull String field, @NotNull SortingOrder order)
{

    public static final String PREFIX_DESC = "-";
    public static final String PREFIX_ASC = "+";
    public static final String SUFFIX_DESC = "desc";
    public static final String SUFFIX_ASC = "asc";
    public static final String SUFFIX_DELIMITER = ":";

    public String toPrefixedString()
    {
        return switch (order)
        {
            case ASC -> field;
            case DESC -> PREFIX_DESC + field;
        };
    }

    public String toSuffixedString()
    {
        return switch (order)
        {
            case ASC -> field + SUFFIX_DELIMITER + SUFFIX_ASC;
            case DESC -> field + SUFFIX_DELIMITER + SUFFIX_DESC;
        };
    }

}
