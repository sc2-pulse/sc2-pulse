// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterStats;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummary;
import com.nephest.battlenet.sc2.model.local.inner.PlayerCharacterSummaryDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.model.local.ladder.common.CommonCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderPlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderTeamStateDAO;
import com.nephest.battlenet.sc2.web.service.PlayerCharacterReportService;
import com.nephest.battlenet.sc2.web.service.SearchService;
import io.swagger.v3.oas.annotations.Hidden;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/character")
public class CharacterController
{

    public static final int SUMMARY_DEPTH_MAX = 120;
    public static final int SUMMARY_IDS_MAX = 50;
    public static final int PLAYER_CHARACTERS_MAX = 100;
    public static final int SEARCH_SUGGESTIONS_SIZE = 10;

    @Autowired
    private LadderSearchDAO ladderSearch;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private LadderCharacterDAO ladderCharacterDAO;

    @Autowired
    private PlayerCharacterStatsDAO playerCharacterStatsDAO;

    @Autowired
    private LadderPlayerCharacterStatsDAO ladderPlayerCharacterStatsDAO;

    @Autowired
    private PlayerCharacterSummaryDAO playerCharacterSummaryDAO;

    @Autowired
    private LadderProPlayerDAO ladderProPlayerDAO;

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

    @Autowired
    private LadderTeamStateDAO ladderTeamStateDAO;

    @Autowired
    private DiscordUserDAO discordUserDAO;

    @Autowired
    private PlayerCharacterReportService reportService;

    @Autowired
    private SearchService searchService;

    @Hidden
    @GetMapping("/search/{term}")
    public List<LadderDistinctCharacter> getCharacterTeamsLegacy(@PathVariable("term") String term)
    {
        return ladderCharacterDAO.findDistinctCharacters(term);
    }

    @GetMapping("/search")
    public List<LadderDistinctCharacter> getCharacterTeams(@RequestParam("term") String term)
    {
        return ladderCharacterDAO.findDistinctCharacters(term);
    }

    @Hidden
    @GetMapping("/search/{term}/suggestions")
    public List<String> suggestLegacy(@PathVariable("term") String term)
    {
        return searchService.suggestIfQuick(term, SEARCH_SUGGESTIONS_SIZE);
    }

    @GetMapping("/search/suggestions")
    public List<String> suggest(@RequestParam("term") String term)
    {
        return searchService.suggestIfQuick(term, SEARCH_SUGGESTIONS_SIZE);
    }

    @GetMapping("/{ids}")
    public ResponseEntity<List<PlayerCharacter>> getPlayerCharacters(@PathVariable("ids") Long[] ids)
    {
        if(ids.length > PLAYER_CHARACTERS_MAX)
            return ResponseEntity.badRequest().build();

        return ResponseEntity.of(Optional.of(playerCharacterDAO.find(ids)));
    }

    @Hidden
    @GetMapping("/{id}/common/{types}")
    public CommonCharacter getCommonCharacterLegacy
    (
        @PathVariable("id") long id,
        @PathVariable(name = "types") BaseMatch.MatchType[] types
    )
    {
        if(types == null) types = new BaseMatch.MatchType[0];
        List<LadderDistinctCharacter> linkedCharacters =
            ladderCharacterDAO.findLinkedDistinctCharactersByCharacterId(id);
        LadderDistinctCharacter currentCharacter = linkedCharacters.isEmpty()
            ? null
            : linkedCharacters.stream()
                .filter(c->c.getMembers().getCharacter().getId() == id)
                .findAny()
                .orElseThrow();
        return new CommonCharacter
        (
            ladderSearch.findCharacterTeams(id),
            linkedCharacters,
            ladderPlayerCharacterStatsDAO.findGlobalList(id),
            ladderProPlayerDAO.getProPlayerByCharacterId(id),
            currentCharacter != null
                ? discordUserDAO
                    .findByAccountId(currentCharacter.getMembers().getAccount().getId(), true)
                    .orElse(null)
                : null,
            ladderMatchDAO.findMatchesByCharacterId(
                id, OffsetDateTime.now(), BaseMatch.MatchType._1V1, 0, 0, 1, types).getResult(),
            ladderTeamStateDAO.find(id),
            reportService.findReportsByCharacterId(id)
        );
    }

