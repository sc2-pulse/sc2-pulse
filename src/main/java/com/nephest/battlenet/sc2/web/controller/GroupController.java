// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.inner.Group;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderClanMemberEventDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.web.controller.group.CharacterGroup;
import com.nephest.battlenet.sc2.web.controller.group.CharacterGroupArgumentResolver;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/group")
public class GroupController
{

    public static final int CLAN_MEMBER_EVENT_PAGE_SIZE = 30;
    public static final int CLAN_MEMBER_EVENT_PAGE_SIZE_MAX = 100;
    public static final int MATCH_PAGE_SIZE_MAX = 100;

    @Autowired
    private LadderCharacterDAO ladderCharacterDAO;

    @Autowired
    private ClanDAO clanDAO;

    @Autowired
    private LadderClanMemberEventDAO ladderClanMemberEventDAO;

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

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

        if(characterIds.size() > CharacterGroupArgumentResolver.CHARACTERS_MAX)
        {
            ResponseEntity<?> entity = ResponseEntity
                .badRequest()
                .body("Max size of characters exceeded: " + CharacterGroupArgumentResolver.CHARACTERS_MAX);
            return Optional.of(entity);
        }

        if(clanIds.size() > CharacterGroupArgumentResolver.CLANS_MAX)
        {
            ResponseEntity<?> entity = ResponseEntity
                .badRequest()
                .body("Max size of clans exceeded: " + CharacterGroupArgumentResolver.CLANS_MAX);
            return Optional.of(entity);
        }

        return Optional.empty();
    }

    @GetMapping
    public ResponseEntity<?> getGroup
    (
        @RequestParam(name = "characterId", required = false, defaultValue = "") Set<Long> characterIds,
        @RequestParam(name = "clanId", required = false, defaultValue = "") Set<Integer> clanIds
    )
    {
        return areIdsInvalid(characterIds, clanIds)
            .orElseGet(()->
            {
                List<LadderDistinctCharacter> characters = ladderCharacterDAO
                    .findDistinctCharactersByCharacterIds(characterIds.toArray(Long[]::new));
                List<Clan> clans = clanDAO.findByIds(clanIds.toArray(Integer[]::new));
                return characters.isEmpty() && clans.isEmpty()
                    ? ResponseEntity.notFound().build()
                    : ResponseEntity.ok(new Group(characters, clans));
            });
    }

    @GetMapping("/character/full") @CharacterGroup
    public ResponseEntity<List<LadderDistinctCharacter>> getFullPlayerCharacters(@CharacterGroup Set<Long> characterIds)
    {
        return WebServiceUtil.notFoundIfEmpty(ladderCharacterDAO
            .findDistinctCharactersByCharacterIds(characterIds.toArray(Long[]::new)));
    }

    @GetMapping("/clan/history")
    public ResponseEntity<?> geClanMemberHistory
    (
        @RequestParam(name = "characterId", required = false, defaultValue = "") Set<Long> characterIds,
        @RequestParam(name = "clanId", required = false, defaultValue = "") Set<Integer> clanIds,
        @RequestParam(name = "createdCursor", required = false) OffsetDateTime createdCursor,
        @RequestParam(name = "characterIdCursor", required = false, defaultValue = Long.MAX_VALUE + "") Long characterIdCursor,
        @RequestParam(name = "limit", required = false, defaultValue = CLAN_MEMBER_EVENT_PAGE_SIZE + "") Integer limit
    )
    {
        if(limit > CLAN_MEMBER_EVENT_PAGE_SIZE_MAX)
            return ResponseEntity.badRequest().body("Max page size exceeded: " + CLAN_MEMBER_EVENT_PAGE_SIZE_MAX);
        OffsetDateTime cCursor = createdCursor != null ? createdCursor : OffsetDateTime.now();
        return areIdsInvalid(characterIds, clanIds)
            .orElseGet(()->ResponseEntity.of(ladderClanMemberEventDAO.find
            (
                characterIds,
                clanIds,
                cCursor,
                characterIdCursor,
                limit
            )));
    }

    @GetMapping("/match") @CharacterGroup
    public ResponseEntity<?> getMatchHistory
    (
        @CharacterGroup Set<Long> characterIds,
        @RequestParam(name = "dateCursor", required = false) OffsetDateTime dateCursor,
        @RequestParam(name = "typeCursor", required = false, defaultValue = "_1V1") BaseMatch.MatchType typeCursor,
        @RequestParam(name = "mapCursor", required = false, defaultValue = "0") int mapCursor,
        @RequestParam(name = "regionCursor", required = false, defaultValue = "US") Region regionCursor,
        @RequestParam(name = "type", required = false, defaultValue = "") BaseMatch.MatchType[] types,
        @RequestParam(name = "limit", required = false, defaultValue = "20") int limit
    )
    {
        if(limit > MATCH_PAGE_SIZE_MAX) return ResponseEntity
            .badRequest()
            .body("Max limit: " + MATCH_PAGE_SIZE_MAX);

        dateCursor = dateCursor != null ? dateCursor : OffsetDateTime.now();
        return WebServiceUtil.notFoundIfEmpty
        (
            ladderMatchDAO.findMatchesByCharacterIds
            (
                characterIds,
                dateCursor, typeCursor, mapCursor, regionCursor,
                0, 1, limit,
                types
            ).getResult()
        );
    }

    @GetMapping("/flat") @CharacterGroup
    public ResponseEntity<Set<Long>> getCharacterIds(@CharacterGroup Set<Long> characterIds)
    {
        return WebServiceUtil.notFoundIfEmpty(characterIds);
    }

}
