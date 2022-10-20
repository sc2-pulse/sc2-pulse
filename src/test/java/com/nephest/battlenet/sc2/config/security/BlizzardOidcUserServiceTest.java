// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountRoleDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.web.service.BlizzardSC2API;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
public class BlizzardOidcUserServiceTest
{

    private static final Partition PARTITION = Partition.GLOBAL;
    private static final String BATTLE_TAG = "tag#1";
    private static final long ID = 1;

    @Mock
    private AccountDAO accountDAO;

    @Mock
    private PlayerCharacterDAO playerCharacterDAO;

    @Mock
    private AccountRoleDAO accountRoleDAO;

    @Mock
    private BlizzardSC2API api;

    @Mock
    private OidcUserService oidcUserService;

    private BlizzardOidcUserService service;

    @BeforeEach
    public void beforeEach()
    throws MalformedURLException
    {
        service = new BlizzardOidcUserService(accountDAO, playerCharacterDAO, accountRoleDAO, api);
        service.setService(oidcUserService);
        OidcUser user = new DefaultOidcUser
        (
            List.of(SC2PulseAuthority.USER),
            new OidcIdToken
            (
                "123",
                Instant.now(),
                Instant.now().plus(Duration.ofDays(1)),
                Map.of
                (
                    "battle_tag", BATTLE_TAG,
                    "iss", new URL("https://eu.blizzard.com")
                )
            ),
            OidcUserInfo.builder()
                .subject(String.valueOf(ID))
                .name(String.valueOf(ID))
                .claim("battle_tag", BATTLE_TAG)
                .claim("iss", new URL("https://eu.blizzard.com"))
                .build()
        );
        when(oidcUserService.loadUser(any())).thenReturn(user);
    }

    @Test
    public void whenAccountAlreadyExists_thenReturnItAndDontMerge()
    {
        Account expectedResult = new Account(1L, PARTITION, BATTLE_TAG);
        when(accountDAO.find(PARTITION, BATTLE_TAG)).thenReturn(Optional.of(expectedResult));

        assertSame(expectedResult, ((BlizzardOidcUser) service.loadUser(null)).getAccount());
        verifyNoMoreInteractions(accountDAO);
    }

    @Test
    public void whenAccountDoesntExist_thenLoadByBlizzardCharactersAndDontMerge()
    {
        Account expectedResult = new Account(1L, PARTITION, BATTLE_TAG);
        when(accountDAO.find(PARTITION, BATTLE_TAG)).thenReturn(Optional.empty());
        when(api.getPlayerCharacters(Region.EU, ID)).thenReturn(Flux.just(
            new BlizzardFullPlayerCharacter(10L, 1, "name#1", Region.EU),
            new BlizzardFullPlayerCharacter(11L, 1, "name#2", Region.US),
            new BlizzardFullPlayerCharacter(12L, 1, "name#3", Region.KR)
        ));
        when(playerCharacterDAO.find(Region.EU, 1, 10L))
            .thenReturn(Optional.of(new PlayerCharacter(1L, 123L, Region.EU, 10L, 1, "name#1")));
        when(accountDAO.findByIds(123L)).thenReturn(List.of(expectedResult));

        assertSame(expectedResult, ((BlizzardOidcUser) service.loadUser(null)).getAccount());
        verifyNoMoreInteractions(accountDAO);
    }

    @Test
    public void whenAccountDoesntExistAndLoadingByBlizzardProfileHasFailed_thenMerge()
    {
        Account expectedResult = new Account(1L, PARTITION, BATTLE_TAG);
        when(accountDAO.merge(expectedResult)).thenReturn(expectedResult);
        when(accountDAO.find(PARTITION, BATTLE_TAG)).thenReturn(Optional.empty());
        when(api.getPlayerCharacters(Region.EU, ID)).thenReturn(Flux.just(
            new BlizzardFullPlayerCharacter(10L, 1, "name#1", Region.EU),
            new BlizzardFullPlayerCharacter(11L, 1, "name#2", Region.US),
            new BlizzardFullPlayerCharacter(12L, 1, "name#3", Region.KR)
        ));
        when(playerCharacterDAO.find(any(), anyInt(), anyLong()))
            .thenReturn(Optional.empty());

        assertEquals(expectedResult, ((BlizzardOidcUser) service.loadUser(null)).getAccount());
        verify(accountDAO).merge(argThat(a->a.equals(expectedResult)));
    }

}
