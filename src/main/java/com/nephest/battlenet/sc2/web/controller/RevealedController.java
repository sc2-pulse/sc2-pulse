// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import static com.nephest.battlenet.sc2.model.BaseTeam.MAX_RATING;

import com.nephest.battlenet.sc2.model.Race;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.TeamFormat;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderProPlayerDAO;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import com.nephest.battlenet.sc2.web.service.community.CommunityService;
import com.nephest.battlenet.sc2.web.service.community.CommunityStreamResult;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/revealed")
public class RevealedController
{

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private LadderProPlayerDAO ladderProPlayerDAO;

    @Autowired
    private CommunityService communityService;

    @GetMapping("/players")
    public List<ProPlayer> getPlayers()
    {
        return proPlayerDAO.findAll();
    }

    @Hidden
    @GetMapping("/player/{ids}/full")
    public ResponseEntity<Object> getLadderProPlayersLegacy(@PathVariable("ids") Set<Long> ids)
    {
        return WebServiceUtil.notFoundIfEmpty(ladderProPlayerDAO.findByIds(ids));
    }

    @Hidden
    @GetMapping("/stream")
    public ResponseEntity<?> getStreamsLegacy
    (
        @RequestParam(name = "service", defaultValue = "") Set<SocialMedia> services,
        @RequestParam(name = "sort", required = false) CommunityService.StreamSorting sorting,
        @RequestParam(name = "identifiedOnly", defaultValue = "false") boolean identifiedOnly,
        @RequestParam(name = "race", defaultValue = "") Set<Race> races,
        @RequestParam(name = "language", defaultValue = "") Set<Locale> languages,
        @RequestParam(name = "teamFormat", defaultValue = "") Set<TeamFormat> teamFormats,
        @RequestParam(name = "ratingMin", required = false) @Min(0) @Max(MAX_RATING) @Valid Integer ratingMin,
        @RequestParam(name = "ratingMax", required = false) @Min(0) @Max(MAX_RATING) @Valid Integer ratingMax,
        @RequestParam(name = "limit", required = false) @Min(1) @Valid Integer limit,
        @RequestParam(name = "limitPlayer", required = false) @Min(1) @Valid Integer limitPlayer,
        @RequestParam(name = "lax", defaultValue = "false") boolean lax
    )
    {
        if(ratingMin != null && ratingMax != null && ratingMin > ratingMax) return ResponseEntity
            .badRequest()
            .body("ratingMin is greater than ratingMax");

        if(sorting == null) sorting = CommunityService.StreamSorting.VIEWERS;

        CommunityStreamResult result = communityService
            .getStreams
            (
                services,
                sorting.getComparator(),
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

    @Hidden
    @GetMapping("/stream/featured")
    public ResponseEntity<CommunityStreamResult> getFeaturedStreamsLegacy
    (
        @RequestParam(name = "service", defaultValue = "") Set<SocialMedia> services
    )
    {
        CommunityStreamResult result = communityService.getFeaturedStreams(services).block();
        return ResponseEntity.status(getStatus(result)).body(result);
    }

    private static HttpStatus getStatus(CommunityStreamResult result)
    {
        return !result.getErrors().isEmpty()
            ? WebServiceUtil.UPSTREAM_ERROR_STATUS
            : result.getStreams().isEmpty()
                ? HttpStatus.NOT_FOUND
                : HttpStatus.OK;
    }

}
