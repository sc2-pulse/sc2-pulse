// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.Blizzard;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import jakarta.validation.ValidationException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
@Blizzard
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
        LOG.trace("getCurrentOrLastOrExistingSeason({}, {})", region, maxSeason);
        try
        {
            return api.getCurrentOrLastSeason(region, maxSeason).block();
        }
        catch(RuntimeException ex)
        {
            if
            (
                ExceptionUtils.indexOfType(ex, WebClientResponseException.class) == -1
                && ExceptionUtils.indexOfType(ex, ValidationException.class) == -1
            ) throw ex;
            LOG.warn(ExceptionUtils.getRootCauseMessage(ex));
        }
        Season s = seasonDAO.findListByRegion(region).stream()
            .filter(ss->ss.getBattlenetId().equals(maxSeason))
            .findAny().orElseThrow();
        if(!SC2Pulse.offsetDateTime().isBefore(s.getEnd().minusDays(EXISTING_SEASON_DAYS_BEFORE_END_THRESHOLD)))
            throw new IllegalStateException("Could not find any season for " + region.name());
        return new BlizzardSeason(s.getBattlenetId(), s.getYear(), s.getNumber(), s.getStart(), s.getEnd());
    }

    /**
     * Finds the current, last, or existing season of the region, starting
     * from the max Blizzard season id of the region.
     *
     * @param region region
     * @return current, last, or existing season
     */
    public BlizzardSeason getCurrentOrLastOrExistingRegionSeason(Region region)
    {
        return getCurrentOrLastOrExistingSeason
        (
            region,
            seasonDAO.getMaxBattlenetId(region)
        );
    }

    /**
     * @deprecated use {@link #getCurrentOrLastOrExistingRegionSeason(Region)}
     * instead. {@code global = true} treats pulse season ids as region-local
     * Blizzard season ids, which is wrong for regions with offset season ids,
     * e.g. CN.
     */
    @Deprecated(forRemoval = true)
    public BlizzardSeason getCurrentOrLastOrExistingSeason(Region region, boolean global)
    {
        int maxId = global
            ? seasonDAO.getMaxPulseSeasonId()
            : seasonDAO.getMaxBattlenetId(region);
        return getCurrentOrLastOrExistingSeason(region, maxId);
    }

    /**
     * @deprecated unused. It treats pulse season ids as region-local Blizzard
     * season ids, which is wrong for regions with offset season ids, e.g. CN.
     */
    @Deprecated(forRemoval = true)
    public BlizzardSeason getCurrentOrLastOrExistingSeason(Region region)
    {
        return getCurrentOrLastOrExistingSeason(region, true);
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
