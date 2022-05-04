// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.local.LongVar;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClanService
{

    private static final Logger LOG = LoggerFactory.getLogger(ClanService.class);

    public static final Duration STATS_UPDATE_FRAME = Duration.ofDays(2);
    public static final int CLAN_STATS_BATCH_SIZE = 12;

    private final ClanDAO clanDAO;

    private InstantVar statsUpdated;
    private InstantVar statsNullified;
    private LongVar statsCursor;

    @Autowired
    public ClanService(ClanDAO clanDAO, VarDAO varDAO)
    {
        this.clanDAO = clanDAO;
        init(varDAO);
    }

    private void init(VarDAO varDAO)
    {
        statsUpdated = new InstantVar(varDAO, "clan.stats.updated", false);
        statsNullified = new InstantVar(varDAO, "clan.stats.nullified", false);
        statsCursor = new LongVar(varDAO, "clan.stats.id", false);
        try
        {
            if(statsUpdated.load() == null) statsUpdated.setValueAndSave(Instant.now());
            if(statsNullified.load() == null) statsNullified
                .setValueAndSave(Instant.now().minus(STATS_UPDATE_FRAME));
            if(statsCursor.load() == null) statsCursor.setValueAndSave(0L);
        }
        catch (Exception ex)
        {
            LOG.error(ex.getMessage(), ex);
        }
    }

    protected InstantVar getStatsUpdated()
    {
        return statsUpdated;
    }

    protected InstantVar getStatsNullified()
    {
        return statsNullified;
    }

    protected LongVar getStatsCursor()
    {
        return statsCursor;
    }

    @Transactional
    public void update()
    {
        if(shouldUpdateStats()) updateStats();
        if(shouldNullifyStats()) nullifyStats();
    }

    private boolean shouldUpdateStats()
    {
        return true;
    }

    private boolean shouldNullifyStats()
    {
        return statsNullified.getValue().isBefore(Instant.now().minus(STATS_UPDATE_FRAME));
    }

    private void updateStats()
    {
        int batchSize = getClanBatchSize();
        if(batchSize < 1) return;

        List<Integer> batch = clanDAO.findIdsByMinMemberCount
        (
            ClanDAO.CLAN_STATS_MIN_MEMBERS,
            (int) (long) statsCursor.getValue(),
            batchSize
        );
        if(batch.isEmpty())
        {
            statsCursor.setValueAndSave(0L);
            statsUpdated.setValueAndSave(Instant.now());
        }
        else
        {
            updateStats(batch);
            statsCursor.setValueAndSave((long) batch.get(batch.size() - 1));
            statsUpdated.setValueAndSave(Instant.now());
        }

    }

    public int updateStats(List<Integer> validClans)
    {
        int batchIx = 0;
        int count = 0;
        while(batchIx < validClans.size())
        {
            List<Integer> batch = validClans.subList(batchIx, Math.min(batchIx + CLAN_STATS_BATCH_SIZE, validClans.size()));
            count += clanDAO.updateStats(batch);
            batchIx += batch.size();
            LOG.trace("Clan stats progress: {}/{} ", batchIx, validClans.size());
        }
        LOG.info("Updates stats of {} clans", count);
        return count;
    }

    private int getClanBatchSize()
    {
        int clansTotal = clanDAO.getCountByMinMemberCount(ClanDAO.CLAN_STATS_MIN_MEMBERS);
        if(clansTotal == 0) return 0;
        Duration durationPerClan = STATS_UPDATE_FRAME.dividedBy(clansTotal);
        return (int) Duration.between(statsUpdated.getValue(), Instant.now())
            .dividedBy(durationPerClan);
    }

    private void nullifyStats()
    {
        clanDAO.nullifyStats(ClanDAO.CLAN_STATS_MIN_MEMBERS - 1);
        statsNullified.setValueAndSave(Instant.now());
    }

}
