// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

public enum SortingOrder
{

    ASC("ASC"),
    DESC("DESC");

    private final String sqlKeyword;

    SortingOrder(String sqlKeyword)
    {
        this.sqlKeyword = sqlKeyword;
    }

    public String getSqlKeyword()
    {
        return sqlKeyword;
    }

}
