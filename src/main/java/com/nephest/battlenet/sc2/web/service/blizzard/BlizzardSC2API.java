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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.tcp.TcpClient;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
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

    private static final Logger LOG = LoggerFactory.getLogger(BlizzardSC2API.class);

    //first mmr season
    public static final long firstSeason = 29;
    public static final long lastSeason = 42;
    public static final int firstSeasonNumber = 3;
    public static final int firstSeasonYear = 2016;
    //historical season data is taken from liquipedia.net
    public static final Map<Integer, BlizzardSeason> MMR_SEASONS = Stream.of
    (
        new BlizzardSeason(29, 2016, 5, LocalDate.of(2016, 10, 18), LocalDate.of(2016, 11, 22)),
        new BlizzardSeason(30, 2016, 6, LocalDate.of(2016, 11, 22), LocalDate.of(2017, 1, 24)),
        new BlizzardSeason(31, 2017, 1, LocalDate.of(2017, 1, 24), LocalDate.of(2017, 5, 2)),
        new BlizzardSeason(32, 2017, 2, LocalDate.of(2017, 5, 2), LocalDate.of(2017, 7, 19)),
        new BlizzardSeason(33, 2017, 3, LocalDate.of(2017, 7, 19), LocalDate.of(2017, 10, 19)),
        new BlizzardSeason(34, 2017, 4, LocalDate.of(2017, 10, 19), LocalDate.of(2018, 1, 23)),
        new BlizzardSeason(35, 2018, 1, LocalDate.of(2018, 1, 23), LocalDate.of(2018, 5, 15)),
        new BlizzardSeason(36, 2018, 2, LocalDate.of(2018, 5, 15), LocalDate.of(2018, 8, 14)),
        new BlizzardSeason(37, 2018, 3, LocalDate.of(2018, 8,14), LocalDate.of(2018, 11, 20)),
        new BlizzardSeason(38, 2018, 4, LocalDate.of(2018, 11, 20), LocalDate.of(2019, 1, 22)),
        new BlizzardSeason(39, 2019, 1, LocalDate.of(2019, 1, 22), LocalDate.of(2019, 5, 22)),
        new BlizzardSeason(40, 2019, 2, LocalDate.of(2019, 5, 22), LocalDate.of(2019, 8, 22)),
        new BlizzardSeason(41, 2019, 3, LocalDate.of(2019, 8, 22), LocalDate.of(2019, 11, 25)),
        new BlizzardSeason(42, 2019, 4, LocalDate.of(2019, 11,25), LocalDate.of(2020, 3, 17)),
        new BlizzardSeason(43, 2020, 1, LocalDate.of(2020, 3, 17), LocalDate.of(2020, 6, 10)),
        new BlizzardSeason(44, 2020, 2, LocalDate.of(2020, 6, 10), LocalDate.of(2020, 10, 1)),
        new BlizzardSeason(45, 2020, 3, LocalDate.of(2020, 10, 1), LocalDate.of(2021, 1, 26))
    ).collect(toUnmodifiableMap(BlizzardSeason::getId, Function.identity()));
    public static final long LAST_SEASON = MMR_SEASONS.keySet().stream().max(Comparator.naturalOrder()).get();
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

    //there is no previous season metadata in the upstream api
    public static BlizzardSeason getSeason(Integer id)
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
        TeamType teamType,
        boolean currentSeason
    )
    {
        Mono<BlizzardLeague> mono =  getWebClient()
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

        /*
           After a new season has started there is a period of time when this endpoint could return a 404
           response. Treating such situations as valid and returning an empty league as the upstream should.
         */
        if(currentSeason) mono = mono.onErrorReturn
        (
            (t)->
            {
                if(t.getCause() != null && t.getCause() instanceof WebClientResponseException.NotFound)
                {
                    WebClientResponseException.NotFound nfe = (WebClientResponseException.NotFound) t.getCause();
                    LOG.warn("Current league not found. New season started recently? ({})", nfe.getRequest().getURI());
                    return true;
                }
                return false;
            },
            BlizzardLeague.createEmpty(leagueType, queueType, teamType)
        );
        return mono;
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
        return getLeague(region, season, leagueType, queueType, teamType, false);
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
