// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.Evidence;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class LadderEvidence
{

    @NotNull
    private final Evidence evidence;

    @NotNull
    private final List<LadderEvidenceVote> votes;

    private final Account reporterAccount;

    public LadderEvidence(Evidence evidence, List<LadderEvidenceVote> votes, Account reporterAccount)
    {
        this.evidence = evidence;
        this.votes = votes;
        this.reporterAccount = reporterAccount;
    }

    public Evidence getEvidence()
    {
        return evidence;
    }

    public List<LadderEvidenceVote> getVotes()
    {
        return votes;
    }

    public Account getReporterAccount()
    {
        return reporterAccount;
    }

}
