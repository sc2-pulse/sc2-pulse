// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.web.service.community.TwitchVideoStreamSupplier.SC2_GAME_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.twitch4j.helix.domain.User;
import com.github.twitch4j.helix.domain.Video;
import com.nephest.battlenet.sc2.config.AllTestConfig;
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
@TestPropertySource("classpath:application-private.properties")
public class TwitchAPIIT
{

    private static TwitchAPI api;

    @BeforeAll
    public static void beforeAll(@Autowired TwitchAPI api)
    {
        TwitchAPIIT.api = api;
    }

    public static Stream<Arguments> whenExceedingMaxUserBatchSize_thenSplitRequestOnSubBatches()
    {
        return Stream.of
        (
            Arguments.of("132530558", (Function<Set<String>, Flux<User>>) api::getUsersByIds),
            Arguments.of("nephest0x", (Function<Set<String>, Flux<User>>) api::getUsersByLogins)
        );
    }

    @MethodSource
    @ParameterizedTest
    public void whenExceedingMaxUserBatchSize_thenSplitRequestOnSubBatches
    (
        String val,
        Function<Set<String>, Flux<User>> function
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
        List<User> users = function.apply(largeBatch)
            .collectList()
            .block();
        assertTrue(users.stream().anyMatch(
            u->u.getLogin().equals("nephest0x")
                && u.getId().equals("132530558"))
        );
    }

    @Test
    public void testGetStreamsByGameId()
    {
        List<com.github.twitch4j.helix.domain.Stream> streams = api
            .getStreamsByGameId(SC2_GAME_ID, 100)
            .collectList()
            .block();
        assertFalse(streams.isEmpty());
        assertTrue(streams.stream().allMatch(s->s.getGameId().equals(SC2_GAME_ID)));
    }

    @Test
    public void testGetVideosByUserId()
    {
        String userId = "21635116"; //heromarine
        Video.Type type = Video.Type.ARCHIVE;
        List<Video> videos = api.getVideosByUserId(userId, type, 10).collectList().block();
        assertFalse(videos.isEmpty());
        assertTrue(videos.stream().allMatch(s->
            s.getUserId().equals(userId) && s.getType().equalsIgnoreCase(type.toString())));
    }

}
