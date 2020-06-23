// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.blizzard;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLadder;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeague;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTierDivision;
import com.nephest.battlenet.sc2.model.local.League;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.nephest.battlenet.sc2.model.BaseLeague.LeagueType.GRANDMASTER;
import static com.nephest.battlenet.sc2.model.TeamFormat.ARCHON;
import static com.nephest.battlenet.sc2.model.TeamFormat._1V1;
import static java.util.stream.Collectors.toUnmodifiableMap;
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
        new BlizzardSeason(29L, 2016, 5),
        new BlizzardSeason(30L, 2016, 6),
        new BlizzardSeason(31L, 2017, 1),
        new BlizzardSeason(32L, 2017, 2),
        new BlizzardSeason(33L, 2017, 3),
        new BlizzardSeason(34L, 2017, 4),
        new BlizzardSeason(35L, 2018, 1),
        new BlizzardSeason(36L, 2018, 2),
        new BlizzardSeason(37L, 2018, 3),
        new BlizzardSeason(38L, 2018, 4),
        new BlizzardSeason(39L, 2019, 1),
        new BlizzardSeason(40L, 2019, 2),
        new BlizzardSeason(41L, 2019, 3),
        new BlizzardSeason(42L, 2019, 4),
        new BlizzardSeason(43L, 2020, 1),
        new BlizzardSeason(44L, 2020, 2)
    ).collect(toUnmodifiableMap(BlizzardSeason::getId, Function.identity()));
    public static final int RETRY_COUNT = 3;
    public static final Duration CONNECT_TIMEOUT = Duration.ofMillis(10000);
    public static final Duration IO_TIMEOUT = Duration.ofMillis(10000);
    public static final Duration RETRY_DURATION_MIN = Duration.ofMillis(300);
    public static final Duration RETRY_DURATION_MAX = Duration.ofMillis(1000);
    public static final Retry RETRY = Retry
        .backoff(RETRY_COUNT, RETRY_DURATION_MIN).maxBackoff(RETRY_DURATION_MAX)
        .filter(t->true)
        .transientErrors(true);

    private WebClient client;
    private String regionUri;

    @Autowired
    public BlizzardSC2API(OAuth2AuthorizedClientManager auth2AuthorizedClientManager)
    {
        initWebClient(auth2AuthorizedClientManager);
    }

    public static boolean isValidCombination(League.LeagueType leagueType, QueueType queueType, TeamType teamType)
    {
        if
        (
            teamType == TeamType.RANDOM
            && (queueType.getTeamFormat() == ARCHON || queueType.getTeamFormat() == _1V1)
        ) return false;

        return leagueType != GRANDMASTER || queueType.getTeamFormat() == ARCHON || queueType.getTeamFormat() == _1V1;
    }

    private void initWebClient(OAuth2AuthorizedClientManager auth2AuthorizedClientManager)
    {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
            new ServletOAuth2AuthorizedClientExchangeFilterFunction(auth2AuthorizedClientManager);
        oauth2Client.setDefaultClientRegistrationId("sc2-sys");
        TcpClient timeoutClient = TcpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECT_TIMEOUT.toMillis())
            .doOnConnected
            (
                c-> c.addHandlerLast(new ReadTimeoutHandler((int) IO_TIMEOUT.toSeconds()))
                .addHandlerLast(new WriteTimeoutHandler((int) IO_TIMEOUT.toSeconds()))
            );
        HttpClient httpClient = HttpClient.from(timeoutClient)
            .compress(true);
        client = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .apply(oauth2Client.oauth2Configuration())
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

    public static BlizzardSeason getSeason(Long id)
    {
        return MMR_SEASONS.get(id);
    }

    public Mono<BlizzardSeason> getCurrentSeason(Region region)
    {
        return getWebClient()
            .get()
            .uri(regionUri != null ? regionUri : (region.getBaseUrl() + "sc2/ladder/season/{0}"), region.getId())
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardSeason.class)
            .retryWhen(RETRY);
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
            .retryWhen(RETRY);
    }

    public Mono<BlizzardLadder> getLadder
    (
        Region region,
        BlizzardTierDivision division
    )
    {
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
            .retryWhen(RETRY);
    }

}
