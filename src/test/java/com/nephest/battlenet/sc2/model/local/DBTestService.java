// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountDiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerAccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import discord4j.common.util.Snowflake;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DBTestService
{

    @Autowired
    private AccountDAO accountDAO;

    @Autowired
    private PlayerCharacterDAO playerCharacterDAO;

    @Autowired
    private ProPlayerDAO proPlayerDAO;

    @Autowired
    private ProPlayerAccountDAO proPlayerAccountDAO;

    @Autowired
    private DiscordUserDAO discordUserDAO;

    @Autowired
    private AccountDiscordUserDAO accountDiscordUserDAO;

    @Transactional
    public Object[] createAccountBindings(int num, boolean bind)
    {
        DiscordUser discordUser = discordUserDAO
            .merge(Set.of(new DiscordUser(Snowflake.of(num), "name" + num, num)))
            .iterator().next();
        ProPlayer proPlayer = proPlayerDAO
            .merge(new ProPlayer(null, (long) num, "proTag" + num, "proName" + num));
        Account acc1 = accountDAO.merge(new Account(null, Partition.GLOBAL, "tag" + num));
        PlayerCharacter char1 = playerCharacterDAO
            .merge(new PlayerCharacter(null, acc1.getId(), Region.EU, (long) num, num, "name" + num));
        if(bind)
        {
            proPlayerAccountDAO.link(proPlayer.getId(), "tag" + num);
            accountDiscordUserDAO
                .create(Set.of(new AccountDiscordUser(acc1.getId(), discordUser.getId())));
        }
        return new Object[]{acc1, char1};
    }

    public void verifyAccountBindings(long accountId, long bindingId)
    {
        DiscordUser discordUser = discordUserDAO.findByAccountId(accountId, false).orElseThrow();
        assertEquals(Snowflake.of(bindingId), discordUser.getId());

        ProPlayerAccount proPlayerAccount = proPlayerAccountDAO
            .findByProPlayerId((int) bindingId)
            .get(0);
        assertEquals(accountId, proPlayerAccount.getAccountId());
    }

}
