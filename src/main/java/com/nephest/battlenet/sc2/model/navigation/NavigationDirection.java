// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.navigation;

import com.nephest.battlenet.sc2.model.SortingOrder;

public enum NavigationDirection
{

    FORWARD("after", ">", "<"),
    BACKWARD("before", "<", ">");

    private final String relativePosition, ascOperator, descOperator;

    NavigationDirection(String relativePosition, String ascOperator, String descOperator)
    {
        this.relativePosition = relativePosition;
        this.ascOperator = ascOperator;
        this.descOperator = descOperator;
    }

    public String getRelativePosition()
    {
        return relativePosition;
    }

    public String getOperator(SortingOrder order)
    {
        return order == SortingOrder.ASC ? ascOperator : descOperator;
    }

}
