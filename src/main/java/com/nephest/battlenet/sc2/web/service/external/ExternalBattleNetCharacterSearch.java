// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.external;

import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.web.service.SC2ArcadeAPI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ExternalBattleNetCharacterSearch
implements ExternalCharacterSearch
{

    private final SC2ArcadeAPI arcadeAPI;
    private final ConversionService conversionService;

    public ExternalBattleNetCharacterSearch
    (
        SC2ArcadeAPI arcadeAPI,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.arcadeAPI = arcadeAPI;
        this.conversionService = conversionService;
    }

    @Override
    public Mono<? extends PlayerCharacterNaturalId> find(String url)
    {
        String[] split = url.split("/");
        if(split.length < 2) throw new IllegalArgumentException("Invalid profile link");

        Region region = conversionService.convert(Integer.parseInt(split[split.length - 2]), Region.class);
        long gameId = Long.reverseBytes(Long.parseUnsignedLong(split[split.length - 1]));
        return arcadeAPI.findByRegionAndGameId(region, gameId);
    }

    @Override
    public SocialMedia getSupportedSocialMedia()
    {
        return SocialMedia.BATTLE_NET;
    }

}
