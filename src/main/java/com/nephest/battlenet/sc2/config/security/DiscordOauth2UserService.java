// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountRoleDAO;
import com.nephest.battlenet.sc2.web.service.DiscordService;
import com.nephest.battlenet.sc2.web.service.PersonalService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service @Discord
public class DiscordOauth2UserService
implements Oauth2UserServiceRegistration<OAuth2UserRequest, OAuth2User>
{

    private static final List<String> REGISTRATION_IDS = List.of("discord-lg");

    private final OAuth2UserService<OAuth2UserRequest, OAuth2User> service =
        new DefaultOAuth2UserService();
    private final AccountRoleDAO accountRoleDAO;
    private final DiscordService discordService;
    private final PersonalService personalService;

    public DiscordOauth2UserService
    (
        AccountRoleDAO accountRoleDAO,
        DiscordService discordService,
        PersonalService personalService
    )
    {
        this.accountRoleDAO = accountRoleDAO;
        this.discordService = discordService;
        this.personalService = personalService;
    }

    @Override
    public Iterable<String> getRegistrationIds()
    {
        return REGISTRATION_IDS;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException
    {
        OAuth2User user = service.loadUser(userRequest);
        DiscordUser discordUser = from(user);
        Authentication authentication = personalService.getAuthentication();
        if(authentication == null) throw new IllegalStateException("Authentication not found");

        AccountUser accountUser = (AccountUser) authentication.getPrincipal();
        Account account = accountUser.getAccount();
        discordService.linkAccountToNewDiscordUser(account.getId(), discordUser);

        return new AccountOauth2User<>(user, account, accountRoleDAO.getRoles(account.getId()));
    }

    private static DiscordUser from(OAuth2User oAuth2User)
    {
        return new DiscordUser
        (
            Long.valueOf(oAuth2User.getAttribute("id")),
            oAuth2User.getAttribute("username"),
            Integer.valueOf(oAuth2User.getAttribute("discriminator"))
        );
    }

}
