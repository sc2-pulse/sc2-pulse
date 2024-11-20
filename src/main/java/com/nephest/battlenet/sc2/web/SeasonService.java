// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web;

import com.nephest.battlenet.sc2.model.local.Season;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SeasonService
{

    private final SeasonDAO seasonDAO;
    private SeasonService service;

    @Autowired
    public SeasonService
    (
        SeasonDAO seasonDAO,
        @Lazy SeasonService service
    )
    {
        this.seasonDAO = seasonDAO;
        this.service = service;
    }

    protected SeasonService getService()
    {
        return service;
    }

    protected void setService(SeasonService service)
    {
        this.service = service;
    }

    public Season merge(Season season)
    {
        Season existing = seasonDAO.find(season.getRegion(), season.getBattlenetId())
            .orElse(null);
        if(existing == null || !haveEqualDates(season, existing))
            return service.update(season);

        if(!haveEqualYearAndNumber(season, existing))
            return seasonDAO.merge(season);

        if(season.getId() == null) season.setId(existing.getId());
        return season;
    }

    @Transactional
    public Season update(Season season)
    {
        seasonDAO.find(season.getRegion(), season.getBattlenetId() - 1)
            .ifPresent(previous->{
                if(!previous.getEnd().isEqual(season.getStart()))
                {
                    previous.setEnd(season.getStart());
                    seasonDAO.merge(previous);
                }
            });
        seasonDAO.find(season.getRegion(), season.getBattlenetId() + 1)
            .ifPresent(next->season.setEnd(next.getStart()));
        return seasonDAO.merge(season);
    }

    private static boolean haveEqualDates(Season l, Season r)
    {
        return l.getStart().isEqual(r.getStart())
            && l.getEnd().isEqual(r.getEnd());
    }

    private static boolean haveEqualYearAndNumber(Season l, Season r)
    {
        return l.getNumber().equals(r.getNumber())
            && l.getYear().equals(r.getYear());
    }

}
