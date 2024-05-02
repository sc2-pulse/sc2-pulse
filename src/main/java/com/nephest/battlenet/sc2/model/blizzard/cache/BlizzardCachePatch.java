// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.blizzard.cache;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nephest.battlenet.sc2.config.convert.jackson.EpochMilliToOffsetDateTimeConverter;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public class BlizzardCachePatch
{

    @NotNull
    public Long buildNumber;

    @NotNull
    private String version;

    @NotNull @JsonDeserialize(converter = EpochMilliToOffsetDateTimeConverter.class)
    private OffsetDateTime publish;

    public Long getBuildNumber()
    {
        return buildNumber;
    }

    public void setBuildNumber(Long buildNumber)
    {
        this.buildNumber = buildNumber;
    }

    public String getVersion()
    {
        return version;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

    public OffsetDateTime getPublish()
    {
        return publish;
    }

    public void setPublish(OffsetDateTime publish)
    {
        this.publish = publish;
    }

}
