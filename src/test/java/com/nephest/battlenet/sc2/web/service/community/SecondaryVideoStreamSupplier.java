// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import com.nephest.battlenet.sc2.model.SocialMedia;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class SecondaryVideoStreamSupplier
implements VideoStreamSupplier
{

    @Override
    public SocialMedia getService()
    {
        return SocialMedia.UNKNOWN;
    }

    @Override
    public Flux<VideoStream> getStreams()
    {
        return Flux.empty();
    }

}
