// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

public class Evidence
implements java.io.Serializable
{

    private static final long serialVersionUID = 2L;
    public static final int MAX_LENGTH = 2000;

    private Integer id;

    @NotNull
    private Integer playerCharacterReportId;

    private Long reporterAccountId;

    private byte[] reporterIp;

    @NotNull @Size(max=MAX_LENGTH)
    private String description;

    private Boolean status;

    @NotNull
    private OffsetDateTime statusChangeDateTime;

    @NotNull
    private OffsetDateTime created;

    @NotNull
    private Boolean archived;

    public Evidence(){}

    public Evidence
    (
        Integer playerCharacterReportId,
        Long reporterAccountId,
        byte[] reporterIp,
        String description,
        Boolean status,
        OffsetDateTime statusChangeDateTime,
        OffsetDateTime created
    )
    {
        this
        (
            null,
            playerCharacterReportId,
            reporterAccountId,
            reporterIp,
            description,
            status,
            statusChangeDateTime,
            created,
            false
        );
    }

    public Evidence
    (
        Integer id,
        Integer playerCharacterReportId,
        Long reporterAccountId,
        byte[] reporterIp,
        String description,
        Boolean status,
        OffsetDateTime statusChangeDateTime,
        OffsetDateTime created,
        Boolean archived
    )
    {
        this.id = id;
        this.playerCharacterReportId = playerCharacterReportId;
        this.reporterAccountId = reporterAccountId;
        this.reporterIp = reporterIp;
        this.description = description;
        this.status = status;
        this.statusChangeDateTime = statusChangeDateTime;
        this.created = created;
        this.archived = archived;
    }

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Integer getPlayerCharacterReportId()
    {
        return playerCharacterReportId;
    }

    public void setPlayerCharacterReportId(Integer playerCharacterReportId)
    {
        this.playerCharacterReportId = playerCharacterReportId;
    }

    public Long getReporterAccountId()
    {
        return reporterAccountId;
    }

    public void setReporterAccountId(Long reporterAccountId)
    {
        this.reporterAccountId = reporterAccountId;
    }

    public byte[] getReporterIp()
    {
        return reporterIp;
    }

    public void setReporterIp(byte[] reporterIp)
    {
        this.reporterIp = reporterIp;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public Boolean getStatus()
    {
        return status;
    }

    public void setStatus(Boolean status)
    {
        this.status = status;
    }

    public OffsetDateTime getStatusChangeDateTime()
    {
        return statusChangeDateTime;
    }

    public void setStatusChangeDateTime(OffsetDateTime statusChangeDateTime)
    {
        this.statusChangeDateTime = statusChangeDateTime;
    }

    public OffsetDateTime getCreated()
    {
        return created;
    }

    public void setCreated(OffsetDateTime created)
    {
        this.created = created;
    }

    public Boolean getArchived()
    {
        return archived;
    }

    public void setArchived(Boolean archived)
    {
        this.archived = archived;
    }

}
