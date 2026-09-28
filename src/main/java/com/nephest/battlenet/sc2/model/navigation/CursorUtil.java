// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.navigation;

import com.nephest.battlenet.sc2.model.SortingOrder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;

public final class CursorUtil
{

    private CursorUtil(){}

    private static final Window<Object> EMPTY_WINDOW = Window.from
    (
        List.of(),
        position->ScrollPosition.keyset(),
        false
    );

    public static String[] getCursorNavigableQueryFormatArguments
    (
        Sort.Direction sortDirection,
        ScrollPosition.Direction scrollDirection,
        boolean orderFirst,
        String... additionalArguments
    )
    {
        Objects.requireNonNull(sortDirection);
        Objects.requireNonNull(scrollDirection);

        SortingOrder order = SortingOrder.from(sortDirection);
        String[] args = new String[2 + additionalArguments.length];
        args[orderFirst ? 0 : 1] = scrollDirection == ScrollPosition.Direction.FORWARD
            ? order.getSqlKeyword()
            : order.reverse().getSqlKeyword();
        args[orderFirst ? 1 : 0] = NavigationDirection.from(scrollDirection)
            .getOperator(sortDirection);
        if(additionalArguments.length > 0)
            System.arraycopy(additionalArguments, 0, args, 2, additionalArguments.length);
        return args;
    }

    public static String formatCursorNavigableQuery
    (
        String template,
        Sort.Direction sortDirection,
        ScrollPosition.Direction scrollDirection,
        boolean orderFirst,
        String... additionalArguments
    )
    {
        Objects.requireNonNull(template);
        Objects.requireNonNull(sortDirection);
        Objects.requireNonNull(scrollDirection);

        return template.formatted((Object[]) getCursorNavigableQueryFormatArguments(
            sortDirection,
            scrollDirection,
            orderFirst,
            additionalArguments
        ));
    }

    public static ScrollPosition.Direction getDirection
    (
        KeysetScrollPosition position
    )
    {
        return position != null
            ? position.getDirection()
            : ScrollPosition.Direction.FORWARD;
    }

    public static <T> IntFunction<ScrollPosition> windowPositionFunction
    (
        List<T> data,
        Function<T, Map<String, Object>> positionFunction
    )
    {
        return i->ScrollPosition.of
        (
            positionFunction.apply(data.get(i)),
            ScrollPosition.Direction.FORWARD
        );
    }

    @SuppressWarnings("unchecked")
    public static <T> Window<T> emptyWindow()
    {
        return (Window<T>) EMPTY_WINDOW;
    }

    public static <T> Window<T> window
    (
        List<T> data,
        Function<T, Map<String, Object>> positionFunction,
        boolean hasNext
    )
    {
        return Window.from
        (
            data,
            CursorUtil.windowPositionFunction(data, positionFunction),
            hasNext
        );
    }

}
