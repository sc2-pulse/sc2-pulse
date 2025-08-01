// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import com.nephest.battlenet.sc2.model.CursorNavigation;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

public record CursorNavigableResult<T>(@Nullable T result, @NotNull CursorNavigation navigation)
{
}
