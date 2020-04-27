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
import com.nephest.battlenet.sc2.model.blizzard.*;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;

import javax.validation.Valid;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toUnmodifiableMap;
import static org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
public class BlizzardSC2API
{

    //first mmr season
    public static final long firstSeason = 29;
    public static final long lastSeason = 42;
    public static final int firstSeasonNumber = 3;
    public static final int firstSeasonYear = 2016;
    public static final Map<Long, BlizzardSeason> MMR_SEASONS = Stream.of
    (
        new BlizzardSeason(29l, 2016, 5),
        new BlizzardSeason(30l, 2016, 6),
        new BlizzardSeason(31l, 2017, 1),
        new BlizzardSeason(32l, 2017, 2),
        new BlizzardSeason(33l, 2017, 3),
        new BlizzardSeason(34l, 2017, 4),
        new BlizzardSeason(35l, 2018, 1),
        new BlizzardSeason(36l, 2018, 2),
        new BlizzardSeason(37l, 2018, 3),
        new BlizzardSeason(38l, 2018, 4),
        new BlizzardSeason(39l, 2019, 1),
        new BlizzardSeason(40l, 2019, 2),
        new BlizzardSeason(41l, 2019, 3),
        new BlizzardSeason(42l, 2019, 4)
    ).collect(toUnmodifiableMap(BlizzardSeason::getId, Function.identity()));
    public static final int RETRY_COUNT = 3;
    public static final Duration CONNECT_TIMEOUT = Duration.ofMillis(10000);
    public static final Duration IO_TIMEOUT = Duration.ofMillis(10000);
    public static final Duration RETRY_DURATION_MIN = Duration.ofMillis(300);
    public static final Duration RETRY_DURATION_MAX = Duration.ofMillis(1000);

    private WebClient client;
    private final String password;
    private BlizzardAccessToken accessToken;
    private String regionUri;

    public BlizzardSC2API(@Value("${blizzard.api.key}") String password)
    {
        Objects.requireNonNull(password);
        this.password = password;
        initWebClient();
    }

    private void initWebClient()
    {
        TcpClient timeoutClient = TcpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
            .doOnConnected
            (
                c->
                {
                    c.addHandlerLast(new ReadTimeoutHandler((int) IO_TIMEOUT.toSeconds()))
                    .addHandlerLast(new WriteTimeoutHandler((int) IO_TIMEOUT.toSeconds()));

                }
            );
        HttpClient httpClient = HttpClient.from(timeoutClient)
            .compress(true);
        client = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    protected void setRegionUri(String uri)
    {
        this.regionUri = uri;
    }

    protected void setWebClient(WebClient client)
    {
        this.client = client;
    }

    protected WebClient getWebClient()
    {
        return client;
    }

    public String getPassword()
    {
        return password;
    }

    @Validated
    public void setAccessToken(@Valid BlizzardAccessToken token)
    {
        this.accessToken = token;
        client = client.mutate()
            .defaultHeader("Authorization", "Bearer " + token.getAccessToken())
            .build();
    }

    public BlizzardAccessToken getAccessToken()
    {
        return accessToken;
    }

    protected void renewAccessToken(boolean force)
    {
        if(force || getAccessToken() == null || !getAccessToken().isValid())
        {
            BlizzardAccessToken token = getWebClient()
                .post()
                .uri(regionUri != null ? regionUri : "https://us.battle.net/oauth/token")
                .header("Authorization", "Basic " + getPassword())
                .accept(APPLICATION_JSON)
                .contentType(APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("grant_type", "client_credentials"))
                .retrieve()
                .bodyToMono(BlizzardAccessToken.class)
                .retryBackoff(RETRY_COUNT, RETRY_DURATION_MIN, RETRY_DURATION_MAX)
                .block();
            setAccessToken(token);
        }
    }

    public void renewAccessToken()
    {
        renewAccessToken(false);
    }

    public static final BlizzardSeason getSeason(Long id)
    {
        return MMR_SEASONS.get(id);
    }

    public Mono<BlizzardSeason> getCurrentSeason(Region region)
    {
        renewAccessToken();
        return getWebClient()
            .get()
            .uri(regionUri != null ? regionUri : (region.getBaseUrl() + "sc2/ladder/season/{0}"), region.getId())
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardSeason.class)
            .retryBackoff(RETRY_COUNT, RETRY_DURATION_MIN, RETRY_DURATION_MAX);
    }

    public Mono<BlizzardLeague> getLeague
    (
        Region region,
        BlizzardSeason season,
        BlizzardLeague.LeagueType leagueType,
        QueueType queueType,
        TeamType teamType
    )
    {
        renewAccessToken();
        return getWebClient()
            .get()
            .uri
            (
                regionUri != null ? regionUri : (region.getBaseUrl() + "data/sc2/league/{0}/{1}/{2}/{3}"),
                season.getId(),
                queueType.getId(),
                teamType.getId(),
                leagueType.getId()
            )
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardLeague.class)
            .retryBackoff(RETRY_COUNT, RETRY_DURATION_MIN, RETRY_DURATION_MAX);
    }

    public Mono<BlizzardLadder> getLadder
    (
        Region region,
        BlizzardTierDivision division
    )
    {
        renewAccessToken();
        return getWebClient()
            .get()
            .uri
            (
                regionUri != null ? regionUri : (region.getBaseUrl() + "data/sc2/ladder/{0}"),
                division.getLadderId()
            )
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardLadder.class)
            .retryBackoff(RETRY_COUNT, RETRY_DURATION_MIN, RETRY_DURATION_MAX);
    }

}
