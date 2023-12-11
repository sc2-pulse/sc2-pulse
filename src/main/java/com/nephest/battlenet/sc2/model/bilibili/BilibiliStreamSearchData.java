// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.bilibili;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BilibiliStreamSearchData
{

    @JsonProperty("list")
    private List<BilibiliStream> streams;

    private Long hasMore;

    public BilibiliStreamSearchData()
    {
    }

    public BilibiliStreamSearchData(List<BilibiliStream> streams, Long hasMore)
    {
        this.streams = streams;
        this.hasMore = hasMore;
    }

    public List<BilibiliStream> getStreams()
    {
        return streams;
    }

    public void setStreams(List<BilibiliStream> streams)
    {
        this.streams = streams;
    }

    public Long getHasMore()
    {
        return hasMore;
    }

    public void setHasMore(Long hasMore)
    {
        this.hasMore = hasMore;
    }

}
