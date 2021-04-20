// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

public enum Period
{

    HOUR("1 HOURS"), DAY("1 DAYS"), WEEK("7 DAYS"), MONTH("1 MONTHS");

    private final String sqlPeriod;

    Period(String sqlPeriod)
    {
        this.sqlPeriod = sqlPeriod;
    }

    public String getSqlPeriod()
    {
        return sqlPeriod;
    }

}
