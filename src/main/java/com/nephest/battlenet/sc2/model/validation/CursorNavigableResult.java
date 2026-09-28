// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import com.nephest.battlenet.sc2.model.CursorNavigation;
import com.nephest.battlenet.sc2.model.navigation.CursorUtil;
import com.nephest.battlenet.sc2.model.navigation.WindowWithPayload;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;

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
        Function<T, Map<String, Object>> keysFunction,
        boolean bidirectional
    )
    {
        if(data.isEmpty()) return new CursorNavigation(null, null);

        return new CursorNavigation
        (
            firstPage || !bidirectional
                ? null
                : ScrollPosition.of
                    (
                        keysFunction.apply(data.get(0)),
                        ScrollPosition.Direction.BACKWARD
                    ),
            data.size() != targetLimit
                ? null
                : ScrollPosition.of
                    (
                        keysFunction.apply(data.get(data.size() - 1)),
                        ScrollPosition.Direction.FORWARD
                    )
        );
    }

    public static <T> CursorNavigation createNavigation
    (
        List<T> data,
        int targetLimit,
        boolean firstPage,
        IntFunction<Map<String, Object>> keysFunction,
        boolean bidirectional
    )
    {
        if(data.isEmpty()) return CursorNavigation.EMPTY;

        return new CursorNavigation
        (
            firstPage || !bidirectional
                ? null
                : ScrollPosition.of
                    (
                        keysFunction.apply(0),
                        ScrollPosition.Direction.BACKWARD
                    ),
            data.size() != targetLimit
                ? null
                : ScrollPosition.of
                    (
                        keysFunction.apply(data.size() - 1),
                        ScrollPosition.Direction.FORWARD
                    )
        );
    }

    public static <T> CursorNavigation createNavigation
    (
        Window<T> window,
        boolean firstPage
    )
    {
        return createNavigation
        (
            window.getContent(),
            window.hasNext() ? window.size() : window.size() + 1,
            firstPage,
            (IntFunction<Map<String, Object>>) i->
            {
                ScrollPosition position = window.positionAt(i);
                if(!(position instanceof KeysetScrollPosition))
                    throw new IllegalStateException
                    (
                        "Only KeysetScrollPosition windows are supported"
                    );

                return ((KeysetScrollPosition) position).getKeys();
            },
            true
        );
    }

    public static <T> CursorNavigableResult<List<T>> wrap
    (
        List<T> data,
        int targetLimit,
        boolean firstPage,
        Function<T, Map<String, Object>> keysFunction,
        boolean bidirectional
    )
    {
        return new CursorNavigableResult<>
        (
            data,
            createNavigation(data, targetLimit, firstPage, keysFunction, bidirectional)
        );
    }

    public static <T> CursorNavigableResult<List<T>> wrap
    (
        List<T> data,
        int targetLimit,
        boolean firstPage,
        Function<T, Map<String, Object>> keysFunction
    )
    {
        return wrap(data, targetLimit, firstPage, keysFunction, true);
    }

    public static <T> CursorNavigableResult<List<T>> wrap
    (
        Window<T> window,
        boolean firstPage
    )
    {
        return new CursorNavigableResult<>
        (
            window.getContent(),
            createNavigation(window, firstPage)
        );
    }

    public static <W, P> CursorNavigableResult<P> wrap
    (
        WindowWithPayload<W, P> wwp,
        boolean firstPage
    )
    {
        return new CursorNavigableResult<>
        (
            wwp.payload(),
            createNavigation(wwp.window(), firstPage)
        );
    }

    public static <T> Window<T> toWindow
    (
        CursorNavigableResult<List<T>> cnr
    )
    {
        if(cnr.result() == null || cnr.result().isEmpty())
            return CursorUtil.emptyWindow();

        return Window.from
        (
            cnr.result(),
            i->
            {
                if(i < 0 || i >= cnr.result.size())
                    throw new IndexOutOfBoundsException(i);

                if(i == 0 && cnr.navigation.before() != null)
                    return cnr.navigation.before();
                if
                (
                    i == cnr.result().size() - 1
                    && cnr.navigation.after() != null
                )
                    return cnr.navigation.after();

                throw new IllegalStateException
                (
                    "Requested cursor position is not supported"
                );
            }
        );
    }

}
