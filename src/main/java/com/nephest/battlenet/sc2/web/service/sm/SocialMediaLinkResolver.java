// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.sm;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import java.util.Collection;
import reactor.core.publisher.Mono;

public interface SocialMediaLinkResolver
{

    Mono<Void> resolve(Collection<? extends SocialMediaLink> link);

    SocialMedia getSupportedSocialMedia();

}
