// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.extension.ValidatorExtension;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import jakarta.validation.Validator;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(ValidatorExtension.class)
public class UInt32EpochSecondsTemporalAccessorValidatorSimpleIntegrationTest
{

    private Validator validator;

    public static Stream<Arguments> validationArguments()
    {
        return Stream.of
        (
            Arguments.of(SC2Pulse.EPOCH_ODT, true),
            Arguments.of(SC2Pulse.UINT32_ODT_MAX, true),
            Arguments.of(null, true),

            Arguments.of(SC2Pulse.EPOCH_ODT.minusSeconds(1), false),
            Arguments.of(SC2Pulse.UINT32_ODT_MAX.plusSeconds(1), false),
            Arguments.of(LocalDateTime.now(), false)
        );
    }

    @MethodSource("validationArguments")
    @ParameterizedTest
    public void testValidation(TemporalAccessor temporalAccessor, boolean expectedResult)
    {
        assertEquals
        (
            validator.validate(new TemporalAccessorDto(temporalAccessor)).isEmpty(),
            expectedResult
        );
    }

    private record TemporalAccessorDto(@UInt32EpochSeconds TemporalAccessor temporalAccessor) {}

}
