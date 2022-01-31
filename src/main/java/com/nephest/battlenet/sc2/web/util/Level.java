// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.util;

public enum Level
{
    SUCCESS("success", "text-success"),
    WARNING("warning", "text-warning"),
    DANGER("danger", "text-danger");

    private final String name;
    private final String cssClass;

    Level(String name, String cssClass)
    {
        this.name = name;
        this.cssClass = cssClass;
    }

    public String getName()
    {
        return name;
    }

    public String getCssClass()
    {
        return cssClass;
    }

}
