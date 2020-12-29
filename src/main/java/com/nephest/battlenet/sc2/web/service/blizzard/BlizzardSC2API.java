// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.blizzard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.*;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ParallelFlux;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.LocalDate;
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

    public static final int REQUESTS_PER_SECOND_CAP = 100;
    public static final int REQUESTS_PER_HOUR_CAP = 36000;
    public static final int FIRST_SEASON = 28;
    //historical season data is taken from liquipedia.net
    public static final Map<Integer, BlizzardSeason> MMR_SEASONS = Stream.of
    (
        new BlizzardSeason(28, 2016, 4, LocalDate.of(2016, 7, 12), LocalDate.of(2016, 10, 18)),
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

    private WebClient client;
    private String regionUri;

    @Autowired
    public BlizzardSC2API(ObjectMapper objectMapper, OAuth2AuthorizedClientManager auth2AuthorizedClientManager)
    {
        initWebClient(objectMapper, auth2AuthorizedClientManager);
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

    private void initWebClient(ObjectMapper objectMapper, OAuth2AuthorizedClientManager auth2AuthorizedClientManager)
    {
        ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
            new ServletOAuth2AuthorizedClientExchangeFilterFunction(auth2AuthorizedClientManager);
        oauth2Client.setDefaultClientRegistrationId("sc2-sys");
        client = WebServiceUtil.getWebClientBuilder(objectMapper, -1).apply(oauth2Client.oauth2Configuration()).build();
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

    public static BlizzardSeason getStaticSeason(Integer id)
    {
        return MMR_SEASONS.get(id);
    }

    public Mono<BlizzardDataSeason> getSeason(Region region, Integer id)
    {
        return getWebClient()
            .get()
            .uri(regionUri != null ? regionUri : (region.getBaseUrl() + "data/sc2/season/{0}"), id)
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardDataSeason.class)
            .retryWhen(WebServiceUtil.RETRY);
    }

    public Mono<BlizzardSeason> getCurrentSeason(Region region)
    {
        return getWebClient()
            .get()
            .uri(regionUri != null ? regionUri : (region.getBaseUrl() + "sc2/ladder/season/{0}"), region.getId())
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardSeason.class)
            .retryWhen(WebServiceUtil.RETRY);
    }

    public BlizzardSeason getLastSeason(Region region)
    {
        BlizzardSeason lastSeason = null;
        for(int i = FIRST_SEASON; true; i++)
        {
            BlizzardSeason s = getSeason(region, i).onErrorResume((t)->Mono.empty()).block();
            if(s == null) break;

            lastSeason = s;
        }
        if(lastSeason == null) throw new IllegalStateException("Could not fetch the last season. API is broken?");

        return lastSeason;
    }

    //current season endpoint can return the 500/503 code sometimes
    public Mono<BlizzardSeason> getCurrentOrLastSeason(Region region)
    {
        return getCurrentSeason(region).onErrorResume((t)->Mono.just(getLastSeason(region)));
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
            .retryWhen(WebServiceUtil.RETRY);

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
            .retryWhen(WebServiceUtil.RETRY);
    }

    public Mono<Tuple2<BlizzardMatches, PlayerCharacter>> getMatches(PlayerCharacter playerCharacter)
    {
        return getWebClient()
            .get()
            .uri
            (
                regionUri != null
                    ? regionUri
                    : (playerCharacter.getRegion().getBaseUrl() + "sc2/legacy/profile/{0}/{1}/{2}/matches"),
                playerCharacter.getRegion().getId(),
                playerCharacter.getRealm(),
                playerCharacter.getBattlenetId()
            )
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardMatches.class)
            .zipWith(Mono.just(playerCharacter))
            .retryWhen(WebServiceUtil.RETRY)
            .onErrorReturn((t)->t.getCause() != null && t.getCause() instanceof WebClientResponseException.NotFound,
                Tuples.of(new BlizzardMatches(), playerCharacter));
    }

    public ParallelFlux<Tuple2<BlizzardMatches, PlayerCharacter>> getMatches(Iterable<? extends PlayerCharacter> playerCharacters)
    {
        return Flux.fromIterable(playerCharacters)
            .parallel()
            .runOn(Schedulers.boundedElastic())
            .flatMap(this::getMatches, false, REQUESTS_PER_SECOND_CAP);
    }

}
