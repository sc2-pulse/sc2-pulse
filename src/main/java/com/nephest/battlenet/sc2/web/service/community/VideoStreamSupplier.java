// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import com.nephest.battlenet.sc2.model.SocialMedia;
import reactor.core.publisher.Flux;

public interface VideoStreamSupplier
{

    SocialMedia getService();

    Flux<VideoStream> getStreams();

}
