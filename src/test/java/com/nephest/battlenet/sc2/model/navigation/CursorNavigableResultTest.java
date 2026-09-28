// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.navigation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.CursorNavigation;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.ScrollPosition;

public class CursorNavigableResultTest
{

    private static final KeysetScrollPosition beforeCursor
        = ScrollPosition.backward(Map.of("id", "2"));
    private static final KeysetScrollPosition afterCursor
        = ScrollPosition.forward(Map.of("id", "1"));

    @Test
    public void whenEmptyData_thenEmptyNavigation()
    {
        assertEquals
        (
            new CursorNavigableResult<List<String>>
            (
                List.of(),
                new CursorNavigation(null, null)
            ),
            CursorNavigableResult.wrap(List.of(), 0, false, p->beforeCursor.getKeys())
        );
    }

    @Test
    public void whenFirstPage_thenNoBeforeCursor()
    {
        assertEquals
        (
            new CursorNavigableResult<>
            (
                List.of(1),
                new CursorNavigation(null, afterCursor)
            ),
            CursorNavigableResult.wrap(List.of(1), 1, true, p->afterCursor.getKeys())
        );
    }

    @Test
    public void whenNotBidirectional_thenNoBeforeCursor()
    {
        assertEquals
        (
            new CursorNavigableResult<>
            (
                List.of(1),
                new CursorNavigation(null, afterCursor)
            ),
            CursorNavigableResult.wrap(List.of(1), 1, false, p->afterCursor.getKeys(), false)
        );
    }

    @Test
    public void whenDataSizeIsLessThanTarget_thenNoAfterCursor()
    {
        assertEquals
        (
            new CursorNavigableResult<>
            (
                List.of(1, 2),
                new CursorNavigation(beforeCursor, null)
            ),
            CursorNavigableResult.wrap(List.of(1, 2), 3, false, p->beforeCursor.getKeys())
        );
    }

    @Test
    public void testWrap()
    {
        assertEquals
        (
            new CursorNavigableResult<>
            (
                List.of(1, 2, 3),
                new CursorNavigation(beforeCursor, afterCursor)
            ),
            CursorNavigableResult.wrap
            (
                List.of(1, 2, 3),
                3,
                false,
                p->p == 1 ? beforeCursor.getKeys() : afterCursor.getKeys()
            )
        );
    }


}
