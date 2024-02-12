// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

@Service
public class BlizzardDataService
{

    public static final Duration ACCOUNT_IMPORT_DURATION = Duration.ofDays(365 * 10);

    private final SeasonDAO seasonDAO;
    private final AccountDAO accountDAO;
    private final PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    public BlizzardDataService
    (
        SeasonDAO seasonDAO,
        AccountDAO accountDAO,
        PlayerCharacterDAO playerCharacterDAO
    )
    {
        this.seasonDAO = seasonDAO;
        this.accountDAO = accountDAO;
        this.playerCharacterDAO = playerCharacterDAO;
    }

    @Transactional
    public void importData(Account account, List<BlizzardFullPlayerCharacter> blizzardCharacters)
    {
        List<PlayerCharacter> characters = blizzardCharacters
            .stream()
            .map(c->PlayerCharacter.of(account, c, c.getName()))
            .map(playerCharacterDAO::merge)
            .collect(Collectors.toList());
        accountDAO.updateAnonymousFlag(account.getId(), false);
        characters
            .stream()
            .map(PlayerCharacter::getId)
            .forEach(id->playerCharacterDAO.updateAnonymousFlag(id, false));

        Integer curSeason = seasonDAO.getMaxBattlenetId();
        Set<Tuple4<Account, PlayerCharacter, Boolean, Integer>> importedData = characters
            .stream()
            .map(c->Tuples.of(account, c, true, curSeason))
            .collect(Collectors.toSet());
        playerCharacterDAO.updateAccountsAndCharacters(importedData);
        accountDAO.updateUpdated(OffsetDateTime.now().plus(ACCOUNT_IMPORT_DURATION), Set.of(account.getId()));
    }

    @Transactional
    public void removeData(Account account, List<BlizzardFullPlayerCharacter> blizzardCharacters)
    {
        OffsetDateTime expiredOdt = OffsetDateTime.now().minus(BlizzardPrivacyService.DATA_TTL);
        List<PlayerCharacter> characters = blizzardCharacters
            .stream()
            .map(c->playerCharacterDAO.find(c.getRegion(), c.getRealm(), c.getBattlenetId()).orElse(null))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        Set<Long> characterIds = characters
            .stream()
            .map(PlayerCharacter::getId)
            .collect(Collectors.toSet());

        accountDAO.updateUpdated(expiredOdt, Set.of(account.getId()));
        playerCharacterDAO.updateUpdated(expiredOdt, characterIds);

        accountDAO.updateAnonymousFlag(account.getId(), true);
        for(Long id : characterIds) playerCharacterDAO.updateAnonymousFlag(id, true);
    }

}
