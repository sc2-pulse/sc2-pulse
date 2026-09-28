// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.navigation;

import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;

public enum NavigationDirection
{

    FORWARD(ScrollPosition.Direction.FORWARD, "after", ">", "<"),
    BACKWARD(ScrollPosition.Direction.BACKWARD, "before", "<", ">");

    private final ScrollPosition.Direction scrollPositionDirection;
    private final String relativePosition, ascOperator, descOperator;

    NavigationDirection
    (
        ScrollPosition.Direction scrollPositionDirection,
        String relativePosition,
        String ascOperator,
        String descOperator
    )
    {
        this.scrollPositionDirection = scrollPositionDirection;
        this.relativePosition = relativePosition;
        this.ascOperator = ascOperator;
        this.descOperator = descOperator;
    }

    public static NavigationDirection from
    (
        ScrollPosition.Direction scrollDirection
    )
    {
        if(scrollDirection == null) return null;

        return switch (scrollDirection)
        {
            case FORWARD -> FORWARD;
            case BACKWARD -> BACKWARD;
            default -> throw new IllegalArgumentException
                ("Unsupported scroll direction: " + scrollDirection);
        };
    }

    public String getRelativePosition()
    {
        return relativePosition;
    }

    public String getOperator(Sort.Direction order)
    {
        return order == Sort.Direction.ASC ? ascOperator : descOperator;
    }

    public ScrollPosition.Direction toScrollPositionDirection()
    {
        return scrollPositionDirection;
    }

}
