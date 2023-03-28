// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.external;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.arcade.ArcadePlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import com.nephest.battlenet.sc2.web.service.SC2ArcadeAPI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ExternalBattleNetCharacterLinkResolver
implements ExternalCharacterLinkResolver
{

    private final SC2ArcadeAPI api;
    private final ConversionService conversionService;

    @Autowired
    public ExternalBattleNetCharacterLinkResolver
    (
        SC2ArcadeAPI api,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.api = api;
        this.conversionService = conversionService;
    }

    @Override
    public Mono<PlayerCharacterLink> resolveLink(PlayerCharacter playerCharacter)
    {
        return api.findCharacter(playerCharacter)
            .mapNotNull(arcadePlayer->extractLink(playerCharacter, arcadePlayer));
    }

    @Override
    public SocialMedia getSupportedSocialMedia()
    {
        return SocialMedia.BATTLE_NET;
    }

    private PlayerCharacterLink extractLink
    (
        PlayerCharacter character,
        ArcadePlayerCharacter arcadeCharacter
    )
    {
        if(arcadeCharacter.getProfileGameId() == null) return null;
        return new PlayerCharacterLink
        (
            character.getId(),
            SocialMedia.BATTLE_NET,
            extractRelativeUrl(arcadeCharacter)
        );
    }

    public String extractRelativeUrl(ArcadePlayerCharacter character)
    {
        return extractRelativeUrl(character.getRegion(), character.getProfileGameId());
    }

    public String extractRelativeUrl(Region region, Long gameId)
    {
        return conversionService.convert(region, Integer.class)
            + "/" + Long.toUnsignedString(Long.reverseBytes(gameId));
    }

}
