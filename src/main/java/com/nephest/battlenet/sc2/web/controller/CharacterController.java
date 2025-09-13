// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static com.nephest.battlenet.sc2.web.service.SearchService.ID_SEARCH_MAX_SEASONS;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.IdField;
import com.nephest.battlenet.sc2.model.IdProjection;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderPlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.navigation.Cursor;
import com.nephest.battlenet.sc2.model.validation.AllowedField;
import com.nephest.battlenet.sc2.model.validation.NotFakeSc2Name;
import com.nephest.battlenet.sc2.model.validation.Version;
import com.nephest.battlenet.sc2.web.controller.group.CharacterGroup;
import com.nephest.battlenet.sc2.web.controller.group.CharacterGroupArgumentResolver;
import com.nephest.battlenet.sc2.web.service.SearchService;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import com.nephest.battlenet.sc2.web.service.external.ExternalLinkResolveResult;
import com.nephest.battlenet.sc2.web.service.external.ExternalPlayerCharacterLinkService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CharacterController
{

    public static final int PLAYER_CHARACTERS_MAX = 100;
    public static final int SEARCH_SUGGESTIONS_SIZE = 10;
    public static final int MATCH_PAGE_SIZE_MAX = 100;
    public static final int TEAM_LIMIT = CharacterGroupArgumentResolver.CHARACTERS_MAX * 5;
    public static final int SINGLE_CHARACTER_TEAM_LIMIT = 3000;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private LadderCharacterDAO ladderCharacterDAO;

    @Autowired
    private LadderPlayerCharacterStatsDAO ladderPlayerCharacterStatsDAO;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private LadderMatchDAO ladderMatchDAO;

    @Autowired
    private SearchService searchService;

    @Autowired
    private ExternalPlayerCharacterLinkService externalPlayerCharacterLinkService;

    @GetMapping("/characters") @CharacterGroup
    public ResponseEntity<Object> get
    (
        @CharacterGroup Set<Long> characterIds,
        @RequestParam(value = "field", required = false) @AllowedField("id") String field
    )
    {
        return field != null
            ? WebServiceUtil.notFoundIfEmpty(characterIds.stream().map(IdProjection::new).toList())
            : WebServiceUtil.notFoundIfEmpty(ladderCharacterDAO
                .findDistinctCharactersByCharacterIds(characterIds));
    }

    @Operation(description = "0/1 season and multiple queues, or multiple seasons and 0/1 queue")
    @GetMapping(value = "/characters", params = {"field=" + IdField.NAME, "name"})
    public ResponseEntity<?> getIds
    (
        @RequestParam("name") @Valid @NotBlank @NotFakeSc2Name String name,
        @RequestParam(name = "region", required = false) Region region,
        @RequestParam(name = "queue", defaultValue = "") Set<QueueType> queues,
        @RequestParam(name = "season", defaultValue = "") @Valid @Size(max = ID_SEARCH_MAX_SEASONS)
        Set<Integer> seasons,
        @RequestParam(name = "caseSensitive", defaultValue = "true") boolean caseSensitive
    )
    {
        if(seasons.size() > 1 && queues.size() > 1) return ResponseEntity.badRequest()
            .body("1 season x queues or x seasons 1 queue are supported");
        return WebServiceUtil.notFoundIfEmpty(searchService
            .findIds(name, caseSensitive, region, seasons, queues)
            .stream()
            .map(IdProjection::new)
            .toList());
    }

    @GetMapping(value = "/characters", params = "query")
    public List<LadderDistinctCharacter> findCharacters(@RequestParam("query") String query)
    {
        return searchService.findDistinctCharacters(query);
    }

    @GetMapping("/characters/suggestions")
    public List<String> getSuggestions(@RequestParam("query") String query)
    {
        return searchService.suggestIfQuick(query, SEARCH_SUGGESTIONS_SIZE);
    }

    private static HttpStatus getStatus(Collection<? extends ExternalLinkResolveResult> results)
    {
        return results.isEmpty()
            ? HttpStatus.NOT_FOUND
            : results.stream()
                .map(ExternalLinkResolveResult::failedTypes)
                .anyMatch(s->!s.isEmpty())
                ? WebServiceUtil.UPSTREAM_ERROR_STATUS
                : results.stream()
                    .map(ExternalLinkResolveResult::links)
                    .anyMatch(l->!l.isEmpty())
                    ? HttpStatus.OK
                    : HttpStatus.NOT_FOUND;
    }

    @GetMapping("/character-links") @CharacterGroup
    public ResponseEntity<?> getLinks
    (
        @CharacterGroup Set<Long> characterIds,
        @RequestParam(name = "type", defaultValue = "") Set<SocialMedia> types
    )
    {
        List<ExternalLinkResolveResult> results = externalPlayerCharacterLinkService
            .getLinks(Set.copyOf(playerCharacterDAO.find(characterIds)), types)
            .collectList()
            .block();
        return ResponseEntity.status(getStatus(results)).body(results);
    }

    @GetMapping("/character-matches") @CharacterGroup
    public ResponseEntity<?> getMatches
    (
        @CharacterGroup Set<Long> characterIds,
        @RequestParam(name = "type", required = false, defaultValue = "") Set<BaseMatch.MatchType> types,
        @Version(LadderMatchDAO.CURSOR_POSITION_VERSION) Cursor cursor,
        @RequestParam(name = "limit", required = false, defaultValue = "20")
        @Min(1) @Max(MATCH_PAGE_SIZE_MAX)
        int limit
    )
    {
        return WebServiceUtil.notFoundIfEmpty
        (
            ladderMatchDAO.findMatchesByCharacterIds
            (
                characterIds,
                cursor,
                limit,
                types
            )
        );
    }

    @Operation
    (
        description = "If multiple characters(flattened) are used, then you must supply 1 season "
            + "and  1 queue filter. Max limit: " + TEAM_LIMIT + " for multi-character, "
            + SINGLE_CHARACTER_TEAM_LIMIT + " for single character."
    )
    @GetMapping("/character-teams") @CharacterGroup
    public ResponseEntity<?> getTeams
    (
        @CharacterGroup Set<Long> characterIds,
        @RequestParam(name = "queue", required = false, defaultValue = "") Set<QueueType> queues,
        @RequestParam(name = "season", required = false, defaultValue = "")
        Set<@Min(0) Integer> seasons,
        @RequestParam(name = "race", required = false, defaultValue = "") Set<Race> races,
        @RequestParam(name = "limit", required = false, defaultValue = TEAM_LIMIT + "")
        @Min(1) @Max(SINGLE_CHARACTER_TEAM_LIMIT)
        Integer limit
    )
    {
        if(characterIds.size() > 1 && limit > TEAM_LIMIT)
            return ResponseEntity.badRequest().body
                (
                    "Limit should be in 1-" + TEAM_LIMIT + " range, "
                        + "1-" + SINGLE_CHARACTER_TEAM_LIMIT + " for single character."
                );
        if(characterIds.size() > 1 && (seasons.size() != 1 || queues.size() != 1))
            return ResponseEntity.badRequest()
                .body("1 season and 1 queue are required for multi-character request");

        return WebServiceUtil.notFoundIfEmpty(ladderSearchDAO
            .findCharacterTeams(characterIds, seasons, queues, races, limit));
    }

}
