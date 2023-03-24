// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.external;

import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.SocialMedia;
import reactor.core.publisher.Mono;

public interface ExternalCharacterSearch
{

    Mono<? extends PlayerCharacterNaturalId> find(String url);

    SocialMedia getSupportedSocialMedia();

    default boolean isStatic()
    {
        return true;
    }

}
