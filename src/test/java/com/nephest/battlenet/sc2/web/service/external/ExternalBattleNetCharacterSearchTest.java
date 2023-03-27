// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.external;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.arcade.ArcadePlayerCharacter;
import com.nephest.battlenet.sc2.web.service.SC2ArcadeAPI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class ExternalBattleNetCharacterSearchTest
{

    @Mock
    private SC2ArcadeAPI sc2ArcadeAPI;

    @Mock
    private ConversionService conversionService;

    private ExternalBattleNetCharacterSearch search;

    @BeforeEach
    public void beforeEach()
    {
        search = new ExternalBattleNetCharacterSearch(sc2ArcadeAPI, conversionService);
    }

    @Test
    public void testGameIdUnsignedLongParsing()
    {
        ArcadePlayerCharacter character
            = new ArcadePlayerCharacter(1L, 5, "name", Region.EU, 128L);
        when(conversionService.convert(2, Region.class)).thenReturn(Region.EU);
        when(sc2ArcadeAPI.findByRegionAndGameId(Region.EU, 128L)) //reversed unsigned long
            .thenReturn(Mono.just(character));
        //original unsigned long
        assertEquals(character, search.find("battlenet:://starcraft/profile/2/9223372036854775808").block());
    }

}
