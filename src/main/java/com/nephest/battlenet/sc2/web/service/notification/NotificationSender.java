// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.notification;

import com.nephest.battlenet.sc2.model.local.Notification;
import reactor.core.publisher.Mono;

public interface NotificationSender
{

    Mono<Notification> send(Notification notification);

}
