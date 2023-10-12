// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nephest.battlenet.sc2.config.convert.jackson.LanguageStringToLocaleConverter;
import com.nephest.battlenet.sc2.config.convert.jackson.LocaleToBCP47StringConverter;
import com.nephest.battlenet.sc2.model.SocialMedia;
import java.util.Locale;
import java.util.Objects;

public class VideoStreamImpl
implements VideoStream
{

    private final SocialMedia service;
    private final String id, userId, userName, title, url, profileImageUrl, thumbnailUrl;

    @JsonSerialize(converter = LocaleToBCP47StringConverter.class)
    @JsonDeserialize(converter = LanguageStringToLocaleConverter.class)
    private final Locale language;
    private final int viewerCount;

    public VideoStreamImpl
    (
        SocialMedia service,
        String id,
        String userId,
        String userName,
        String title,
        Locale language,
        String url,
        String profileImageUrl,
        String thumbnailUrl,
        int viewerCount
    )
    {
        this.id = id;
        this.service = service;
        this.userId = userId;
        this.userName = userName;
        this.title = title;
        this.language = language;
        this.url = url;
        this.profileImageUrl = profileImageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.viewerCount = viewerCount;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof VideoStreamImpl)) {return false;}
        VideoStreamImpl that = (VideoStreamImpl) o;
        return getService() == that.getService() && Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getService(), getId());
    }

    @Override
    public String getId()
    {
        return id;
    }

    @Override
    public SocialMedia getService()
    {
        return service;
    }

    @Override
    public String getUserId()
    {
        return userId;
    }

    @Override
    public String getUserName()
    {
        return userName;
    }

    @Override
    public String getTitle()
    {
        return title;
    }

    @Override
    public Locale getLanguage()
    {
        return language;
    }

    @Override
    public String getUrl()
    {
        return url;
    }

    @Override
    public String getProfileImageUrl()
    {
        return profileImageUrl;
    }

    @Override
    public String getThumbnailUrl()
    {
        return thumbnailUrl;
    }

    @Override
    public int getViewerCount()
    {
        return viewerCount;
    }

}
