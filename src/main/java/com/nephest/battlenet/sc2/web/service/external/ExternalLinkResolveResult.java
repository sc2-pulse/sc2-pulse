// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.external;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import java.util.List;
import java.util.Set;

public class ExternalLinkResolveResult
{

    private final List<PlayerCharacterLink> links;
    private final Set<SocialMedia> failedTypes;

    public ExternalLinkResolveResult(List<PlayerCharacterLink> links, Set<SocialMedia> failedTypes)
    {
        this.links = links;
        this.failedTypes = failedTypes;
    }

    public List<PlayerCharacterLink> getLinks()
    {
        return links;
    }

    public Set<SocialMedia> getFailedTypes()
    {
        return failedTypes;
    }

}
