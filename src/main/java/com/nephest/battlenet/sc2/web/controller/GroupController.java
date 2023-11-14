// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static com.nephest.battlenet.sc2.web.controller.group.CharacterGroupArgumentResolver.areIdsInvalid;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.inner.Group;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderClanMemberEventDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.web.controller.group.CharacterGroup;
import com.nephest.battlenet.sc2.web.controller.group.CharacterGroupArgumentResolver;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/group")
public class GroupController
{

    public static final int CLAN_MEMBER_EVENT_PAGE_SIZE = 30;
    public static final int CLAN_MEMBER_EVENT_PAGE_SIZE_MAX = 100;
    public static final int MATCH_PAGE_SIZE_MAX = 100;
    public static final int TEAM_LIMIT = CharacterGroupArgumentResolver.CHARACTERS_MAX * 5;
    public static final int SINGLE_CHARACTER_TEAM_LIMIT = 3000;

    @Autowired
    private LadderCharacterDAO ladderCharacterDAO;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private LadderProPlayerDAO ladderProPlayerDAO;

    @Autowired
    private ClanDAO clanDAO;

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private LadderClanMemberEventDAO ladderClanMemberEventDAO;

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

    @Autowired
    private CharacterGroupArgumentResolver resolver;

    @GetMapping
    public ResponseEntity<?> getGroup
    (
        @RequestParam(name = "characterId", required = false, defaultValue = "") Set<Long> characterIds,
        @RequestParam(name = "clanId", required = false, defaultValue = "") Set<Integer> clanIds,
        @RequestParam(name = "proPlayerId", required = false, defaultValue = "") Set<Long> proPlayerIds,
        @RequestParam(name = "accountId", required = false, defaultValue = "") Set<Long> accountIds
    )
    {
        return areIdsInvalid(characterIds, clanIds, proPlayerIds, accountIds)
            .orElseGet(()->
            {
                List<LadderDistinctCharacter> characters = ladderCharacterDAO
                    .findDistinctCharactersByCharacterIds(characterIds);
                List<Clan> clans = clanDAO.findByIds(clanIds);
                List<LadderProPlayer> proPlayers = ladderProPlayerDAO
                    .findByIds(proPlayerIds);
                List<Account> accounts = accountDAO.findByIds(accountIds);
                accounts.sort(Account.NATURAL_ID_COMPARATOR);
                return characters.isEmpty()
                    && clans.isEmpty()
                    && proPlayerIds.isEmpty()
                    && accounts.isEmpty()
                        ? ResponseEntity.notFound().build()
                        : ResponseEntity.ok(new Group(characters, clans, proPlayers, accounts));
            });
    }

    @GetMapping("/character/full") @CharacterGroup
    public ResponseEntity<List<LadderDistinctCharacter>> getFullPlayerCharacters(@CharacterGroup Set<Long> characterIds)
    {
        return WebServiceUtil.notFoundIfEmpty(ladderCharacterDAO
            .findDistinctCharactersByCharacterIds(characterIds));
    }

    @GetMapping("/clan/history")
    public ResponseEntity<?> geClanMemberHistory
    (
        @RequestParam(name = "characterId", required = false, defaultValue = "") Set<Long> characterIds,
        @RequestParam(name = "clanId", required = false, defaultValue = "") Set<Integer> clanIds,
        @RequestParam(name = "proPlayerId", required = false, defaultValue = "") Set<Long> proPlayerIds,
        @RequestParam(name = "accountId", required = false, defaultValue = "") Set<Long> accountIds,
        @RequestParam(name = "createdCursor", required = false) OffsetDateTime createdCursor,
        @RequestParam(name = "characterIdCursor", required = false, defaultValue = Long.MAX_VALUE + "") Long characterIdCursor,
        @RequestParam(name = "limit", required = false, defaultValue = CLAN_MEMBER_EVENT_PAGE_SIZE + "") Integer limit
    )
    {
        if(limit > CLAN_MEMBER_EVENT_PAGE_SIZE_MAX)
            return ResponseEntity.badRequest().body("Max page size exceeded: " + CLAN_MEMBER_EVENT_PAGE_SIZE_MAX);
        OffsetDateTime cCursor = createdCursor != null ? createdCursor : OffsetDateTime.now();
        return areIdsInvalid(characterIds, clanIds, proPlayerIds, accountIds)
            .orElseGet(()->ResponseEntity.of(ladderClanMemberEventDAO.find
            (
                resolver.resolve(characterIds, Set.of(), proPlayerIds, accountIds),
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

    @Operation
    (
        description = "If multiple characters(flattened) are used, then you must supply 1 season "
            + "and  1 queue filter. Max limit: " + TEAM_LIMIT + " for multi-character, "
            + SINGLE_CHARACTER_TEAM_LIMIT + " for single character."
    )
    @GetMapping("/team") @CharacterGroup
    public ResponseEntity<?> getTeams
    (
        @CharacterGroup Set<Long> characterIds,
        @RequestParam(name = "season", required = false, defaultValue = "") Set<Integer> seasons,
        @RequestParam(name = "queue", required = false, defaultValue = "") Set<QueueType> queues,
        @RequestParam(name = "limit", required = false, defaultValue = TEAM_LIMIT + "") Integer limit
    )
    {
        if
        (
            limit < 1
                || (characterIds.size() == 1 && limit > SINGLE_CHARACTER_TEAM_LIMIT)
                || (characterIds.size() > 1 && limit > TEAM_LIMIT)
        ) return ResponseEntity.badRequest().body
            (
                "Limit should be in 1-" + TEAM_LIMIT + " range, "
                + "1-" + SINGLE_CHARACTER_TEAM_LIMIT + " for single character."
            );
        if(characterIds.size() > 1 && (seasons.size() != 1 || queues.size() != 1))
            return ResponseEntity.badRequest()
                .body("1 season and 1 queue are required for multi-character request");

        return WebServiceUtil.notFoundIfEmpty(ladderSearchDAO
            .findCharacterTeams(characterIds, seasons, queues, limit));
    }

    @GetMapping("/flat") @CharacterGroup
    public ResponseEntity<Set<Long>> getCharacterIds(@CharacterGroup Set<Long> characterIds)
    {
        return WebServiceUtil.notFoundIfEmpty(characterIds);
    }

}
