// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.security.BlizzardOidcUser;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.AccountFollowing;
import com.nephest.battlenet.sc2.model.local.dao.AccountRoleDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.common.CommonPersonalData;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.service.AccountFollowingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/my")
public class PersonalController
{

    @Autowired
    private AccountRoleDAO accountRoleDAO;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private LadderCharacterDAO ladderCharacterDAO;

    @Autowired
    private AccountFollowingService accountFollowingService;

    @GetMapping("/common")
    public CommonPersonalData getCommon(@AuthenticationPrincipal BlizzardOidcUser user)
    {
        return new CommonPersonalData
        (
            user.getAccount(),
            accountRoleDAO.getRoles(user.getAccount().getId()),
            ladderCharacterDAO.findLinkedDistinctCharactersByAccountId(user.getAccount().getId()),
            accountFollowingService.getAccountFollowingList(user.getAccount().getId())
        );
    }

    @GetMapping("/account")
    public Account getAccount(@AuthenticationPrincipal BlizzardOidcUser user)
    {
        return user.getAccount();
    }

    @GetMapping("/characters")
    public List<LadderDistinctCharacter> getCharacters(@AuthenticationPrincipal BlizzardOidcUser user)
    {
        return ladderCharacterDAO.findLinkedDistinctCharactersByAccountId(user.getAccount().getId());
    }

    @PostMapping("/following/{id}")
    public ResponseEntity<String> follow(@AuthenticationPrincipal BlizzardOidcUser user, @PathVariable("id") long id)
    {
        if(!accountFollowingService.canFollow(id) || !accountFollowingService.follow(user.getAccount().getId(), id))
        {
            return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body("{\"message\":\"Too many followings\"}");
        }
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/following/{id}")
    public void unfollow(@AuthenticationPrincipal BlizzardOidcUser user, @PathVariable("id") long id)
    {
        accountFollowingService.unfollow(user.getAccount().getId(), id);
    }

    @GetMapping("/following")
    public List<AccountFollowing> getFollowingList(@AuthenticationPrincipal BlizzardOidcUser user)
    {
        return accountFollowingService.getAccountFollowingList(user.getAccount().getId());
    }

    @GetMapping("/following/ladder")
    public List<LadderTeam> getFollowingLadder
    (
        @AuthenticationPrincipal BlizzardOidcUser user,
        @RequestParam("season") int season,
        @RequestParam("queue") QueueType queue,
        @RequestParam("team-type") TeamType teamType,
        @RequestParam(name = "us", required = false) boolean us,
        @RequestParam(name = "eu", required = false) boolean eu,
        @RequestParam(name = "kr", required = false) boolean kr,
        @RequestParam(name = "cn", required = false) boolean cn,
        @RequestParam(name = "bro", required = false) boolean bronze,
        @RequestParam(name = "sil", required = false) boolean silver,
        @RequestParam(name = "gol", required = false) boolean gold,
        @RequestParam(name = "pla", required = false) boolean platinum,
        @RequestParam(name = "dia", required = false) boolean diamond,
        @RequestParam(name = "mas", required = false) boolean master,
        @RequestParam(name = "gra", required = false) boolean grandmaster
    )
    {
        Set<Region> regions = EnumSet.noneOf(Region.class);
        if(us) regions.add(Region.US);
        if(eu) regions.add(Region.EU);
        if(kr) regions.add(Region.KR);
        if(cn) regions.add(Region.CN);

        Set<BaseLeague.LeagueType> leagues = EnumSet.noneOf(BaseLeague.LeagueType.class);
        if(bronze) leagues.add(BaseLeague.LeagueType.BRONZE);
        if(silver) leagues.add(BaseLeague.LeagueType.SILVER);
        if(gold) leagues.add(BaseLeague.LeagueType.GOLD);
        if(platinum) leagues.add(BaseLeague.LeagueType.PLATINUM);
        if(diamond) leagues.add(BaseLeague.LeagueType.DIAMOND);
        if(master) leagues.add(BaseLeague.LeagueType.MASTER);
        if(grandmaster) leagues.add(BaseLeague.LeagueType.GRANDMASTER);
        return ladderSearchDAO.findFollowingTeams
        (
            user.getAccount().getId(),
            season,
            regions,
            leagues,
            queue,
            teamType
        );
    }

}
