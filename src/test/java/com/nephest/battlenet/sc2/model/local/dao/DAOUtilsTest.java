// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DAOUtilsTest
{

    @Test
    public void testToCollisionFreeSet()
    {
        Assertions.assertEquals(Set.of(1, 2), DAOUtils.toCollisionFreeSet(List.of(1, 2)));
    }

    @Test
    public void whenThereAreCollisions_thenThrowException()
    {
        Assertions.assertThrows
        (
            IllegalArgumentException.class,
            ()->DAOUtils.toCollisionFreeSet(List.of(1, 2, 1)),
            "Collision detected"
        );
    }

}
