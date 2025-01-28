// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StatusService
{

    private final UpdateService updateService;
    private final BlizzardSC2API api;
    private final StatsService statsService;
    private final AlternativeLadderService alternativeLadderService;
    private final MatchService matchService;
    private final PostgreSQLUtils postgreSQLUtils;
    private final Map<Region, Status> statusMap = new EnumMap<>(Region.class);
    private Long players;
    private Long teamSnapshots;
    private Long matches;

    @Autowired
    public StatusService
    (
        UpdateService updateService,
        BlizzardSC2API api,
        StatsService statsService,
        AlternativeLadderService alternativeLadderService,
        MatchService matchService,
        PostgreSQLUtils postgreSQLUtils,
        GlobalContext globalContext
    )
    {
        this.updateService = updateService;
        this.api = api;
        this.statsService = statsService;
        this.alternativeLadderService = alternativeLadderService;
        this.matchService = matchService;
        this.postgreSQLUtils = postgreSQLUtils;
        init(globalContext.getActiveRegions());
    }

    private void init(Collection<? extends Region> regionsToUpdate)
    {
        for(Region region : regionsToUpdate) statusMap.put(region, new Status());
        update();
    }

    public void update()
    {
        players = postgreSQLUtils.getApproximateCount("account");
        teamSnapshots = postgreSQLUtils.getApproximateCount("team_state");
        matches = postgreSQLUtils.getApproximateCount("match");
        for(Map.Entry<Region, Status> entry : statusMap.entrySet())
        {
            Region region = entry.getKey();
            Status status = entry.getValue();

            updatePartialFlag(region, status);
            updateRedirectedFlag(region, status);
            updatePrioritizedFlag(region, status);
            updatePrioritized2Flag(region, status);
            updateWebFlag(region, status);
            updateOperationalFlag(region, status);
            updateDuration(region, status);
        }
    }

    private boolean updatePartialFlag(Region region, Status status)
    {
        return statsService.isAlternativeUpdate(region, true)
            ? status.getFlags().add(Status.Flag.PARTIAL)
            : status.getFlags().remove(Status.Flag.PARTIAL);
    }

    private boolean updateRedirectedFlag(Region region, Status status)
    {
        return (api.getForceRegion(region) != null && api.getForceRegion(region) != region)
            || (api.getForceProfileRegion(region) != null && api.getForceProfileRegion(region) != region)
                ? status.getFlags().add(Status.Flag.REDIRECTED)
                : status.getFlags().remove(Status.Flag.REDIRECTED);
    }

    private boolean updatePrioritizedFlag(Region region, Status status)
    {
        return statsService.isPartialUpdate(region)
            ? status.getFlags().add(Status.Flag.PRIORITIZED)
            : status.getFlags().remove(Status.Flag.PRIORITIZED);
    }

    private boolean updatePrioritized2Flag(Region region, Status status)
    {
        return statsService.isPartialUpdate2(region)
            ? status.getFlags().add(Status.Flag.PRIORITIZED_2)
            : status.getFlags().remove(Status.Flag.PRIORITIZED_2);
    }

    private boolean updateWebFlag(Region region, Status status)
    {
        return alternativeLadderService.isAutoWeb(region)
            || alternativeLadderService.isProfileLadderWebRegion(region)
            || alternativeLadderService.isDiscoveryWebRegion(region)
            || matchService.isWeb(region)
                ? status.getFlags().add(Status.Flag.WEB)
                : status.getFlags().remove(Status.Flag.WEB);
    }

    private boolean updateOperationalFlag(Region region, Status status)
    {
        return status.getFlags().stream().anyMatch(f->f != Status.Flag.OPERATIONAL)
            ? status.getFlags().remove(Status.Flag.OPERATIONAL)
            : status.getFlags().add(Status.Flag.OPERATIONAL);
    }

    private void updateDuration(Region region, Status status)
    {
        Duration refreshDuration = status.getFlags().contains(Status.Flag.WEB)
                && AlternativeLadderService.ADDITIONAL_WEB_SCAN_TIME_FRAME != Duration.ZERO
                    ? AlternativeLadderService.ADDITIONAL_WEB_SCAN_TIME_FRAME
                    : updateService.calculateUpdateDuration(null);
        status.setRefreshDuration(refreshDuration);
    }

    public Map<Region, Status> getStatusMap()
    {
        return statusMap;
    }

    public Long getPlayers()
    {
        return players;
    }

    public Long getTeamSnapshots()
    {
        return teamSnapshots;
    }

    public Long getMatches()
    {
        return matches;
    }

}
