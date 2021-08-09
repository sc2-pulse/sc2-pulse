// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import javax.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.Objects;

public class EvidenceVote
implements java.io.Serializable
{

    private static final long serialVersionUID = 1L;

    @NotNull
    private Integer evidenceId;

    @NotNull
    private OffsetDateTime evidenceCreated;

    @NotNull
    private Long voterAccountId;

    @NotNull
    private Boolean vote;

    @NotNull
    private OffsetDateTime updated;

    public EvidenceVote(){}

    public EvidenceVote
    (Integer evidenceId, OffsetDateTime evidenceCreated, Long voterAccountId, Boolean vote, OffsetDateTime updated)
    {
        this.evidenceId = evidenceId;
        this.evidenceCreated = evidenceCreated;
        this.voterAccountId = voterAccountId;
        this.vote = vote;
        this.updated = updated;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof EvidenceVote)) return false;
        EvidenceVote that = (EvidenceVote) o;
        return evidenceId.equals(that.evidenceId) && voterAccountId.equals(that.voterAccountId);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(evidenceId, voterAccountId);
    }

    @Override
    public String toString()
    {
        return "EvidenceVote{" + "evidenceId=" + evidenceId + ", voterAccountId=" + voterAccountId + '}';
    }

    public Integer getEvidenceId()
    {
        return evidenceId;
    }

    public void setEvidenceId(Integer evidenceId)
    {
        this.evidenceId = evidenceId;
    }

    public OffsetDateTime getEvidenceCreated()
    {
        return evidenceCreated;
    }

    public void setEvidenceCreated(OffsetDateTime evidenceCreated)
    {
        this.evidenceCreated = evidenceCreated;
    }

    public Long getVoterAccountId()
    {
        return voterAccountId;
    }

    public void setVoterAccountId(Long voterAccountId)
    {
        this.voterAccountId = voterAccountId;
    }

    public Boolean getVote()
    {
        return vote;
    }

    public void setVote(Boolean vote)
    {
        this.vote = vote;
    }

    public OffsetDateTime getUpdated()
    {
        return updated;
    }

    public void setUpdated(OffsetDateTime updated)
    {
        this.updated = updated;
    }

}
