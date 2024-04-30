// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static com.nephest.battlenet.sc2.web.service.SearchService.ID_SEARCH_MAX_SEASONS;

import com.nephest.battlenet.sc2.model.BaseMatch;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
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
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.model.local.ladder.common.CommonCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderMatchDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderPlayerCharacterStatsDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderTeamStateDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.model.validation.NotFakeSc2Name;
import com.nephest.battlenet.sc2.web.service.PlayerCharacterReportService;
import com.nephest.battlenet.sc2.web.service.SearchService;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import com.nephest.battlenet.sc2.web.service.external.ExternalLinkResolveResult;
import com.nephest.battlenet.sc2.web.service.external.ExternalPlayerCharacterLinkService;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
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

    @Autowired
    private ExternalPlayerCharacterLinkService externalPlayerCharacterLinkService;

    @Hidden
    @GetMapping("/search/{term}")
    public List<LadderDistinctCharacter> getCharacterTeamsLegacy(@PathVariable("term") String term)
    {
        return searchService.findDistinctCharacters(term);
    }

    @GetMapping("/search")
    public List<LadderDistinctCharacter> getCharacterTeams(@RequestParam("term") String term)
    {
        return searchService.findDistinctCharacters(term);
    }

    @Operation(description = "0/1 season and multiple queues, or multiple seasons and 0/1 queue")
    @GetMapping("/search/advanced")
    public ResponseEntity<?> findCharacterIds
    (
        @RequestParam("name") @Valid @NotBlank @NotFakeSc2Name String name,
        @RequestParam(name = "caseSensitive", defaultValue = "true") boolean caseSensitive,
        @RequestParam(name = "region", required = false) Region region,
        @RequestParam(name = "season", defaultValue = "") @Valid @Size(max = ID_SEARCH_MAX_SEASONS) Set<Integer> seasons,
        @RequestParam(name = "queue", defaultValue = "") Set<QueueType> queues
    )
    {
        if(seasons.size() > 1 && queues.size() > 1) return ResponseEntity.badRequest()
            .body("1 season x queues or x seasons 1 queue are supported");
        return WebServiceUtil.notFoundIfEmpty(searchService
            .findIds(name, caseSensitive, region, seasons, queues));
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
    public ResponseEntity<List<PlayerCharacter>> getPlayerCharacters(@PathVariable("ids") Set<Long> ids)
    {
        if(ids.size() > PLAYER_CHARACTERS_MAX)
            return ResponseEntity.badRequest().build();

        return ResponseEntity.of(Optional.of(playerCharacterDAO.find(ids)));
    }

    @Hidden
    @GetMapping("/{id}/common/{types}")
    public ResponseEntity<CommonCharacter> getCommonCharacterLegacy
    (
        @PathVariable("id") long id,
        @PathVariable(name = "types") BaseMatch.MatchType[] types
    )
    {
        if(types == null) types = new BaseMatch.MatchType[0];
        List<LadderDistinctCharacter> linkedCharacters =
            ladderCharacterDAO.findLinkedDistinctCharactersByCharacterId(id);
        if(linkedCharacters.isEmpty()) return ResponseEntity.notFound().build();

        LadderDistinctCharacter currentCharacter = linkedCharacters.stream()
                .filter(c->c.getMembers().getCharacter().getId() == id)
                .findAny()
                .orElseThrow();

        Set<Long> idSet = Set.of(id);
        return ResponseEntity.ok(new CommonCharacter
        (
            ladderSearch.findCharacterTeams(idSet),
            linkedCharacters,
            ladderPlayerCharacterStatsDAO.findGlobalList(id),
            ladderProPlayerDAO.findByCharacterIds(idSet).stream().findFirst().orElse(null),
            discordUserDAO
                .findByAccountId(currentCharacter.getMembers().getAccount().getId(), true)
                .orElse(null),
            ladderMatchDAO.findMatchesByCharacterId(
                id, SC2Pulse.offsetDateTime(), BaseMatch.MatchType._1V1, 0, 0, 1, types).getResult(),
            ladderTeamStateDAO.find(id),
            reportService.findReportsByCharacterIds
            (
                linkedCharacters.stream()
                    .map(LadderDistinctCharacter::getMembers)
                    .map(LadderTeamMember::getCharacter)
                    .map(PlayerCharacter::getId)
                    .collect(Collectors.toSet())
            )
        ));
    }

    @GetMapping("/{id}/common")
    public ResponseEntity<CommonCharacter> getCommonCharacter
    (
        @PathVariable("id") long id,
        @RequestParam(name = "matchType", required = false) BaseMatch.MatchType[] types,
        @RequestParam(name = "mmrHistoryDepth", required = false) Integer depth
    )
    {
        if(types == null) types = new BaseMatch.MatchType[0];
        OffsetDateTime from = depth == null ? null : SC2Pulse.offsetDateTime().minusDays(depth);
        List<LadderDistinctCharacter> linkedCharacters =
            ladderCharacterDAO.findLinkedDistinctCharactersByCharacterId(id);
        if(linkedCharacters.isEmpty()) return ResponseEntity.notFound().build();

        LadderDistinctCharacter currentCharacter = linkedCharacters.stream()
            .filter(c->c.getMembers().getCharacter().getId() == id)
            .findAny()
            .orElseThrow();

        Set<Long> idSet = Set.of(id);
        return ResponseEntity.ok(new CommonCharacter
        (
            ladderSearch.findCharacterTeams(idSet),
            linkedCharacters,
            ladderPlayerCharacterStatsDAO.findGlobalList(id),
            ladderProPlayerDAO.findByCharacterIds(idSet).stream().findFirst().orElse(null),
            discordUserDAO
                .findByAccountId(currentCharacter.getMembers().getAccount().getId(), true)
                .orElse(null),
            ladderMatchDAO.findMatchesByCharacterId(
                id, SC2Pulse.offsetDateTime(), BaseMatch.MatchType._1V1, 0, 0, 1, types).getResult(),
            ladderTeamStateDAO.find(id, from),
            reportService.findReportsByCharacterIds
            (
                linkedCharacters.stream()
                    .map(LadderDistinctCharacter::getMembers)
                    .map(LadderTeamMember::getCharacter)
                    .map(PlayerCharacter::getId)
                    .collect(Collectors.toSet())
            )
        ));
    }

    @Hidden
    @GetMapping
    ({
        "/{id}/matches/{dateAnchor}/{typeAnchor}/{mapAnchor}/{page}/{pageDiff}",
        "/{id}/matches/{dateAnchor}/{typeAnchor}/{mapAnchor}/{page}/{pageDiff}/{types}"
    })
    public PagedSearchResult<List<LadderMatch>> getCharacterMatchesLegacy
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
            SC2Pulse.offsetDateTime(OffsetDateTime.parse(dateAnchor)),
            typeAnchor,
            mapAnchor,
            page,
            pageDiff,
            types
        );
    }

    @Hidden
    @GetMapping("/{id}/teams")
    public List<LadderTeam> getCharacterTeamsLegacy
    (
        @PathVariable("id") long id
    )
    {
        return ladderSearch.findCharacterTeams(Set.of(id));
    }

    @GetMapping("/{id}/stats")
    public List<PlayerCharacterStats> getCharacterStats
    (
        @PathVariable("id") long id
    )
    {
        return playerCharacterStatsDAO.findGlobalList(id);
    }

    @Operation
    (
        description = "Max depth is " + SUMMARY_DEPTH_MAX + ", unlimited for single character"
    )
    @GetMapping
    ({
        "/{ids}/summary/1v1/{depthDays}",
        "/{ids}/summary/1v1/{depthDays}/{races}"
    })
    public List<PlayerCharacterSummary> getCharacterSummary
    (
        @PathVariable("ids") @Valid @Size(max = SUMMARY_IDS_MAX) Long[] ids,
        @PathVariable("depthDays") int depth,
        @PathVariable(name = "races", required = false) Race[] races
    )
    {
        if(ids.length > 1 && depth > SUMMARY_DEPTH_MAX)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Depth is too big, max: " + SUMMARY_DEPTH_MAX);
        if(races == null) races = Race.EMPTY_RACE_ARRAY;

        return playerCharacterSummaryDAO.find(ids, SC2Pulse.offsetDateTime().minusDays(depth), races);
    }

    @GetMapping("/{id}/links/additional")
    public ResponseEntity<ExternalLinkResolveResult> getAdditionalCharacterLinks
    (
        @PathVariable("id") long id
    )
    {
        List<PlayerCharacter> characters = playerCharacterDAO.find(Set.of(id));
        if(characters.isEmpty()) return ResponseEntity.notFound().build();

        ExternalLinkResolveResult result = externalPlayerCharacterLinkService.getLinks(characters.get(0));
        ResponseEntity.BodyBuilder bodyBuilder = ResponseEntity.status(getStatus(result));
        if(result.getFailedTypes().isEmpty()) bodyBuilder
            .header(HttpHeaders.CACHE_CONTROL, WebServiceUtil.DEFAULT_CACHE_HEADER);
        return bodyBuilder.body(result);
    }

    private static HttpStatus getStatus(ExternalLinkResolveResult result)
    {
        return !result.getFailedTypes().isEmpty()
            ? HttpStatus.BAD_GATEWAY
            : result.getLinks().isEmpty()
                ? HttpStatus.NOT_FOUND
                : HttpStatus.OK;
    }

}
