// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.external;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import java.util.List;
import java.util.Set;

public record ExternalLinkResolveResult
(
    List<PlayerCharacterLink> links,
    Set<SocialMedia> failedTypes
)
{

}
