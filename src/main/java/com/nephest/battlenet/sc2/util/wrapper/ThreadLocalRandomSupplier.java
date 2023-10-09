// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.util.wrapper;

import java.util.Random;
import java.util.function.Supplier;

public interface ThreadLocalRandomSupplier
extends Supplier<Random>
{
}
