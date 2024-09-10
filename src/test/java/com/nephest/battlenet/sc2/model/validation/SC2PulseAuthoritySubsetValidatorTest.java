// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import org.junit.jupiter.api.Test;

public class SC2PulseAuthoritySubsetValidatorTest
{

    @Test
    @SC2PulseAuthoritySubset(anyOf = {SC2PulseAuthority.MODERATOR, SC2PulseAuthority.REVEALER})
    public void testAnyOfValidation()
    throws NoSuchMethodException
    {
        SC2PulseAuthoritySubsetValidator validator = new SC2PulseAuthoritySubsetValidator();
        SC2PulseAuthoritySubset annotation = getClass()
            .getMethod("testAnyOfValidation")
            .getAnnotation(SC2PulseAuthoritySubset.class);
        validator.initialize(annotation);
        assertTrue(validator.isValid(SC2PulseAuthority.MODERATOR, null));
        assertTrue(validator.isValid(SC2PulseAuthority.REVEALER, null));
        assertFalse(validator.isValid(SC2PulseAuthority.ADMIN, null));
        assertFalse(validator.isValid(null, null));
    }

}
