// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import org.springframework.data.domain.Sort;

public enum SortingOrder
{

    ASC(Sort.Direction.ASC, "ASC"),
    DESC(Sort.Direction.DESC, "DESC");

    private final Sort.Direction sortDirection;
    private final String sqlKeyword;

    SortingOrder(Sort.Direction sortDirection, String sqlKeyword)
    {
        this.sortDirection = sortDirection;
        this.sqlKeyword = sqlKeyword;
    }

    public static SortingOrder from(Sort.Direction sortDirection)
    {
        return switch (sortDirection)
        {
            case ASC -> ASC;
            case DESC -> DESC;
            default -> throw new IllegalArgumentException
            ("Unsupported sort direction: " + sortDirection);
        };
    }

    public String getSqlKeyword()
    {
        return sqlKeyword;
    }

    public SortingOrder reverse()
    {
        return this == ASC ? DESC : ASC;
    }

    public Sort.Direction toSortDirection()
    {
        return this.sortDirection;
    }

}
