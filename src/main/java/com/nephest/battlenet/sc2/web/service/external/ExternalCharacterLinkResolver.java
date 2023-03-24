// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.external;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import reactor.core.publisher.Mono;

public interface ExternalCharacterLinkResolver
{

    Mono<PlayerCharacterLink> resolveLink(PlayerCharacter playerCharacter);

    SocialMedia getSupportedSocialMedia();

    default boolean isStatic()
    {
        return true;
    }

}
