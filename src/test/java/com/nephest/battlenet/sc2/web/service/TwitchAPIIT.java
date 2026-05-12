// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.web.service.community.TwitchVideoStreamSupplier.SC2_GAME_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.extension.AutoConfigureDatabase;
import com.nephest.battlenet.sc2.model.twitch.dto.TwitchStreamDto;
import com.nephest.battlenet.sc2.model.twitch.dto.TwitchUserDto;
import com.nephest.battlenet.sc2.model.twitch.dto.TwitchVideoDto;
import com.nephest.battlenet.sc2.twitch.TwitchTest;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Flux;

@TwitchTest
@SpringBootTest(classes = {AllTestConfig.class})
@TestPropertySource("classpath:application.properties")
@AutoConfigureDatabase(AutoConfigureDatabase.ExecutionPhase.CLASS)
public class TwitchAPIIT
{

    private static TwitchAPI api;

    @BeforeAll
    public static void beforeAll
    (
        @Autowired TwitchAPI api
    )
    throws Exception
    {
        TwitchAPIIT.api = api;
    }

    public static Stream<Arguments> whenExceedingMaxUserBatchSize_thenSplitRequestOnSubBatches()
    {
        return Stream.of
        (
            Arguments.of("132530558", (Function<Set<String>, Flux<TwitchUserDto>>) api::getUsersByIds),
            Arguments.of("nephest0x", (Function<Set<String>, Flux<TwitchUserDto>>) api::getUsersByLogins)
        );
    }

    @MethodSource
    @ParameterizedTest
    public void whenExceedingMaxUserBatchSize_thenSplitRequestOnSubBatches
    (
        String val,
        Function<Set<String>, Flux<TwitchUserDto>> function
    )
    {
        Set<String> largeBatch = Stream.concat
        (
            IntStream.range(0, TwitchAPI.USER_BATCH_SIZE)
                .boxed()
                .map(String::valueOf),
            Stream.of(val)
        )
            .collect(Collectors.toSet());
        List<TwitchUserDto> users = function.apply(largeBatch)
            .collectList()
            .block();
        assertTrue(users.stream().anyMatch(
            u->u.login().equals("nephest0x")
                && u.id().equals("132530558"))
        );
    }

    @Test
    public void testGetStreamsByGameId()
    {
        List<TwitchStreamDto> streams = api
            .getStreamsByGameId(SC2_GAME_ID, 100)
            .collectList()
            .block();
        assertFalse(streams.isEmpty());
        /*
            Twitch API returns other games sometimes. Ensure that at least *some* streams are
            related to the target game.
         */
        assertTrue(streams.stream().anyMatch(s->s.gameId().equals(SC2_GAME_ID)));
    }

    @Test
    public void testGetVideosByUserId()
    {
        String userId = "21635116"; //heromarine
        TwitchVideoDto.Type type = TwitchVideoDto.Type.ARCHIVE;
        List<TwitchVideoDto> videos = api.getVideosByUserId(userId, type, 10).collectList().block();
        assertFalse(videos.isEmpty());
        assertTrue(videos.stream().allMatch(s->
            s.userId().equals(userId) && s.type() == type));
    }

}
