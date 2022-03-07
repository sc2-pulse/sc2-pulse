// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import java.io.IOException;
import java.util.function.BiConsumer;

@FunctionalInterface
public interface ThrowingBiConsumer<T, U> extends BiConsumer<T, U>
{

    @Override
    default void accept(final T elem, U elem2)
    {
        try
        {
            acceptThrows(elem, elem2);
        }
        catch (final Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    void acceptThrows(T elem, U elem2) throws IOException;

}
