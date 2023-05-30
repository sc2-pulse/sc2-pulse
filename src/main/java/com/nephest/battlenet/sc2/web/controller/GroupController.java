// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.local.dao.ClanMemberEventDAO;
import com.nephest.battlenet.sc2.web.controller.group.CharacterGroup;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/group")
public class GroupController
{

    public static final int MAX_CHARACTERS = 100;
    public static final int MAX_CLANS = 10;

    public static final int CLAN_MEMBER_EVENT_PAGE_SIZE = 30;

    @Autowired
    private ClanMemberEventDAO clanMemberEventDAO;

    public static Optional<ResponseEntity<?>> areIdsInvalid
    (
        Set<Long> characterIds,
        Set<Integer> clanIds
    )
    {
        if(characterIds.isEmpty() && clanIds.isEmpty())
        {
            ResponseEntity<?> entity = ResponseEntity
                .badRequest()
                .body("At least one clanId or characterId is required");
            return Optional.of(entity);
        }

        if(characterIds.size() > MAX_CHARACTERS)
        {
            ResponseEntity<?> entity = ResponseEntity
                .badRequest()
                .body("Max size of characters exceeded: " + MAX_CHARACTERS);
            return Optional.of(entity);
        }

        if(clanIds.size() > MAX_CLANS)
        {
            ResponseEntity<?> entity = ResponseEntity
                .badRequest()
                .body("Max size of clans exceeded: " + MAX_CLANS);
            return Optional.of(entity);
        }

        return Optional.empty();
    }

    @GetMapping("/clan/history")
    public ResponseEntity<?> geClanMemberHistory
    (
        @CharacterGroup Set<Long> characterIds,
        @RequestParam(name = "createdCursor", required = false) OffsetDateTime createdCursor,
        @RequestParam(name = "characterIdCursor", required = false, defaultValue = Long.MAX_VALUE + "") Long characterIdCursor,
        @RequestParam(name = "limit", required = false, defaultValue = CLAN_MEMBER_EVENT_PAGE_SIZE + "") Integer limit
    )
    {
        OffsetDateTime cCursor = createdCursor != null ? createdCursor : OffsetDateTime.now();
            return WebServiceUtil.notFoundIfEmpty(clanMemberEventDAO.find
            (
                characterIds,
                cCursor,
                characterIdCursor,
                limit
            ));
    }

    @GetMapping
    public ResponseEntity<Set<Long>> getCharacterIds(@CharacterGroup Set<Long> characterIds)
    {
        return WebServiceUtil.notFoundIfEmpty(characterIds);
    }

}
