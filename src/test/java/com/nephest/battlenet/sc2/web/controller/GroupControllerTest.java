// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.web.controller.group.CharacterGroupArgumentResolver;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class GroupControllerTest
{

    private static void verifyBadRequest(ResponseEntity<?> entity, String body)
    {
        assertEquals(HttpStatus.BAD_REQUEST, entity.getStatusCode());
        assertEquals(body, entity.getBody());
    }

    @Test
    public void whenIdsAreEmpty_thenBadRequest()
    {
        verifyBadRequest
        (
            GroupController.areIdsInvalid(Set.of(), Set.of()).orElseThrow(),
            "At least one clanId or characterId is required"
        );
    }

    @Test
    public void whenCharacterSizeIsExceeded_thenBadRequest()
    {
        Set<Long> bigCharacterSet = LongStream
            .range(0, CharacterGroupArgumentResolver.CHARACTERS_MAX + 1)
            .boxed()
            .collect(Collectors.toSet());
        verifyBadRequest
        (
            GroupController.areIdsInvalid(bigCharacterSet, Set.of()).orElseThrow(),
            "Max size of characters exceeded: " + CharacterGroupArgumentResolver.CHARACTERS_MAX
        );
    }

    @Test
    public void whenClanSizeIsExceeded_thenBadRequest()
    {
        Set<Integer> bigClanSet = IntStream
            .range(0, CharacterGroupArgumentResolver.CLANS_MAX + 1)
            .boxed()
            .collect(Collectors.toSet());
        verifyBadRequest
        (
            GroupController.areIdsInvalid(Set.of(), bigClanSet).orElseThrow(),
            "Max size of clans exceeded: " + CharacterGroupArgumentResolver.CLANS_MAX
        );
    }

    @Test
    public void whenValidIds_thenReturnEmptyOptional()
    {
        Set<Long> characterSet = LongStream
            .range(0, CharacterGroupArgumentResolver.CHARACTERS_MAX)
            .boxed()
            .collect(Collectors.toSet());
        Set<Integer> clanSet = IntStream
            .range(0, CharacterGroupArgumentResolver.CLANS_MAX)
            .boxed()
            .collect(Collectors.toSet());
        assertTrue(GroupController.areIdsInvalid(characterSet, clanSet).isEmpty());
    }

}
