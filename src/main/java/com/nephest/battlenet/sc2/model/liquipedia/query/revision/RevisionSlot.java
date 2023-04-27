// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.liquipedia.query.revision;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy.class)
public class RevisionSlot
{

    private String contentModel, contentFormat, content;

    public RevisionSlot()
    {
    }

    public RevisionSlot(String contentModel, String contentFormat, String content)
    {
        this.contentModel = contentModel;
        this.contentFormat = contentFormat;
        this.content = content;
    }

    public String getContentModel()
    {
        return contentModel;
    }

    public void setContentModel(String contentModel)
    {
        this.contentModel = contentModel;
    }

    public String getContentFormat()
    {
        return contentFormat;
    }

    public void setContentFormat(String contentFormat)
    {
        this.contentFormat = contentFormat;
    }

    public String getContent()
    {
        return content;
    }

    public void setContent(String content)
    {
        this.content = content;
    }

}
