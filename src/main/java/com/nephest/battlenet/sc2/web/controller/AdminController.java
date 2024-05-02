// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.Cron;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.web.service.AlternativeLadderService;
import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
import com.nephest.battlenet.sc2.web.service.MatchService;
import com.nephest.battlenet.sc2.web.service.StatsService;
import com.nephest.battlenet.sc2.web.service.SupporterService;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
@Hidden
public class AdminController
{

    @Autowired
    private StatsService statsService;

    @Autowired
    private AlternativeLadderService alternativeLadderService;

    @Autowired
    private BlizzardSC2API sc2API;

    @Autowired
    private MatchService matchService;

    @Autowired
    private SupporterService supporterService;

    //lazy for tests
    @Autowired @Lazy
    private Cron cron;

    @RequestMapping
    (
        value = "/update/partial/{region}",
        method = {RequestMethod.POST, RequestMethod.DELETE}
    )
    public void addPartialUpdate(@PathVariable("region") Region region, HttpServletRequest request)
    {
        statsService.setPartialUpdate(region, request.getMethod().equals("POST"));
    }

    @RequestMapping
    (
        value = "/update/partial/2/{region}",
        method = {RequestMethod.POST, RequestMethod.DELETE}
    )
    public void addPartialUpdate2(@PathVariable("region") Region region, HttpServletRequest request)
    {
        statsService.setPartialUpdate2(region, request.getMethod().equals("POST"));
    }

    @PostMapping("/update/match/frame/{durationMillis}")
    public ResponseEntity<Object> setMatchUpdateTimeFrame(@PathVariable("durationMillis") long durationMillis)
    {
        if(durationMillis < 0) return ResponseEntity.badRequest()
            .body("Duration can't be negative");

        matchService.setMatchUpdateFrame(Duration.ofMillis(durationMillis));
        return ResponseEntity.ok().build();
    }

    @PostMapping("/alternative/forced/{region}")
    public void addForcedAlternativeRegion(@PathVariable("region") Region region)
    {
        statsService.addForcedAlternativeRegion(region);
    }

    @DeleteMapping("/alternative/forced/{region}")
    public void removeForcedAlternativeRegion(@PathVariable("region") Region region)
    {
        statsService.removeForcedAlternativeRegion(region);
    }

    @PostMapping("/alternative/profile/ladder/web/region/{region}")
    public void addAlternativeProfileLadderWebRegion(@PathVariable("region") Region region)
    {
        alternativeLadderService.addProfileLadderWebRegion(region);
    }

    @DeleteMapping("/alternative/profile/ladder/web/region/{region}")
    public void removeAlternativeProfileLadderWebRegion(@PathVariable("region") Region region)
    {
        alternativeLadderService.removeProfileLadderWebRegion(region);
    }

    @PostMapping("/alternative/discovery/web/region/{region}")
    public void addAlternativeWebRegion(@PathVariable("region") Region region)
    {
        alternativeLadderService.addDiscoveryWebRegion(region);
    }

    @DeleteMapping("/alternative/discovery/web/region/{region}")
    public void removeAlternativeWebRegion(@PathVariable("region") Region region)
    {
        alternativeLadderService.removeDiscoveryWebRegion(region);
    }

    @PostMapping("/blizzard/api/region/{region}/force/{force}")
    public void addForceAPIRegion(@PathVariable("region") Region region, @PathVariable("force") Region force)
    {
        sc2API.setForceRegion(region, force);
    }

    @PostMapping("/blizzard/api/force/region/auto/{auto}")
    public void setAutoForceRegion(@PathVariable("auto") boolean auto)
    {
        sc2API.setAutoForceRegion(auto);
    }

    @DeleteMapping("/blizzard/api/region/{region}/force")
    public void removeForceAPIRegion(@PathVariable("region") Region region)
    {
        sc2API.setForceRegion(region, null);
    }

    @PostMapping("/blizzard/api/region/{region}/force/{force}/profile")
    public void setForceProfileAPIRegion
    (
        @PathVariable("region") Region region,
        @PathVariable("force") Region force
    )
    {
        sc2API.setForceProfileRegion(region, force);
    }

