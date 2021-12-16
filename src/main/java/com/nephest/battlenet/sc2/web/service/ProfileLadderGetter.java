// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileLadder;
import reactor.core.publisher.Mono;

import java.util.Set;

public interface ProfileLadderGetter
{

    Mono<BlizzardProfileLadder> getProfileLadderMono
    (Region region, BlizzardPlayerCharacter character, long id, Set<QueueType> queueTypes);

}
