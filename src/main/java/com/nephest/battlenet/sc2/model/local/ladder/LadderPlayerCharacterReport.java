// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.PlayerCharacterReport;

import javax.validation.constraints.NotNull;
import java.util.List;

public class LadderPlayerCharacterReport
{

    @NotNull
    private LadderTeamMember member;

    @NotNull
    private LadderTeamMember additionalMember;

    @NotNull
    private PlayerCharacterReport report;

    @NotNull
    private List<LadderEvidence> evidence;

    public LadderPlayerCharacterReport(){}

    public LadderPlayerCharacterReport(LadderTeamMember member, PlayerCharacterReport report)
    {
        this.member = member;
        this.report = report;
    }

    public LadderPlayerCharacterReport
    (LadderTeamMember member, PlayerCharacterReport report, List<LadderEvidence> evidence)
    {
        this.member = member;
        this.report = report;
        this.evidence = evidence;
    }

    public LadderTeamMember getMember()
    {
        return member;
    }

    public void setMember(LadderTeamMember member)
    {
        this.member = member;
    }

    public LadderTeamMember getAdditionalMember()
    {
        return additionalMember;
    }

    public void setAdditionalMember(LadderTeamMember additionalMember)
    {
        this.additionalMember = additionalMember;
    }

    public PlayerCharacterReport getReport()
    {
        return report;
    }

    public void setReport(PlayerCharacterReport report)
    {
        this.report = report;
    }

    public List<LadderEvidence> getEvidence()
    {
        return evidence;
    }

    public void setEvidence(List<LadderEvidence> evidence)
    {
        this.evidence = evidence;
    }

}
