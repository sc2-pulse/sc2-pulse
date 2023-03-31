// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.external;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import com.nephest.battlenet.sc2.model.replaystats.ReplayStats;
import com.nephest.battlenet.sc2.model.replaystats.ReplayStatsPlayerCharacter;
import com.nephest.battlenet.sc2.web.service.SC2ReplayStatsAPI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@ReplayStats
public class ExternalReplayStatsCharacterLinkResolver
implements ExternalCharacterLinkResolver
{

    private final SC2ReplayStatsAPI api;

    @Autowired
    public ExternalReplayStatsCharacterLinkResolver(SC2ReplayStatsAPI api)
    {
        this.api = api;
    }

    @Override
    public Mono<PlayerCharacterLink> resolveLink(PlayerCharacter playerCharacter)
    {
        return api.findCharacter(playerCharacter)
            .mapNotNull(replayStatsCharacter->extractLink(playerCharacter, replayStatsCharacter));
    }

    @Override
    public SocialMedia getSupportedSocialMedia()
    {
        return SocialMedia.REPLAY_STATS;
    }

    private PlayerCharacterLink extractLink
    (
        PlayerCharacter character,
        ReplayStatsPlayerCharacter replayStatsPlayerCharacter
    )
    {
        if(replayStatsPlayerCharacter.getReplayStatsId() == null) return null;

        return new PlayerCharacterLink
        (
            character.getId(),
            SocialMedia.REPLAY_STATS,
            extractRelativeUrl(replayStatsPlayerCharacter)
        );
    }

    public static String extractRelativeUrl(ReplayStatsPlayerCharacter character)
    {
        return String.valueOf(character.getReplayStatsId());
    }

}
