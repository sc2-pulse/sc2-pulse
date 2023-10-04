// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import java.util.Optional;
import java.util.Set;

public interface BasicEntityOperations<T>
{

    Set<T> merge(Set<T> entities);

    Optional<T> find(T t);

}
