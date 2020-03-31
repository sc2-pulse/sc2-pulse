/*-
 * =========================LICENSE_START=========================
 * SC2 Ladder Generator
 * %%
 * Copyright (C) 2020 Oleksandr Masniuk
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * =========================LICENSE_END=========================
 */
package com.nephest.battlenet.sc2.web.service.blizzard;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardAccessToken;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeague;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTierDivision;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class BlizzardSC2APIIT
{

    public static final int RETRY_COUNT = 2;
    public static final String VALID_ACCESS_TOKEN = "{\"access_token\":\"fdfgkjheufh\",\"token_type\":\"bearer\",\"expires_in\":86399}";
    public static final String VALID_SEASON = "{\"seasonId\": 1, \"year\": 2010, \"number\": 1}";
    public static final String VALID_LEAGUE = "{\"type\": 0, \"queueType\": 201, \"teamType\": 0, \"tier\": []}";
    public static final String VALID_LADDER = "{\"team\": []}";

    private static BlizzardSC2API api;

    @BeforeEach
    public void beforeEach()
    {
        api = new BlizzardSC2API("password");
        BlizzardAccessToken token = mock(BlizzardAccessToken.class);
        when(token.isValid()).thenReturn(true);
        api.setAccessToken(token);
    }

    @Test
    public void testRetrying()
    throws Exception
    {
        MockWebServer server = new MockWebServer();
        server.start();
        api.setRegionUri(server.url("/someurl").uri().toString());

        testRetrying(api.getCurrentSeason(Region.EU), VALID_SEASON, server, RETRY_COUNT);
        testRetrying
        (
            api.getLeague
            (
                Region.EU,
                mock(BlizzardSeason.class),
                BlizzardLeague.LeagueType.BRONZE,
                QueueType.LOTV_1V1,
                TeamType.ARRANGED
            ),
            VALID_LEAGUE, server, RETRY_COUNT
        );
        testRetrying(api.getLadder(Region.EU, mock(BlizzardTierDivision.class)), VALID_LADDER, server, RETRY_COUNT);
        server.shutdown();
    }

    private void testRetrying(Mono mono, String body, MockWebServer server, int count)
    throws Exception
    {
        testRetryingOnErrorCodes(mono, body, server, count);
        testRetryingOnMalformedBody(mono, body, server, count);
        testRetryingOnTimeout(mono, body, server, count);
    }

    private void testRetryingOnErrorCodes(Mono mono, String body, MockWebServer server, int count)
    throws Exception
    {
        for(int i = 0; i < count; i++) server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));

        StepVerifier.create(mono)
            .expectNextCount(1)
            .expectComplete().verify();
    }

    private void testRetryingOnMalformedBody(Mono mono, String body, MockWebServer server, int count)
    throws Exception
    {
        for(int i = 0; i < count; i++)
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("dadsdcz"));;
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));

        StepVerifier.create(mono)
            .expectNextCount(1)
            .expectComplete().verify();
    }

    private void testRetryingOnTimeout(Mono mono, String body, MockWebServer server, int count)
    throws Exception
    {
        System.out.println("Testing socket timeouts, might take some time...");
        api.setOperationTimeout(Duration.ofMillis(100));
        MockResponse dr = new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBodyDelay(6, TimeUnit.SECONDS)
            .setBody(body);
        for(int i = 0; i < count; i++) server.enqueue(dr);
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));

        StepVerifier.create(mono)
            .expectNextCount(1)
            .expectComplete().verify();
    }

}
