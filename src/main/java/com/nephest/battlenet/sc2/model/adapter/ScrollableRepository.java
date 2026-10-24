// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.adapter;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.Window;

public interface ScrollableRepository
{

    <T> Window<T> findAllBy
    (
        Sort sort,
        ScrollPosition position,
        Limit limit,
        Class<T> type
    );

}
