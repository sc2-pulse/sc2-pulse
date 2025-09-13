// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static com.nephest.battlenet.sc2.model.BaseTeam.MAX_RATING;
import static com.nephest.battlenet.sc2.web.controller.group.CharacterGroupArgumentResolver.areIdsInvalid;

import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.BaseLeagueTier;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.TeamFormat;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.inner.Group;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderStatsDAO;
import com.nephest.battlenet.sc2.model.validation.AllowedField;
import com.nephest.battlenet.sc2.model.web.SortParameter;
import com.nephest.battlenet.sc2.web.service.SC2MetaService;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import com.nephest.battlenet.sc2.web.service.community.CommunityService;
import com.nephest.battlenet.sc2.web.service.community.CommunityStreamResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class MiscController
{

    public static final Comparator<Clan> CLAN_COMPARATOR = Comparator
        .comparing(Clan::getActiveMembers, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(Clan::getMembers, Comparator.nullsLast(Comparator.reverseOrder()))
        .thenComparing(Clan::getId);

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private ClanDAO clanDAO;

    @Autowired
    private LadderProPlayerDAO ladderProPlayerDAO;

    @Autowired
    private SeasonDAO seasonDAO;

    @Autowired
    private LadderCharacterDAO ladderCharacterDAO;

    @Autowired
    private LadderStatsDAO ladderStatsDAO;

    @Autowired
    private SC2MetaService sc2MetaService;

    @Autowired
    private CommunityService communityService;

    @GetMapping("/seasons")
    public List<Season> getSeasons(@RequestParam(name = "season", required = false) Integer season)
    {
        return seasonDAO.findListByBattlenetId(season);
    }

    @GetMapping("/patches")
    public ResponseEntity<?> getPatches
    (
        @RequestParam(value = "buildMin", required = false) Long buildMin
    )
    {
        return WebServiceUtil.notFoundIfEmpty(sc2MetaService
            .getPatches(buildMin != null ? buildMin : SC2MetaService.PATCH_START));
    }

    @GetMapping("/tier-thresholds")
    public Map<Region, Map<BaseLeague.LeagueType, Map<BaseLeagueTier.LeagueTierType, Integer[]>>> getTierThresholds
    (
        @RequestParam("queue") QueueType queue,
        @RequestParam("teamType") TeamType teamType,
        @RequestParam("season") int season,
        @RequestParam(value = "region", defaultValue = "") Set<Region> regions,
        @RequestParam(value = "league", defaultValue = "") Set<BaseLeague.LeagueType> leagues
    )
    {
        return ladderStatsDAO.findLeagueBounds(season, regions, leagues, queue, teamType);
    }

    private static HttpStatus getStatus(CommunityStreamResult result)
    {
        return !result.getErrors().isEmpty()
            ? WebServiceUtil.UPSTREAM_ERROR_STATUS
            : result.getStreams().isEmpty()
                ? HttpStatus.NOT_FOUND
                : HttpStatus.OK;
    }

    @GetMapping("/streams")
    public ResponseEntity<?> getStreams
    (
        @RequestParam(name = "service", defaultValue = "") Set<SocialMedia> services,
        @RequestParam(name = "identifiedOnly", defaultValue = "false") boolean identifiedOnly,
        @RequestParam(name = "lax", defaultValue = "false") boolean lax,
        @RequestParam(name = "teamFormat", defaultValue = "") Set<TeamFormat> teamFormats,
        @RequestParam(name = "race", defaultValue = "") Set<Race> races,
        @RequestParam(name = "language", defaultValue = "") Set<Locale> languages,
        @RequestParam(name = "ratingMin", required = false) @Min(0) @Max(MAX_RATING) @Valid Integer ratingMin,
        @RequestParam(name = "ratingMax", required = false) @Min(0) @Max(MAX_RATING) @Valid Integer ratingMax,
        @RequestParam(name = "sort", defaultValue = "-viewers", required = false)
        @AllowedField({"viewers", "rating", "topPercentRegion"})
        SortParameter sort,
        @RequestParam(name = "limit", required = false) @Min(1) @Valid Integer limit,
        @RequestParam(name = "limitPlayer", required = false) @Min(1) @Valid Integer limitPlayer
    )
    {
        if(ratingMin != null && ratingMax != null && ratingMin > ratingMax) return ResponseEntity
            .badRequest()
            .body("ratingMin is greater than ratingMax");

        CommunityStreamResult result = communityService
            .getStreams
            (
                services,
                sort,
                identifiedOnly,
                races,
                languages,
                teamFormats,
                ratingMin, ratingMax,
                limit, limitPlayer,
                lax
            )
            .block();
        return ResponseEntity.status(getStatus(result)).body(result);
    }

    @GetMapping("/entities")
    public ResponseEntity<?> getEntities
    (
        @RequestParam(name = "characterId", required = false, defaultValue = "") Set<Long> characterIds,
        @RequestParam(name = "clanId", required = false, defaultValue = "") Set<Integer> clanIds,
        @RequestParam(name = "proPlayerId", required = false, defaultValue = "") Set<Long> proPlayerIds,
        @RequestParam(name = "accountId", required = false, defaultValue = "") Set<Long> accountIds,
        @RequestParam(name = "toonHandle", required = false, defaultValue = "") Set<PlayerCharacterNaturalId> toonHandles
    )
    {
        return areIdsInvalid(characterIds, clanIds, proPlayerIds, accountIds, toonHandles)
            .orElseGet(()->
            {
                List<LadderDistinctCharacter> characters = ladderCharacterDAO
                    .findDistinctCharactersByCharacterIds
                    (
                        Stream.of
                        (
                            characterIds,
                            playerCharacterDAO.findIdsByNaturalIds(toonHandles)
                        )
                            .flatMap(Collection::stream)
                            .collect(Collectors.toSet())
                    );
                List<Clan> clans = clanDAO.findByIds(clanIds);
                if(!clans.isEmpty()) clans.sort(CLAN_COMPARATOR);
                List<LadderProPlayer> proPlayers = ladderProPlayerDAO
                    .findByIds(proPlayerIds);
                List<Account> accounts = accountDAO.findByIds(accountIds);
                if(!accounts.isEmpty()) accounts.sort(Account.NATURAL_ID_COMPARATOR);
                return characters.isEmpty()
                    && clans.isEmpty()
                    && proPlayerIds.isEmpty()
                    && accounts.isEmpty()
                    ? ResponseEntity.notFound().build()
                    : ResponseEntity.ok(new Group(characters, clans, proPlayers, accounts));
            });
    }

}
