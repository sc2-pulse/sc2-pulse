// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.extension.ValidatorExtension;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.data.domain.Sort;

@ExtendWith(ValidatorExtension.class)
class AllowedFieldValidatorsSimpleIntegrationTest
{

    private Validator validator;

    public static Stream<Function<String, Object>> constructors()
    {
        return Stream.of
        (
            field->new SortDto
            (
                field == null
                    ? Sort.unsorted()
                    : Sort.by(Sort.Direction.DESC, field)
            ),
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
            "Invalid fields",
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

    @Test
    void testTraversable()
    {
        Sort sort = Sort.by("name", "age");
        assertTrue(validator.validate(new TraversableSortDto(sort)).isEmpty());
        assertEquals
        (
            "Field traversal is forbidden",
            validator.validate(new SortDto(sort)).iterator().next().getMessage()
        );
    }

    @Test
    void shouldRejectInvalidSortFieldAfterValidField()
    {
        Sort sort = Sort.by("name", "salary");
        assertEquals
        (
            "Invalid fields",
            validator.validate(new TraversableSortDto(sort)).iterator().next().getMessage()
        );
    }

    @Test
    void shouldAcceptUnsortedValue()
    {
        assertTrue(validator.validate(new SortDto(Sort.unsorted())).isEmpty());
    }

    @Test
    public void testDuplicates()
    {
        Sort duplicateSort = Sort.by
        (
            Sort.Order.asc("age"),
            Sort.Order.desc("email"),
            Sort.Order.desc("age")
        );
        assertTrue
        (
            validator.validate(new DuplicateSortDto(duplicateSort))
                .isEmpty()
        );
        assertEquals
        (
            "Duplicate fields are forbidden",
            validator.validate(new TraversableSortDto(duplicateSort)).iterator().next()
                .getMessage()
        );
    }

    private record SortDto(@AllowedField({"name", "age", "email"}) Sort sort) {}
    private record TraversableSortDto
    (
        @AllowedField(value = {"name", "age", "email"}, allowTraversal = true)
        Sort sort
    ) {}
    private record DuplicateSortDto
    (
        @AllowedField
        (
            value = {"name", "age", "email"},
            allowTraversal = true,
            allowDuplicates = true
        )
        Sort sort
    ) {}
    private record StringDto(@AllowedField({"name", "age", "email"}) String sort) {}

}
