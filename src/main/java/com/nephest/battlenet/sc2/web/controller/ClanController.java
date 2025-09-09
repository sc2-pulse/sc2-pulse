// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static com.nephest.battlenet.sc2.web.controller.group.CharacterGroupArgumentResolver.areIdsInvalid;

import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderClanMemberEventDAO;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.model.validation.AllowedField;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import com.nephest.battlenet.sc2.model.validation.Version;
import com.nephest.battlenet.sc2.model.web.SortParameter;
import com.nephest.battlenet.sc2.web.controller.group.CharacterGroupArgumentResolver;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ClanController
{

    public static final int MIN_ADDITIONAL_CURSOR_FILTER = 0;
    public static final int MAX_ADDITIONAL_CURSOR_FILTER = 9999;
    public static final String MIN_ADDITIONAL_CURSOR_FILTER_STR = "0";
    public static final String MAX_ADDITIONAL_CURSOR_FILTER_STR = "9999";
    public static final int CLAN_MEMBER_EVENT_PAGE_SIZE = 30;
    public static final int CLAN_MEMBER_EVENT_PAGE_SIZE_MAX = 100;

    @Autowired
    private ClanDAO clanDAO;

    @Autowired
    private LadderClanMemberEventDAO ladderClanMemberEventDAO;

    @Autowired
    private CharacterGroupArgumentResolver resolver;

    @GetMapping("/clans")
    public CursorNavigableResult<List<Clan>> getByCursor
    (
        @Version(ClanDAO.CURSOR_POSITION_VERSION) Cursor cursor,
        @RequestParam(value = "sort", defaultValue = "-activeMembers")
        @AllowedField({"members", "activeMembers", "gamesPerActiveMemberPerDay", "avgRating"})
        SortParameter sort,
        @RequestParam(name="minActiveMembers", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER_STR) int minActiveMembers,
        @RequestParam(name="maxActiveMembers", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER_STR) int maxActiveMembers,
        @RequestParam(name="minGamesPerActiveMemberPerDay", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER_STR) double minGamesPerActiveMemberPerDay,
        @RequestParam(name="maxGamesPerActiveMemberPerDay", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER_STR) double maxGamesPerActiveMemberPerDay,
        @RequestParam(name="minAvgRating", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER_STR) int minAvgRating,
        @RequestParam(name="maxAvgRating", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER_STR) int maxAvgRating,
        @RequestParam(name="region", required = false) Region region
    )
    {
        return clanDAO.findByCursor
        (
            minActiveMembers, maxActiveMembers,
            minGamesPerActiveMemberPerDay, maxGamesPerActiveMemberPerDay,
            minAvgRating, maxAvgRating,
            region,
            sort, cursor
        );
    }

    @GetMapping(value = "/clans", params = "query")
    public List<Clan> findByTextQuery(@RequestParam("query") String query)
    {
        return clanDAO.findByTagOrName(query);
    }

    @GetMapping("/clan-histories")
    public ResponseEntity<?> getClanHistories
    (
        @RequestParam(name = "characterId", required = false, defaultValue = "")
        Set<Long> characterIds,
        @RequestParam(name = "clanId", required = false, defaultValue = "") Set<Integer> clanIds,
        @RequestParam(name = "proPlayerId", required = false, defaultValue = "") Set<Long> proPlayerIds,
        @RequestParam(name = "accountId", required = false, defaultValue = "") Set<Long> accountIds,
        @RequestParam(name = "toonHandle", required = false, defaultValue = "") Set<PlayerCharacterNaturalId> toonHandles,
        @RequestParam(name = "createdCursor", required = false) OffsetDateTime createdCursor,
        @RequestParam(name = "characterIdCursor", required = false, defaultValue = Long.MAX_VALUE + "") Long characterIdCursor,
        @RequestParam(name = "limit", required = false, defaultValue = CLAN_MEMBER_EVENT_PAGE_SIZE + "") Integer limit
    )
    {
        if(limit > CLAN_MEMBER_EVENT_PAGE_SIZE_MAX)
            return ResponseEntity.badRequest().body("Max page size exceeded: " + CLAN_MEMBER_EVENT_PAGE_SIZE_MAX);
        OffsetDateTime cCursor = createdCursor != null ? createdCursor : SC2Pulse.offsetDateTime();
        return areIdsInvalid(characterIds, clanIds, proPlayerIds, accountIds, toonHandles)
            .orElseGet(()->ResponseEntity.of(ladderClanMemberEventDAO.find
            (
                resolver.resolve(characterIds, Set.of(), proPlayerIds, accountIds, toonHandles),
                clanIds,
                cCursor,
                characterIdCursor,
                limit
            )));
    }

}
