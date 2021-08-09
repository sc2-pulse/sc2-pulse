// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder;

import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.EvidenceVote;

import javax.validation.constraints.NotNull;

public class LadderEvidenceVote
{

    @NotNull
    private final EvidenceVote vote;

    @NotNull
    private final Account voterAccount;

    public LadderEvidenceVote(EvidenceVote vote, Account voterAccount)
    {
        this.vote = vote;
        this.voterAccount = voterAccount;
    }

    public EvidenceVote getVote()
    {
        return vote;
    }

    public Account getVoterAccount()
    {
        return voterAccount;
    }

}
