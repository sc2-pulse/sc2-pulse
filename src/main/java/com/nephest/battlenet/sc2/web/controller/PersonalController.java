// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.config.security.AccountUser;
import com.nephest.battlenet.sc2.config.security.SC2PulseAuthority;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.AccountFollowing;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountDiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountRoleDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDiscordUser;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.common.CommonPersonalData;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.service.AccountFollowingService;
import com.nephest.battlenet.sc2.web.service.DiscordService;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/my")
public class PersonalController
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private AccountRoleDAO accountRoleDAO;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired
    private LadderCharacterDAO ladderCharacterDAO;

    @Autowired
    private DiscordUserDAO discordUserDAO;

    @Autowired
    private AccountDiscordUserDAO accountDiscordUserDAO;

    @Autowired
    private AccountFollowingService accountFollowingService;

    @Autowired
    private DiscordService discordService;

    @GetMapping("/common")
    public CommonPersonalData getCommon(@AuthenticationPrincipal AccountUser user)
    {
        DiscordUser discordUser = discordUserDAO
            .findByAccountId(user.getAccount().getId(), false)
            .orElse(null);
        return new CommonPersonalData
        (
            getAccount(user),
            user.getAuthorities().stream().map(a->(SC2PulseAuthority) a).collect(Collectors.toList()),
            ladderCharacterDAO.findLinkedDistinctCharactersByAccountId(user.getAccount().getId()),
            accountFollowingService.getAccountFollowingList(user.getAccount().getId()),
            ladderCharacterDAO.findDistinctCharactersByFollowing(user.getAccount().getId()),
            discordUser == null
                ? null
                : new LadderDiscordUser
                (
                    discordUser,
                    accountDiscordUserDAO.findMeta(discordUser.getId()).orElse(null)
                )
        );
    }

    @GetMapping("/account")
    public Account getAccount(@AuthenticationPrincipal AccountUser user)
    {
        //Spring security nullifies battletags, fetching account by id until I figure out how to fix the issue
        return accountDAO.findByIds(Set.of(user.getAccount().getId())).get(0);
    }

    @GetMapping("/characters")
    public List<LadderDistinctCharacter> getCharacters(@AuthenticationPrincipal AccountUser user)
    {
        return ladderCharacterDAO.findLinkedDistinctCharactersByAccountId(user.getAccount().getId());
    }

    @PostMapping("/following/{id}")
    public ResponseEntity<String> follow(@AuthenticationPrincipal AccountUser user, @PathVariable("id") long id)
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
    public void unfollow(@AuthenticationPrincipal AccountUser user, @PathVariable("id") long id)
    {
        accountFollowingService.unfollow(user.getAccount().getId(), id);
    }

    @GetMapping("/following")
    public List<AccountFollowing> getFollowingList(@AuthenticationPrincipal AccountUser user)
    {
        return accountFollowingService.getAccountFollowingList(user.getAccount().getId());
    }

    @GetMapping("/following/characters")
    public List<LadderDistinctCharacter> getFollowingCharacters(@AuthenticationPrincipal AccountUser user)
    {
        return ladderCharacterDAO.findDistinctCharactersByFollowing(user.getAccount().getId());
    }

    @GetMapping("/following/ladder")
    public List<LadderTeam> getFollowingLadder
    (
        @AuthenticationPrincipal AccountUser user,
        @RequestParam("season") int season,
        @RequestParam("queue") QueueType queue,
        @RequestParam("team-type") TeamType teamType,
        @RequestParam(value = "region", defaultValue = "") Set<Region> regions,
        @RequestParam(value = "league", defaultValue = "") Set<BaseLeague.LeagueType> leagues
    )
    {
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

    @PostMapping("/discord/unlink")
    public void unlinkDiscordUser(@AuthenticationPrincipal AccountUser user)
    {
        discordService.unlinkAccountFromDiscordUser(user.getAccount().getId(), null);
    }

    @PostMapping("/discord/public/{public}")
    public void setPublicFlag
    (
        @AuthenticationPrincipal AccountUser user,
        @PathVariable("public") Boolean isPublic
    )
    {
        discordService.setVisibility(user.getAccount().getId(), isPublic);
    }

}
