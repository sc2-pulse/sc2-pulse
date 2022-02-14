// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;

@Service
public class SC2WebServiceUtil
{

    private static final Logger LOG = LoggerFactory.getLogger(SC2WebServiceUtil.class);

    public static final int EXISTING_SEASON_DAYS_BEFORE_END_THRESHOLD = 3;

    private final BlizzardSC2API api;
    private final SeasonDAO seasonDAO;

    @Autowired
    public SC2WebServiceUtil(BlizzardSC2API api, SeasonDAO seasonDAO)
    {
        this.api = api;
        this.seasonDAO = seasonDAO;
    }

    public BlizzardSeason getCurrentOrLastOrExistingSeason(Region region, int maxSeason)
    {
        try
        {
            return api.getCurrentOrLastSeason(region, maxSeason).block();
        }
        catch(RuntimeException ex)
        {
            if(!(ExceptionUtils.getRootCause(ex) instanceof WebClientResponseException)) throw ex;
            LOG.warn(ExceptionUtils.getRootCauseMessage(ex));
        }
        Season s = seasonDAO.findListByRegion(region).stream()
            .filter(ss->ss.getBattlenetId().equals(maxSeason))
            .findAny().orElseThrow();
        if(!LocalDate.now().isBefore(s.getEnd().minusDays(EXISTING_SEASON_DAYS_BEFORE_END_THRESHOLD)))
            throw new IllegalStateException("Could not find any season for " + region.name());
        return new BlizzardSeason(s.getBattlenetId(), s.getYear(), s.getNumber(), s.getStart(), s.getEnd());
    }

    public BlizzardSeason getCurrentOrLastOrExistingSeason(Region region)
    {
        return getCurrentOrLastOrExistingSeason(region, seasonDAO.getMaxBattlenetId());
    }

    public BlizzardSeason getExternalOrExistingSeason(Region region, int season)
    {
        try
        {
            return api.getSeason(region, season).block();
        }
        catch(RuntimeException ex)
        {
            if(!(ExceptionUtils.getRootCause(ex) instanceof WebClientResponseException)) throw ex;
            LOG.warn(ExceptionUtils.getRootCauseMessage(ex));
        }
        Season s = seasonDAO.findListByRegion(region).stream()
            .filter(ss->ss.getBattlenetId().equals(season))
            .findAny().orElseThrow();
        return new BlizzardSeason(s.getBattlenetId(), s.getYear(), s.getNumber(), s.getStart(), s.getEnd());
    }

}
