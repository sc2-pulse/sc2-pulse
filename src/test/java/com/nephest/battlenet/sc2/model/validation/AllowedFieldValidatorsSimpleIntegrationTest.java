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
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@ExtendWith(ValidatorExtension.class)
class AllowedFieldValidatorsSimpleIntegrationTest
{

    private Validator validator;

    public static Stream<Function<String, Object>> constructors()
    {
        return Stream.of
        (
            field->new SortDto(new SortParameter(field, SortingOrder.DESC)),
            StringDto::new
        );
    }

    @MethodSource("constructors")
    @ParameterizedTest
    void shouldAllowValidField(Function<String, Object> constructor)
    {
        Object dto = constructor.apply("name");
        assertTrue(validator.validate(dto).isEmpty());
    }

    @MethodSource("constructors")
    @ParameterizedTest
    void shouldRejectInvalidField(Function<String, Object> constructor)
    {
        Object dto = constructor.apply("salary");
        Set<ConstraintViolation<Object>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertEquals
        (
            "Invalid field 'salary'. Allowed fields are: name, age, email",
            violations.iterator().next().getMessage()
        );
    }

    @MethodSource("constructors")
    @ParameterizedTest
    void shouldAllowNullValue(Function<String, Object> constructor)
    {
        Object dto = constructor.apply(null);
        assertTrue(validator.validate(dto).isEmpty());
    }

    @MethodSource("constructors")
    @ParameterizedTest
    void shouldAllowAnotherValidField(Function<String, Object> constructor)
    {
        Object dto = constructor.apply("age");
        assertTrue(validator.validate(dto).isEmpty());
    }

    private record SortDto(@AllowedField({"name", "age", "email"}) SortParameter sort) {}
    private record StringDto(@AllowedField({"name", "age", "email"}) String sort) {}

}
