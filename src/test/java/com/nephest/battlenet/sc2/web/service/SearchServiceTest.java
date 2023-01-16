// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.convert.ConversionService;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
public class SearchServiceTest
{

    @Mock
    private PlayerCharacterDAO playerCharacterDAO;

    @Mock
    private AccountDAO accountDAO;

    @Mock
    private ClanDAO clanDAO;

    @Mock
    private LadderCharacterDAO ladderCharacterDAO;

    @Mock
    private SC2ArcadeAPI sc2ArcadeAPI;

    @Mock
    private ConversionService conversionService;

    private SearchService searchService;

    @BeforeEach
    public void beforeEach()
    {
        searchService = new SearchService
        (
            playerCharacterDAO,
            accountDAO,
            clanDAO,
            ladderCharacterDAO,
            sc2ArcadeAPI,
            conversionService
        );
    }

    @CsvSource
    ({
        "c, GENERAL",
        "[c, CLAN",
        "c#, BATTLE_TAG"
    })
    @ParameterizedTest
    public void testSearchTypeOf(String term, SearchService.SearchType expectedResult)
    {
        assertEquals(expectedResult, SearchService.SearchType.of(term));
    }

    @CsvSource
    ({
        ",",
        "'', ''",
        "[, ''",
        "[c, c",
        "[cl], cl"
    })
    @ParameterizedTest
    public void testExtractClanSearchTerm(String term, String expectedResult)
    {
        assertEquals(expectedResult, SearchService.extractClanTag(term));
    }


    @ValueSource(strings =
    {
        "battlenet://starcraft/profile/2/10",
        "battlenet:://starcraft/profile/2/10"
    })
    @ParameterizedTest
    public void whenArcadeApiThrowsException_thenReturnEmptyList(String link)
    {
        when(sc2ArcadeAPI.findByRegionAndGameId(any(), anyLong()))
            .thenReturn(Mono.error(new IllegalStateException("test exception")));
        assertTrue(searchService.findDistinctCharacters(link)
            .isEmpty());
    }

    @ValueSource(strings =
    {
        "battlenet://starcraft/profile/2/9223372036854775808",
        "battlenet:://starcraft/profile/2/9223372036854775808"
    })
    @ParameterizedTest
    public void whenSearchByGameLink_thenTranslateToRegularProfileViaArcadeAPI(String link)
    {
        when(conversionService.convert(2, Region.class)).thenReturn(Region.EU);
        when(sc2ArcadeAPI.findByRegionAndGameId(Region.EU, 128L)) //reversed unsigned long
            .thenReturn(Mono.just(new BlizzardFullPlayerCharacter(1L, 5, "name", Region.EU)));
        List<LadderDistinctCharacter> characters = new ArrayList<>();
        characters.add(null);
        when(ladderCharacterDAO.findDistinctCharacters("/2/5/1")).thenReturn(characters);

        //unsigned long test
        assertEquals(characters, searchService.findDistinctCharacters(link));
    }

}
