// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.navigation;

import jakarta.validation.constraints.NotNull;

public record Cursor
(
    @NotNull Position position,
    @NotNull NavigationDirection direction
)
{}
