// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import com.nephest.battlenet.sc2.model.SocialMedia;
import java.util.List;
import java.util.Set;

public class CommunityStreamResult
{

    private final List<LadderVideoStream> streams;
    private final Set<SocialMedia> errors;

    public CommunityStreamResult(List<LadderVideoStream> streams, Set<SocialMedia> errors)
    {
        this.streams = streams;
        this.errors = errors;
    }

    public List<LadderVideoStream> getStreams()
    {
        return streams;
    }

    public Set<SocialMedia> getErrors()
    {
        return errors;
    }

}
