// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.extension.ValidatorExtension;
import com.nephest.battlenet.sc2.model.SortingOrder;
import com.nephest.battlenet.sc2.model.web.SortParameter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ValidatorExtension.class)
class SortParameterAllowedFieldValidatorSimpleIntegrationTest
{

    private Validator validator;

    @Test
    void shouldAllowValidField()
    {
        TestDto dto = new TestDto(new SortParameter("name", SortingOrder.ASC));
        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void shouldRejectInvalidField()
    {
        TestDto dto = new TestDto(new SortParameter("salary", SortingOrder.DESC));
        Set<ConstraintViolation<TestDto>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertEquals
        (
            "Invalid field 'salary'. Allowed fields are: name, age, email",
            violations.iterator().next().getMessage()
        );
    }

    @Test
    void shouldAllowNullValue()
    {
        TestDto dto = new TestDto(null);
        assertTrue(validator.validate(dto).isEmpty());
    }

    @Test
    void shouldAllowAnotherValidField()
    {
        TestDto dto = new TestDto(new SortParameter("age", SortingOrder.DESC));
        assertTrue(validator.validate(dto).isEmpty());
    }

    private record TestDto(@AllowedField({"name", "age", "email"}) SortParameter sort) {}

}