    @DeleteMapping("/blizzard/api/region/{region}/force/profile")
    public void removeForceProfileAPIRegion(@PathVariable("region") Region region)
    {
        sc2API.setForceProfileRegion(region, null);
    }

    @PostMapping("/blizzard/api/match/web/region/{region}")
    public void addMatchWebRegion(@PathVariable("region") Region region)
    {
        matchService.addWebRegion(region);
    }

    @DeleteMapping("/blizzard/api/match/web/region/{region}")
    public void removeMatchWebRegion(@PathVariable("region") Region region)
    {
        matchService.removeWebRegion(region);
    }

    @PostMapping("/supporters/supporter/{name}")
    public void addSupporter(@PathVariable String name)
    {
        supporterService.addSupporter(name);
    }

    @DeleteMapping("/supporters/supporter/{name}")
    public void removeSupporter(@PathVariable String name)
    {
        supporterService.removeSupporter(name);
    }

    @PostMapping("/supporters/donor/{name}")
    public void addDonor(@PathVariable String name)
    {
        supporterService.getDonorsVar().getValue().add(name);
        supporterService.getDonorsVar().save();
    }

    @DeleteMapping("/supporters/donor/{name}")
    public void removeDonor(@PathVariable String name)
    {
        supporterService.getDonorsVar().getValue().remove(name);
        supporterService.getDonorsVar().save();
    }

    @PostMapping("/update/ladder/{update}")
    public void setUpdateLadder(@PathVariable("update") Boolean updateLadder)
    {
        cron.setShouldUpdateLadder(updateLadder);
    }

    @RequestMapping
    (
        value = "/blizzard/api/ssl/error/ignore/{region}",
        method = {RequestMethod.POST, RequestMethod.DELETE}
    )
    public void setTimeout(@PathVariable("region") Region region, HttpServletRequest request)
    {
        sc2API.setIgnoreClientSslErrors(region, request.getMethod().equals("POST"));
    }

    @PostMapping("/blizzard/api/timeout/{region}/{timeout}")
    public void setTimeout
    (
        @PathVariable("region") Region region,
        @PathVariable("timeout") int timeout
    )
    {
        sc2API.setTimeout(region, Duration.ofMillis(timeout));
    }

    @DeleteMapping("/blizzard/api/timeout/{region}")
    public void removeTimeout(@PathVariable("region") Region region)
    {
        sc2API.setTimeout(region, null);
    }

    @PostMapping("/blizzard/api/rps/{region}/{cap}")
    public ResponseEntity<Object> setRequestsPerSecondCap
    (
        @PathVariable("region") Region region,
        @PathVariable("cap") float cap
    )
    {
        if(cap < 0) return ResponseEntity.badRequest().body("The cap can't be negative");
        if(cap > BlizzardSC2API.REQUESTS_PER_SECOND_CAP)
            return  ResponseEntity.badRequest()
                .body("Max cap: " + BlizzardSC2API.REQUESTS_PER_SECOND_CAP);

        sc2API.setRequestsPerSecondCap(region, cap);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/blizzard/api/rps/{region}")
    public void removeRequestsPerSecondCap(@PathVariable("region") Region region)
    {
        sc2API.setRequestsPerSecondCap(region, null);
    }

    @PostMapping("/blizzard/api/rph/{region}/{cap}")
    public ResponseEntity<Object> setRequestsPerHourCap
    (
        @PathVariable("region") Region region,
        @PathVariable("cap") float cap
    )
    {
        if(cap < 0) return ResponseEntity.badRequest().body("The cap can't be negative");
        if(cap > BlizzardSC2API.REQUESTS_PER_HOUR_CAP)
            return  ResponseEntity.badRequest()
                .body("Max cap: " + BlizzardSC2API.REQUESTS_PER_HOUR_CAP);

        sc2API.setRequestsPerHourCap(region, cap);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/blizzard/api/rph/{region}")
    public void removeRequestsPerHourCap(@PathVariable("region") Region region)
    {
        sc2API.setRequestsPerHourCap(region, null);
    }

}
