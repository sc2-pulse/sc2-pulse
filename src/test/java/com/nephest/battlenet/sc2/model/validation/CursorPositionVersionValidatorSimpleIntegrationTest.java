// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.extension.ValidatorExtension;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.navigation.NavigationDirection;
import com.nephest.battlenet.sc2.model.navigation.Position;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@ExtendWith(ValidatorExtension.class)
public class CursorPositionVersionValidatorSimpleIntegrationTest
{

    private Validator validator;

    private static final long VALID_VERSION = 2L;

    @ValueSource(longs = {1L, 2L})
    @ParameterizedTest
    public void testVerification(long version)
    {
        Cursor cursor = new Cursor
        (
            new Position(version, List.of("5", "6")),
            NavigationDirection.FORWARD
        );
        assertEquals
        (
            version == VALID_VERSION,
            validator.validate(new TestDto(cursor)).isEmpty()
        );
    }

    @Test
    void shouldAllowNullValue()
    {
        assertTrue(validator.validate(new TestDto(null)).isEmpty());
    }

    private record TestDto(@Version(VALID_VERSION) Cursor cursor) {}

}
