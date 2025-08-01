// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import jakarta.annotation.Nullable;

public record CursorNavigation(@Nullable String backward, @Nullable String forward)
{}
