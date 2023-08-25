// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class VarTest
{

    @Mock
    private VarDAO varDAO;

    @Test
    public void whenExceptionIsThrownWhenTryLoad_thenSilentlyIgnore()
    {
        Var<Object> var = new Var<>(null, null, null, null, false);

        assertThrows(RuntimeException.class, var::load);
        assertFalse(var.tryLoad());
    }

    @Test
    public void whenTryLoadSucceeds_thenReturnTrue()
    {
        when(varDAO.find("test")).thenReturn(Optional.of("value123"));
        Var<String> var = new Var<>
        (
            varDAO,
            "test",
            Function.identity(), Function.identity(),
            false
        );
        assertTrue(var.tryLoad());
        assertEquals("value123", var.getValue());
    }

}
