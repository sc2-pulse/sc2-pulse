// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static com.nephest.battlenet.sc2.web.controller.group.CharacterGroupArgumentResolver.areIdsInvalid;

import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberEventDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderClanMemberEvents;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderClanMemberEventDAO;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.validation.AllowedField;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import com.nephest.battlenet.sc2.model.validation.Version;
import com.nephest.battlenet.sc2.model.web.SortParameter;
import com.nephest.battlenet.sc2.web.controller.group.CharacterGroupArgumentResolver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
        @RequestParam(name="region", required = false) Region region,
        @RequestParam(name="activeMembersMin", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER_STR)
        @Min(0)
        int minActiveMembers,
        @RequestParam(name="activeMembersMax", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER_STR)
        @Min(0)
        int maxActiveMembers,
        @RequestParam(name="gamesPerActiveMemberPerDayMin", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER_STR)
        @Min(0)
        double minGamesPerActiveMemberPerDay,
        @RequestParam(name="gamesPerActiveMemberPerDayMax", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER_STR)
        @Min(0)
        double maxGamesPerActiveMemberPerDay,
        @RequestParam(name="avgRatingMin", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER_STR)
        @Min(0)
        int minAvgRating,
        @RequestParam(name="avgRatingMax", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER_STR)
        @Min(0)
        int maxAvgRating,
        @RequestParam(value = "sort", defaultValue = "-activeMembers")
        @AllowedField({"members", "activeMembers", "gamesPerActiveMemberPerDay", "avgRating"})
        SortParameter sort,
        @Version(ClanDAO.CURSOR_POSITION_VERSION) Cursor cursor
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
        @Version(ClanMemberEventDAO.CURSOR_POSITION_VERSION) Cursor cursor,
        @RequestParam(name = "limit", required = false, defaultValue = CLAN_MEMBER_EVENT_PAGE_SIZE + "")
        @Min(1) @Max(CLAN_MEMBER_EVENT_PAGE_SIZE_MAX)
        Integer limit
    )
    {
        return areIdsInvalid(characterIds, clanIds, proPlayerIds, accountIds, toonHandles)
            .orElseGet(()->{
                CursorNavigableResult<LadderClanMemberEvents> evts = ladderClanMemberEventDAO.find
                (
                    resolver.resolve(characterIds, Set.of(), proPlayerIds, accountIds, toonHandles),
                    clanIds,
                    cursor,
                    limit
                );
                return evts.result() == null
                    ? ResponseEntity.notFound().build()
                    : ResponseEntity.ok().body(evts);
            });
    }

}
