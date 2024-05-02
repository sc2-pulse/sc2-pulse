// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.twitch;

import com.github.twitch4j.helix.domain.Video;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;

public class TwitchVideo
{

    @NotNull
    private Long id;

    @NotNull
    private Long twitchUserId;

    @NotNull
    private String url;

    @NotNull
    private OffsetDateTime begin;

    @NotNull
    private OffsetDateTime end;

    public TwitchVideo(){}

    public TwitchVideo(Long id, Long twitchUserId, String url, OffsetDateTime begin, OffsetDateTime end)
    {
        this.id = id;
        this.twitchUserId = twitchUserId;
        this.url = url;
        this.begin = begin;
        this.end = end;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof TwitchVideo that)) {return false;}
        return id.equals(that.id);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(id);
    }

    @Override
    public String toString()
    {
        return "TwitchVideo{" + "id=" + id + '}';
    }

    public static TwitchVideo of(Video video)
    {
        OffsetDateTime start = video.getCreatedAtInstant().atOffset(SC2Pulse.offsetDateTime().getOffset());
        return new TwitchVideo
        (
            Long.parseLong(video.getId()),
            Long.parseLong(video.getUserId()),
            video.getUrl(),
            start,
            start.plus(Duration.parse("PT" + video.getDuration().toUpperCase()))
        );
    }

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getTwitchUserId()
    {
        return twitchUserId;
    }

    public void setTwitchUserId(Long twitchUserId)
    {
        this.twitchUserId = twitchUserId;
    }

    public String getUrl()
    {
        return url;
    }

    public void setUrl(String url)
    {
        this.url = url;
    }

    public OffsetDateTime getBegin()
    {
        return begin;
    }

    public void setBegin(OffsetDateTime begin)
    {
        this.begin = begin;
    }

    public OffsetDateTime getEnd()
    {
        return end;
    }

    public void setEnd(OffsetDateTime end)
    {
        this.end = end;
    }

}
