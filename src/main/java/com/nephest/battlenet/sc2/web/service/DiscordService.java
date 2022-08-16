// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.AccountDiscordUser;
import com.nephest.battlenet.sc2.model.local.dao.AccountDiscordUserDAO;
import com.nephest.battlenet.sc2.util.MiscUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Discord
public class DiscordService
{

    private static final Logger LOG = LoggerFactory.getLogger(DiscordService.class);

    public static final int DB_CURSOR_BATCH_SIZE = 1000;
    public static final int USER_UPDATE_BATCH_SIZE = 200;

    private final DiscordUserDAO discordUserDAO;
    private final AccountDiscordUserDAO accountDiscordUserDAO;
    private final DiscordAPI discordAPI;
    private final ExecutorService dbExecutorService;

    @Autowired
    public DiscordService
    (
        DiscordUserDAO discordUserDAO,
        AccountDiscordUserDAO accountDiscordUserDAO,
        DiscordAPI discordAPI,
        @Qualifier("dbExecutorService") ExecutorService dbExecutorService
    )
    {
        this.discordUserDAO = discordUserDAO;
        this.accountDiscordUserDAO = accountDiscordUserDAO;
        this.discordAPI = discordAPI;
        this.dbExecutorService = dbExecutorService;
    }

    /**
     * Remove existing connections and link the ids afterwards within the same transaction.
     *
     * @param accountId accountId
     * @param discordUserId discordUserId to link with
     */
    @Transactional
    public void linkAccountToDiscordUser(Long accountId, Long discordUserId)
    {
        accountDiscordUserDAO.remove(accountId, discordUserId);
        accountDiscordUserDAO.create(new AccountDiscordUser(accountId, discordUserId));
    }

    @Transactional
    public void linkAccountToNewDiscordUser(Long accountId, DiscordUser discordUser)
    {
        discordUserDAO.merge(discordUser);
        linkAccountToDiscordUser(accountId, discordUser.getId());
    }

    public void unlinkAccountFromDiscordUser(Long accountId, Long discordUserId)
    {
        accountDiscordUserDAO.remove(accountId, discordUserId);
    }

    public void update()
    {
        removeUsersWithNoAccountLinked();
        updateUsersFromAPI();
    }

    private void removeUsersWithNoAccountLinked()
    {
        int removed = discordUserDAO.removeUsersWithNoAccountLinked();
        if(removed > 0) LOG.info("Removed {} empty discord users", removed);
    }

    private int updateUsersFromAPI()
    {
        long idCursor = 0;
        int count = 0;
        while(true)
        {
            List<Long> toUpdate = discordUserDAO.findIdsByIdCursor(idCursor, DB_CURSOR_BATCH_SIZE);
            if(toUpdate.isEmpty()) break;

            List<Future<?>> tasks = new ArrayList<>();
            discordAPI.getUsers(toUpdate)
                .buffer(USER_UPDATE_BATCH_SIZE)
                .toStream()
                .forEach(batch->tasks.add(dbExecutorService.submit(()->updateUsers(batch))));
            MiscUtil.awaitAndThrowException(tasks, true, true);

            count += toUpdate.size();
            idCursor = toUpdate.get(toUpdate.size() - 1);
        }

        if(count > 0) LOG.info("Updated {} discord users", count);
        return count;
    }

    private void updateUsers(List<DiscordUser> users)
    {
        discordUserDAO.merge(users.toArray(DiscordUser[]::new));
    }

}