    @GetMapping("/{id}/common")
    public CommonCharacter getCommonCharacter
    (
        @PathVariable("id") long id,
        @RequestParam(name = "matchType", required = false) BaseMatch.MatchType[] types,
        @RequestParam(name = "mmrHistoryDepth", required = false) Integer depth
    )
    {
        if(types == null) types = new BaseMatch.MatchType[0];
        OffsetDateTime from = depth == null ? null : OffsetDateTime.now().minusDays(depth);
        List<LadderDistinctCharacter> linkedCharacters =
            ladderCharacterDAO.findLinkedDistinctCharactersByCharacterId(id);
        LadderDistinctCharacter currentCharacter = linkedCharacters.isEmpty()
            ? null
            : linkedCharacters.stream()
                .filter(c->c.getMembers().getCharacter().getId() == id)
                .findAny()
                .orElseThrow();
        return new CommonCharacter
        (
            ladderSearch.findCharacterTeams(id),
            linkedCharacters,
            ladderPlayerCharacterStatsDAO.findGlobalList(id),
            ladderProPlayerDAO.getProPlayerByCharacterId(id),
            currentCharacter != null
                ? discordUserDAO
                    .findByAccountId(currentCharacter.getMembers().getAccount().getId(), true)
                    .orElse(null)
                : null,
            ladderMatchDAO.findMatchesByCharacterId(
                id, OffsetDateTime.now(), BaseMatch.MatchType._1V1, 0, 0, 1, types).getResult(),
            ladderTeamStateDAO.find(id, from),
            reportService.findReportsByCharacterId(id)
        );
    }

    @GetMapping
    ({
        "/{id}/matches/{dateAnchor}/{typeAnchor}/{mapAnchor}/{page}/{pageDiff}",
        "/{id}/matches/{dateAnchor}/{typeAnchor}/{mapAnchor}/{page}/{pageDiff}/{types}"
    })
    public PagedSearchResult<List<LadderMatch>> getCharacterMatches
    (
        @PathVariable("id") long id,
        @PathVariable("dateAnchor") String dateAnchor,
        @PathVariable("typeAnchor") BaseMatch.MatchType typeAnchor,
        @PathVariable("mapAnchor") int mapAnchor,
        @PathVariable("page") int page,
        @PathVariable("pageDiff") int pageDiff,
        @PathVariable(name = "types", required = false) BaseMatch.MatchType[] types
    )
    {
        if(Math.abs(pageDiff) > 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page count is too big");
        if(types == null) types = new BaseMatch.MatchType[0];

        return ladderMatchDAO.findMatchesByCharacterId
        (
            id,
            OffsetDateTime.parse(dateAnchor),
            typeAnchor,
            mapAnchor,
            page,
            pageDiff,
            types
        );
    }

    @GetMapping("/{id}/teams")
    public List<LadderTeam> getCharacterTeams
    (
        @PathVariable("id") long id
    )
    {
        return ladderSearch.findCharacterTeams(id);
    }

    @GetMapping("/{id}/stats")
    public List<PlayerCharacterStats> getCharacterStats
    (
        @PathVariable("id") long id
    )
    {
        return playerCharacterStatsDAO.findGlobalList(id);
    }

    @GetMapping
    ({
        "/{ids}/summary/1v1/{depthDays}",
        "/{ids}/summary/1v1/{depthDays}/{races}"
    })
    public List<PlayerCharacterSummary> getCharacterSummary
    (
        @PathVariable("ids") Long[] ids,
        @PathVariable("depthDays") int depth,
        @PathVariable(name = "races", required = false) Race[] races
    )
    {
        if(ids.length > SUMMARY_IDS_MAX)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Id list is too long, max: " + SUMMARY_IDS_MAX);
        if(depth > SUMMARY_DEPTH_MAX)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Depth is too big, max: " + SUMMARY_DEPTH_MAX);
        if(races == null) races = Race.EMPTY_RACE_ARRAY;

        return playerCharacterSummaryDAO.find(ids, OffsetDateTime.now().minusDays(depth), races);
    }

}
