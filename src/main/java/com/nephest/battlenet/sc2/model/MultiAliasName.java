// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

import java.util.stream.Stream;

public interface MultiAliasName
extends Named, MultiAlias
{

    default Stream<String> getAllNames()
    {
        return Stream.concat
        (
            Stream.of(getName()),
            getAdditionalNames().stream()
        );
    }

}
