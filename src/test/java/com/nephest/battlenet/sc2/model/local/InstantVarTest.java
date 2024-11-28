// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.time.Instant;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class InstantVarTest
{

    private static final String KEY = "test.key";

    @Mock
    private VarDAO varDAO;

    private InstantVar instantVar;

    @BeforeEach
    public void beforeEach()
    {
        instantVar = new InstantVar(varDAO, KEY, false);
    }

    public static Stream<Arguments> testConversion()
    {
        Instant minMilliInstant = Instant.ofEpochMilli(Long.MIN_VALUE);
        return Stream.of
        (
            Arguments.of
            (
                minMilliInstant,
                String.valueOf(minMilliInstant.toEpochMilli())
            ),
            Arguments.of
            (
                minMilliInstant.minusSeconds(1),
                "s" + minMilliInstant.minusSeconds(1)
            )
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testConversion(Instant instant, String text)
    {
        when(varDAO.find(KEY)).thenReturn(Optional.of(text));
        assertEquals(instant, instantVar.load());

        instantVar.save();
        verify(varDAO).merge(KEY, text);
    }

}
