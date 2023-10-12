// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nephest.battlenet.sc2.model.SocialMedia;
import java.util.Locale;

@JsonDeserialize(as = VideoStreamImpl.class)
public interface VideoStream
{

    SocialMedia getService();

    String getId();

    String getUserId();

    String getUserName();

    String getTitle();

    Locale getLanguage();

    String getUrl();

    String getProfileImageUrl();

    String getThumbnailUrl();

    int getViewerCount();

}
