// Copyright (C) 2020-2021 Oleksandr Masniuk
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
import com.nephest.battlenet.sc2.web.service.NoRetryException;
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
import reactor.util.function.Tuple5;
import reactor.util.function.Tuples;

import java.util.Arrays;
import java.util.stream.LongStream;

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
    public static final int DELAY = 1000;
    public static final int CONCURRENCY = SAFE_REQUESTS_PER_SECOND_CAP / Runtime.getRuntime().availableProcessors();
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
        client =
            WebServiceUtil.getWebClientBuilder(objectMapper, 300000).apply(oauth2Client.oauth2Configuration()).build();
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

    public Mono<BlizzardSeason> getLastSeason(Region region, int startFrom)
    {
        return chainSeasonMono(region, startFrom, startFrom);
    }

    private Mono<BlizzardSeason> chainSeasonMono(Region region, int season, int startFrom)
    {
        return Mono.defer(()->getSeason(region, season)
            .then(chainSeasonMono(region, season + 1, startFrom))
            .onErrorResume(
                (t)->season <= startFrom ? Mono.empty() : getSeason(region, season - 1)
                    .onErrorResume((t1)->Mono.empty())));
    }

    //current season endpoint can return the 500/503 code sometimes
    public Mono<BlizzardSeason> getCurrentOrLastSeason(Region region, int startFrom)
    {
        return getCurrentSeason(region).onErrorResume((t)->getLastSeason(region, startFrom));
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

    public ParallelFlux<BlizzardLeague> getLeagues
    (Iterable<? extends Tuple5<Region, BlizzardSeason, BaseLeague.LeagueType, QueueType, TeamType>> ids, boolean cur)
    {
        return Flux.fromIterable(ids)
            .parallel(SAFE_REQUESTS_PER_SECOND_CAP)
            .runOn(Schedulers.boundedElastic())
            .flatMap(id->WebServiceUtil.getRateDelayedMono(
                getLeague(id.getT1(), id.getT2(), id.getT3(), id.getT4(), id.getT5(), cur),
                t->{if(!(ExceptionUtils.getRootCause(t) instanceof NoRetryException))
                    LOG.error(ExceptionUtils.getRootCauseMessage(t));
                    return Mono.empty();},
                DELAY),
                true, 1);
    }

    public Mono<BlizzardLadder> getLadder
    (
        Region region,
        Long id
    )
    {
        return getWebClient()
            .get()
            .uri(regionUri != null ? regionUri : (region.getBaseUrl() + "data/sc2/ladder/{0}"), id)
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardLadder.class)
            .retryWhen(WebServiceUtil.RETRY);
    }

    public ParallelFlux<Tuple2<BlizzardLadder, Long>> getLadders
    (
        Region region,
        Long[] divisions
    )
    {
        return Flux.fromArray(divisions)
            .parallel(SAFE_REQUESTS_PER_SECOND_CAP)
            .runOn(Schedulers.boundedElastic())
            .flatMap(d->WebServiceUtil.getRateDelayedMono(
                getLadder(region, d).zipWith(Mono.just(d)),
                t->{if(!(ExceptionUtils.getRootCause(t) instanceof NoRetryException))
                        LOG.error(ExceptionUtils.getRootCauseMessage(t));
                    return Mono.empty();},
                DELAY),
                true, 1);
    }

    public Mono<BlizzardLadder> getLadder
    (
        Region region,
        BlizzardTierDivision division
    )
    {
        return getLadder(region, division.getLadderId());
    }

    public ParallelFlux<Tuple2<BlizzardLadder, BlizzardTierDivision>> getLadders
    (
        Region region,
        BlizzardTierDivision[] divisions
    )
    {
        return Flux.fromArray(divisions)
            .parallel(SAFE_REQUESTS_PER_SECOND_CAP)
            .runOn(Schedulers.boundedElastic())
            .flatMap(d->WebServiceUtil.getRateDelayedMono(
                    getLadder(region, d).zipWith(Mono.just(d)),
                    t->{if(!(ExceptionUtils.getRootCause(t) instanceof NoRetryException)) LOG.error(t.getMessage(), t);
                        return Mono.empty();},
                    DELAY),
                true, 1);
    }

    public Mono<Tuple3<Region, BlizzardPlayerCharacter[], Long>> getProfileLadderId(Region region, long ladderId)
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
                BlizzardPlayerCharacter[] characters = new BlizzardPlayerCharacter[3];
                try
                {
                    JsonNode members = objectMapper.readTree(s).at("/ladderMembers");
                    characters[0] = objectMapper
                        .treeToValue(members.get(members.size() - 1).get("character"), BlizzardPlayerCharacter.class);
                    characters[1] = objectMapper
                        .treeToValue(members.get(members.size() / 2).get("character"), BlizzardPlayerCharacter.class);
                    characters[2] = objectMapper
                        .treeToValue(members.get(0).get("character"), BlizzardPlayerCharacter.class);
                }
                catch (JsonProcessingException e)
                {
                    throw new IllegalStateException("Invalid json structure", e);
                }
                return Tuples.of(region, characters, ladderId);
            })
            .doOnError(t->LOG.error(ExceptionUtils.getRootCauseMessage(t)))
            .retryWhen(WebServiceUtil.RETRY_SKIP_NOT_FOUND);
    }

    public ParallelFlux<Tuple3<Region, BlizzardPlayerCharacter[], Long>> getProfileLadderIds
    (Region region, long from, long toExcluded)
    {
        return Flux.fromStream(LongStream.range(from, toExcluded).boxed())
            .parallel(SAFE_REQUESTS_PER_SECOND_CAP)
            .runOn(Schedulers.boundedElastic())
            .flatMap(l->WebServiceUtil.getRateDelayedMono(getProfileLadderId(region, l), DELAY),
                true, 1);
    }

    public ParallelFlux<Tuple3<Region, BlizzardPlayerCharacter[], Long>> getProfileLadderIds
    (Region region, Iterable<? extends Long> ids)
    {
        return Flux.fromIterable(ids)
            .parallel(SAFE_REQUESTS_PER_SECOND_CAP)
            .runOn(Schedulers.boundedElastic())
            .flatMap(l->WebServiceUtil.getRateDelayedMono(getProfileLadderId(region, l), DELAY),
                true, 1);
    }

    public Mono<BlizzardProfileLadder> getProfile1v1Ladder(Tuple3<Region, BlizzardPlayerCharacter[], Long> id)
    {
        return chainProfileLadderMono(id, 0);
    }

    private Mono<BlizzardProfileLadder> chainProfileLadderMono
    (Tuple3<Region, BlizzardPlayerCharacter[], Long> id, int ix)
    {
        int prevIx = ix - 1;
        if(ix > 0) LOG.warn("Profile ladder not found {} times: {}/{}/{}",
            ix, id.getT2()[prevIx].getRealm(), id.getT2()[prevIx].getId(), id.getT3());
        return Mono.defer(()->
        {
            if(ix < id.getT2().length)
            {
                return getProfileLadderMono(id.getT1(), id.getT2()[ix],id.getT3())
                    .onErrorResume((t)->{
                        LOG.debug(ExceptionUtils.getRootCauseMessage(t));
                        return chainProfileLadderMono(id, ix + 1);
                    });
            }
            LOG.error("Profile ladder not found {}/{}/{}", id.getT2()[prevIx].getRealm(), id.getT2()[prevIx].getId(), id.getT3());
            return Mono.error(new NoRetryException("Profile ladder not found"));
        });
    }

    protected Mono<BlizzardProfileLadder> getProfileLadderMono(Region region, BlizzardPlayerCharacter character,long id)
    {
        return getWebClient()
            .get()
            .uri
                (
                    regionUri != null ? regionUri : (region.getBaseUrl() + "sc2/profile/{0}/{1}/{2}/ladder/{1}"),
                    region.getId(), character.getRealm(), character.getId(), id
                )
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(String.class)
            .flatMap((s)->
            {
                try
                {
                    return extractProfileLadder(s, id);
                }
                catch (JsonProcessingException e)
                {
                    throw new IllegalStateException("Invalid json structure", e);
                }
            })
            .retryWhen(WebServiceUtil.RETRY_SKIP_NOT_FOUND);
    }

    private Mono<BlizzardProfileLadder> extractProfileLadder(String s, long ladderId)
    throws JsonProcessingException
    {
        JsonNode root = objectMapper.readTree(s);

        BlizzardLadderMembership[] memberships = objectMapper.treeToValue(root.at("/allLadderMemberships"), BlizzardLadderMembership[].class);
        BlizzardLadderMembership currentMembership = Arrays.stream(memberships)
            .filter(m->m.getLadderId().equals(ladderId))
            .findAny().orElse(null);
        if(currentMembership == null)
        {
            LOG.warn("Current ladder membership not found {}", ladderId);
            return Mono.error(new NoRetryException("Current ladder membership not found. Player moved to a new division?"));
        }
        if (!currentMembership.getLocalizedGameMode().toLowerCase().contains("1v1"))
            return Mono.error(new NoRetryException("Invalid game mode: " + currentMembership.getLocalizedGameMode()));

        BlizzardProfileLadder ladder = new BlizzardProfileLadder(
            objectMapper.treeToValue(root.at("/ladderTeams"), BlizzardProfileTeam[].class),
            BaseLeague.LeagueType.from(root.at("/league").asText()));
        return Mono.just(ladder);
    }

    public ParallelFlux<Tuple2<BlizzardProfileLadder, Tuple3<Region, BlizzardPlayerCharacter[], Long>>> getProfile1v1Ladders
    (Iterable<? extends Tuple3<Region, BlizzardPlayerCharacter[], Long>> ids, int rps)
    {
        return Flux.fromIterable(ids)
            .parallel(rps)
            .runOn(Schedulers.boundedElastic())
            .flatMap(id->WebServiceUtil
                .getRateDelayedMono(
                    getProfile1v1Ladder(id),
                    t->{if(!(ExceptionUtils.getRootCause(t) instanceof NoRetryException)) LOG.error(t.getMessage(), t);
                        return Mono.empty();},
                    DELAY)
                .zipWith(Mono.just(id)),
                true, 1);
    }

    public ParallelFlux<Tuple2<BlizzardProfileLadder, Tuple3<Region, BlizzardPlayerCharacter[], Long>>> getProfile1v1Ladders
    (Iterable<? extends Tuple3<Region, BlizzardPlayerCharacter[], Long>> ids)
    {
        return getProfile1v1Ladders(ids, SAFE_REQUESTS_PER_SECOND_CAP);
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
            .parallel(SAFE_REQUESTS_PER_SECOND_CAP)
            .runOn(Schedulers.boundedElastic())
            .flatMap(p->WebServiceUtil.getRateDelayedMono(getMatches(p), DELAY), false, 1);
    }

}
