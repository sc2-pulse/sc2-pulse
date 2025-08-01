// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.inner.VersusSummary;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import java.util.List;

public class Versus
{

    private List<LadderTeam> teamsGroup1;
    private List<Clan> clansGroup1;

    private List<LadderTeam> teamsGroup2;
    private List<Clan> clansGroup2;

    private VersusSummary summary;
    private CursorNavigableResult<List<LadderMatch>> matches;

    public Versus(){}

    public Versus
    (
        List<LadderTeam> teamsGroup1,
        List<Clan> clansGroup1,
        List<LadderTeam> teamsGroup2,
        List<Clan> clansGroup2,
        VersusSummary summary,
        CursorNavigableResult<List<LadderMatch>> matches
    )
    {
        this.teamsGroup1 = teamsGroup1;
        this.clansGroup1 = clansGroup1;
        this.teamsGroup2 = teamsGroup2;
        this.clansGroup2 = clansGroup2;
        this.summary = summary;
        this.matches = matches;
    }

    public List<LadderTeam> getTeamsGroup1()
    {
        return teamsGroup1;
    }

    public void setTeamsGroup1(List<LadderTeam> teamsGroup1)
    {
        this.teamsGroup1 = teamsGroup1;
    }

    public List<Clan> getClansGroup1()
    {
        return clansGroup1;
    }

    public void setClansGroup1(List<Clan> clansGroup1)
    {
        this.clansGroup1 = clansGroup1;
    }

    public List<LadderTeam> getTeamsGroup2()
    {
        return teamsGroup2;
    }

    public void setTeamsGroup2(List<LadderTeam> teamsGroup2)
    {
        this.teamsGroup2 = teamsGroup2;
    }

    public List<Clan> getClansGroup2()
    {
        return clansGroup2;
    }

    public void setClansGroup2(List<Clan> clansGroup2)
    {
        this.clansGroup2 = clansGroup2;
    }

    public VersusSummary getSummary()
    {
        return summary;
    }

    public void setSummary(VersusSummary summary)
    {
        this.summary = summary;
    }

    public CursorNavigableResult<List<LadderMatch>> getMatches()
    {
        return matches;
    }

    public void setMatches(CursorNavigableResult<List<LadderMatch>> matches)
    {
        this.matches = matches;
    }

}
