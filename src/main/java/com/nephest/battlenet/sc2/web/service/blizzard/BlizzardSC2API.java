// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.blizzard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.*;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
import reactor.util.function.Tuple3;
import reactor.util.function.Tuples;

import static com.nephest.battlenet.sc2.model.BaseLeague.LeagueType.GRANDMASTER;
import static com.nephest.battlenet.sc2.model.TeamFormat.ARCHON;
import static com.nephest.battlenet.sc2.model.TeamFormat._1V1;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
public class BlizzardSC2API
{

    private static final Logger LOG = LoggerFactory.getLogger(BlizzardSC2API.class);

    public static final int REQUESTS_PER_SECOND_CAP = 100;
    public static final int REQUESTS_PER_HOUR_CAP = 36000;
    public static final double REQUEST_RATE_COEFF = 0.9;
    public static final int SAFE_REQUESTS_PER_SECOND_CAP =
        (int) Math.round(REQUESTS_PER_SECOND_CAP * REQUEST_RATE_COEFF);
    public static final int SAFE_REQUESTS_PER_HOUR_CAP = (int) Math.round(REQUESTS_PER_HOUR_CAP * REQUEST_RATE_COEFF);
    public static final int FIRST_SEASON = 28;

    private WebClient client;
    private String regionUri;
    private final ObjectMapper objectMapper;

    @Autowired
    public BlizzardSC2API(ObjectMapper objectMapper, OAuth2AuthorizedClientManager auth2AuthorizedClientManager)
    {
        initWebClient(objectMapper, auth2AuthorizedClientManager);
        this.objectMapper = objectMapper;
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

    public Mono<BlizzardSeason> getSeason(Region region, Integer id)
    {
        return getWebClient()
            .get()
            .uri(regionUri != null ? regionUri : (region.getBaseUrl() + "data/sc2/season/{0}"), id)
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardDataSeason.class).cast(BlizzardSeason.class)
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

    public Mono<BlizzardSeason> getLastSeason(Region region)
    {
        return chainSeasonMono(region, BlizzardSC2API.FIRST_SEASON);
    }

    private Mono<BlizzardSeason> chainSeasonMono(Region region, int season)
    {
        return Mono.defer(()->getSeason(region, season)
            .then(chainSeasonMono(region, season + 1))
            .onErrorResume((t)->getSeason(region, season - 1).onErrorResume((t1)->Mono.empty())));
    }

    //current season endpoint can return the 500/503 code sometimes
    public Mono<BlizzardSeason> getCurrentOrLastSeason(Region region)
    {
        return getCurrentSeason(region).onErrorResume((t)->getLastSeason(region));
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

    public Mono<Tuple3<Region, BlizzardPlayerCharacter, Long>> getProfileLadderId(Region region, long ladderId)
    {
        return getWebClient()
            .get()
            .uri
            (
                regionUri != null ? regionUri : (region.getBaseUrl() + "sc2/legacy/ladder/{0}/{1}"),
                region.getId(), ladderId
            )
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(String.class)
            .map((s)->
            {
                BlizzardPlayerCharacter character = null;
                try
                {
                    JsonNode members = objectMapper.readTree(s).at("/ladderMembers");
                    character = objectMapper
                        //select a player that is less likely to promote
                        .treeToValue(members.get(members.size() - 1).get("character"), BlizzardPlayerCharacter.class);
                }
                catch (JsonProcessingException e)
                {
                    throw new IllegalStateException("Invalid json structure", e);
                }
                return Tuples.of(region, character, ladderId);
            })
            .retryWhen(WebServiceUtil.RETRY_SKIP_NOT_FOUND);
    }

    public Mono<BlizzardProfileLadder> getProfile1v1Ladder(Region region, BlizzardPlayerCharacter character, long ladderId)
    {
        return getWebClient()
            .get()
            .uri
            (
                regionUri != null ? regionUri : (region.getBaseUrl() + "sc2/profile/{0}/{1}/{2}/ladder/{1}"),
                region.getId(), character.getRealm(), character.getId(), ladderId
            )
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(String.class)
            .flatMap((s)->
            {
                try
                {
                    return Mono.justOrEmpty(extractProfileLadder(s));
                }
                catch (JsonProcessingException e)
                {
                    throw new IllegalStateException("Invalid json structure", e);
                }
            })
            .retryWhen(WebServiceUtil.RETRY_SKIP_NOT_FOUND);
    }

    private BlizzardProfileLadder extractProfileLadder(String s)
    throws JsonProcessingException
    {
        JsonNode root = objectMapper.readTree(s);

        if(!objectMapper.treeToValue(root.at("/currentLadderMembership"), BlizzardLadderMembership.class).getLocalizedGameMode()
            .toLowerCase().contains("1v1")) return null;

        return new BlizzardProfileLadder(
            objectMapper.treeToValue(root.at("/ladderTeams"), BlizzardProfileTeam[].class),
            BaseLeague.LeagueType.from(root.at("/league").asText()));
    }

    public Mono<Tuple2<BlizzardMatches, PlayerCharacter>> getMatches(PlayerCharacter playerCharacter)
    {
        return getWebClient()
            .get()
            .uri
            (
                regionUri != null
                    ? regionUri
                    //EU is the most stable host for matches currently, might change it in the future
                    : (Region.EU.getBaseUrl() + "sc2/legacy/profile/{0}/{1}/{2}/matches"),
                playerCharacter.getRegion().getId(),
                playerCharacter.getRealm(),
                playerCharacter.getBattlenetId()
            )
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardMatches.class)
            .zipWith(Mono.just(playerCharacter))
            .retryWhen(WebServiceUtil.RETRY)
            //API can be broken randomly, accepting this as a normal behavior
            .doOnError(t->LOG.error(ExceptionUtils.getRootCauseMessage(t)))
            .onErrorReturn(Tuples.of(new BlizzardMatches(), playerCharacter));
    }

    public ParallelFlux<Tuple2<BlizzardMatches, PlayerCharacter>> getMatches(Iterable<? extends PlayerCharacter> playerCharacters)
    {
        return Flux.fromIterable(playerCharacters)
            .parallel()
            .runOn(Schedulers.boundedElastic())
            .flatMap(this::getMatches, false, SAFE_REQUESTS_PER_SECOND_CAP);
    }

}
