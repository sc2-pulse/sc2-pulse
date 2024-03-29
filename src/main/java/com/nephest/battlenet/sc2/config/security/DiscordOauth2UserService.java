// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import static com.nephest.battlenet.sc2.web.service.PersonalService.getPrincipal;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountRoleDAO;
import com.nephest.battlenet.sc2.web.service.AccountService;
import com.nephest.battlenet.sc2.web.service.DiscordAPI;
import com.nephest.battlenet.sc2.web.service.DiscordService;
import com.nephest.battlenet.sc2.web.service.PersonalService;
import discord4j.common.util.Snowflake;
import java.util.List;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service @Discord
public class DiscordOauth2UserService
implements Oauth2UserServiceRegistration<OAuth2UserRequest, OAuth2User>
{

    private static final List<String> REGISTRATION_IDS = List.of("discord-lg");

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> service =
        new DefaultOAuth2UserService();
    private final AccountRoleDAO accountRoleDAO;
    private final AccountService accountService;
    private final DiscordService discordService;
    private final DiscordAPI discordAPI;

    public DiscordOauth2UserService
    (
        AccountRoleDAO accountRoleDAO,
        AccountService accountService,
        DiscordService discordService,
        DiscordAPI discordAPI,
        PersonalService personalService
    )
    {
        this.accountRoleDAO = accountRoleDAO;
        this.accountService = accountService;
        this.discordService = discordService;
        this.discordAPI = discordAPI;
    }

    @Override
    public Iterable<String> getRegistrationIds()
    {
        return REGISTRATION_IDS;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException
    {
        OAuth2User user = discordAPI
            .withLimiter(Mono.fromCallable(()->service.loadUser(userRequest)), true)
            .blockLast();
        DiscordUser discordUser = from(user);
        AccountUser accountUser = getPrincipal()
            .orElseThrow(()->new IllegalStateException("Authentication not found"));

        Account account = accountUser.getAccount();
        discordService.linkAccountToNewDiscordUser(account.getId(), discordUser);

        return new AccountOauth2User<>
        (
            user,
            account,
            accountService.getOrGenerateNewPassword(account.getId()),
            accountRoleDAO.getRoles(account.getId())
        );
    }

    private static DiscordUser from(OAuth2User oAuth2User)
    {
        return new DiscordUser
        (
            Snowflake.of((String) oAuth2User.getAttribute("id")),
            oAuth2User.getAttribute("username"),
            Integer.valueOf(oAuth2User.getAttribute("discriminator"))
        );
    }

}
