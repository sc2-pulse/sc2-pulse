// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import com.nephest.battlenet.sc2.model.CursorNavigation;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import com.nephest.battlenet.sc2.model.navigation.Position;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.function.Function;

public record CursorNavigableResult<T>(@Nullable T result, @NotNull CursorNavigation navigation)
{

    private static final CursorNavigableResult<Object> EMPTY_VALUE
        = new CursorNavigableResult<>(null, CursorNavigation.EMPTY);

    public static <T> CursorNavigableResult<List<T>> emptyList()
    {
        return new CursorNavigableResult<>(List.of(), CursorNavigation.EMPTY);
    }

    @SuppressWarnings("unchecked")
    public static <T> CursorNavigableResult<T> emptyValue()
    {
        return (CursorNavigableResult<T>) EMPTY_VALUE;
    }

    public static <T> CursorNavigation createNavigation
    (
        List<T> data,
        int targetLimit,
        boolean firstPage,
        Function<T, Position> positionFunction,
        boolean bidirectional
    )
    {
        if(data.isEmpty()) return new CursorNavigation(null, null);

        return new CursorNavigation
        (
            firstPage || !bidirectional
                ? null
                : new Cursor(positionFunction.apply(data.get(0)), NavigationDirection.BACKWARD),
            data.size() != targetLimit
                ? null
                : new Cursor
                (
                    positionFunction.apply(data.get(data.size() - 1)),
                    NavigationDirection.FORWARD
                )
        );
    }

    public static <T> CursorNavigableResult<List<T>> wrap
    (
        List<T> data,
        int targetLimit,
        boolean firstPage,
        Function<T, Position> positionFunction,
        boolean bidirectional
    )
    {
        return new CursorNavigableResult<>
        (
            data,
            createNavigation(data, targetLimit, firstPage, positionFunction, bidirectional)
        );
    }

    public static <T> CursorNavigableResult<List<T>> wrap
    (
        List<T> data,
        int targetLimit,
        boolean firstPage,
        Function<T, Position> positionFunction
    )
    {
        return wrap(data, targetLimit, firstPage, positionFunction, true);
    }

}
