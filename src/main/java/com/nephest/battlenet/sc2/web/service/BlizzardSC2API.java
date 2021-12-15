// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.*;
import com.nephest.battlenet.sc2.model.blizzard.*;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.util.LogUtil;
import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;
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
import reactor.util.function.*;
import reactor.util.retry.RetrySpec;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.LongStream;

import static com.nephest.battlenet.sc2.model.BaseLeague.LeagueType.GRANDMASTER;
import static com.nephest.battlenet.sc2.model.TeamFormat.ARCHON;
import static com.nephest.battlenet.sc2.model.TeamFormat._1V1;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@Service
public class BlizzardSC2API
extends BaseAPI
{

    private static final Logger LOG = LoggerFactory.getLogger(BlizzardSC2API.class);

    public static final int REQUESTS_PER_SECOND_CAP = 100;
    public static final int REQUESTS_PER_HOUR_CAP = 36000;
    public static final double REQUEST_RATE_MARGIN = 0.1;
    public static final Duration REQUEST_SLOT_REFRESH_TIME =
        Duration.ofMillis((long) (1000 * (1.0 + REQUEST_RATE_MARGIN)));
    public static final int DELAY = 1000;
    public static final int FIRST_SEASON = 28;
    public static final int PROFILE_LADDER_RETRY_COUNT = 3;
    public static final Duration ERROR_RATE_FRAME = Duration.ofMinutes(60);
    public static final double RETRY_ERROR_RATE_THRESHOLD = 40.0;

    private String regionUri;
    private final ObjectMapper objectMapper;
    private final Map<Region, WebClient> clients = new EnumMap<>(Region.class);
    private final Map<Region, ReactorRateLimiter> rateLimiters = new HashMap<>();
    private final Map<Region, AtomicLong> requests = new EnumMap<>(Region.class);
    private final Map<Region, AtomicLong> errors = new EnumMap<>(Region.class);
    private final Map<Region, Double> errorRates = new EnumMap<>(Region.class);

    @Autowired
    public BlizzardSC2API(ObjectMapper objectMapper, OAuth2AuthorizedClientManager auth2AuthorizedClientManager)
    {
        initWebClient(objectMapper, auth2AuthorizedClientManager);
        this.objectMapper = objectMapper;
        for(Region r : Region.values()) rateLimiters.put(r, new ReactorRateLimiter());
        Flux.interval(Duration.ofSeconds(0), REQUEST_SLOT_REFRESH_TIME).doOnNext(i->refreshReactorSlots()).subscribe();
        initErrorRates();
    }

    private void initErrorRates()
    {
        for(Region r : Region.values()) requests.put(r, new AtomicLong(0));
        for(Region r : Region.values()) errors.put(r, new AtomicLong(0));
        for(Region r : Region.values()) errorRates.put(r, 0.0);
        Flux.interval(ERROR_RATE_FRAME, ERROR_RATE_FRAME).doOnNext(i->calculateErrorRates()).subscribe();
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

    @Override
    protected void setWebClient(WebClient client)
    {
        for(Region region : Region.values()) clients.put(region, client);
    }

    @Override
    public WebClient getWebClient()
    {
        return getWebClient(Region.EU);
    }

    public WebClient getWebClient(Region region)
    {
        return clients.get(region);
    }

    private void initWebClient(ObjectMapper objectMapper, OAuth2AuthorizedClientManager auth2AuthorizedClientManager)
    {
        for(Region region : Region.values())
        {
            ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(auth2AuthorizedClientManager);
            oauth2Client.setDefaultClientRegistrationId("sc2-sys-" + region.name().toLowerCase());
            clients.put(region, WebServiceUtil.getWebClientBuilder(objectMapper, 500 * 1024)
                .apply(oauth2Client.oauth2Configuration()).build());
        }
    }

    protected void setRegionUri(String uri)
    {
        this.regionUri = uri;
    }

    private void refreshReactorSlots()
    {
        rateLimiters.values().forEach(l->l.refreshSlots(REQUESTS_PER_SECOND_CAP));
    }

    private void calculateErrorRates()
    {
        requests.keySet().forEach(r->
        {
            long requestCount = requests.get(r).getAndSet(0);
            double errorRate = requestCount == 0
                ? 0.0
                : (errors.get(r).getAndSet(0) / (double) requestCount) * 100;
            errorRates.put(r, errorRate);
            LOG.debug("{} error rate: {}%", r, errorRate);
        });
    }
    
    private RetrySpec getRetry(Region region, RetrySpec wanted)
    {
        return errorRates.get(region) > RETRY_ERROR_RATE_THRESHOLD 
            ? WebServiceUtil.RETRY_NEVER 
            : getRetry(wanted);
    }

    public Mono<BlizzardSeason> getSeason(Region region, Integer id)
    {
        return getWebClient(region)
            .get()
            .uri(regionUri != null ? regionUri : (region.getBaseUrl() + "data/sc2/season/{0}"), id)
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardDataSeason.class).cast(BlizzardSeason.class)
            .retryWhen(rateLimiters.get(region).retryWhen(getRetry(region, WebServiceUtil.RETRY)))
            .delaySubscription(rateLimiters.get(region).requestSlot())
            .doOnRequest(s->requests.get(region).incrementAndGet())
            .doOnError(t->errors.get(region).incrementAndGet());
    }

    public Mono<BlizzardSeason> getCurrentSeason(Region region)
    {
        return getWebClient(region)
            .get()
            .uri(regionUri != null ? regionUri : (region.getBaseUrl() + "sc2/ladder/season/{0}"), region.getId())
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardSeason.class)
            .retryWhen(rateLimiters.get(region).retryWhen(getRetry(region, WebServiceUtil.RETRY)))
            .delaySubscription(rateLimiters.get(region).requestSlot())
            .doOnRequest(s->requests.get(region).incrementAndGet())
            .doOnError(t->errors.get(region).incrementAndGet());
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
                (t)->season <= startFrom
                    ? Mono.error(new IllegalStateException(String.format("Last season not found %s %s", season, startFrom), t))
                    : getSeason(region, season - 1)));
    }

    //current season endpoint can return the 500/503 code sometimes
    public Mono<BlizzardSeason> getCurrentOrLastSeason(Region region, int startFrom)
    {
        return getCurrentSeason(region).onErrorResume(t->getLastSeason(region, startFrom));
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
        Mono<BlizzardLeague> mono =  getWebClient(region)
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
            .retryWhen(rateLimiters.get(region).retryWhen(getRetry(region, WebServiceUtil.RETRY)))
            .delaySubscription(rateLimiters.get(region).requestSlot())
            .doOnRequest(s->requests.get(region).incrementAndGet())
            .doOnError(t->errors.get(region).incrementAndGet());

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
                    LOG.debug("Current league not found. New season started recently? ({})", nfe.getRequest().getURI());
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

    public Flux<Tuple2<BlizzardLeague, Region>> getLeagues
    (Iterable<? extends Tuple5<Region, BlizzardSeason, BaseLeague.LeagueType, QueueType, TeamType>> ids, boolean cur)
    {
        return Flux.fromIterable(ids)
            .flatMap(id->WebServiceUtil.getOnErrorLogAndSkipMono(
                getLeague(id.getT1(), id.getT2(), id.getT3(), id.getT4(), id.getT5(), cur)
                    .zipWith(Mono.just(id.getT1()))));
    }

    public Mono<BlizzardLadder> getLadder
    (
        Region region,
        Long id
    )
    {
        return getWebClient(region)
            .get()
            .uri(regionUri != null ? regionUri : (region.getBaseUrl() + "data/sc2/ladder/{0}"), id)
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardLadder.class)
            .retryWhen(rateLimiters.get(region).retryWhen(getRetry(region, WebServiceUtil.RETRY)))
            .delaySubscription(rateLimiters.get(region).requestSlot())
            .doOnRequest(s->requests.get(region).incrementAndGet())
            .doOnError(t->errors.get(region).incrementAndGet());
    }

    public Mono<BlizzardLadder> getFilteredLadder
    (
        Region region,
        Long id,
        long startingFromEpochSeconds
    )
    {
        return getWebClient(region)
            .get()
            .uri(regionUri != null ? regionUri : (region.getBaseUrl() + "data/sc2/ladder/{0}"), id)
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(String.class)
            .map(s->extractNewTeams(s, startingFromEpochSeconds))
            .retryWhen(rateLimiters.get(region).retryWhen(getRetry(region, WebServiceUtil.RETRY)))
            .delaySubscription(rateLimiters.get(region).requestSlot())
            .doOnRequest(s->requests.get(region).incrementAndGet())
            .doOnError(t->errors.get(region).incrementAndGet());
    }

    private BlizzardLadder extractNewTeams(String s, long startingFromEpochSeconds)
    {
        try
        {
            ArrayList<BlizzardTeam> teams = new ArrayList<>();
            JsonNode root = objectMapper.readTree(s);
            for(JsonNode team : root.at("/team"))
            {
                long timestamp = team.findValue("last_played_time_stamp").asLong();
                if(timestamp > startingFromEpochSeconds) teams.add(objectMapper.treeToValue(team, BlizzardTeam.class));
            }
            return new BlizzardLadder
            (
                teams.toArray(BlizzardTeam[]::new),
                objectMapper.treeToValue(root.at("/league"), BlizzardLadderLeague.class)
            );
        }
        catch (JsonProcessingException e)
        {
            throw new IllegalStateException("Invalid json structure", e);
        }
    }

    public Flux<Tuple2<BlizzardLadder, Long>> getLadders
    (
        Region region,
        Long[] divisions
    )
    {
        return Flux.fromArray(divisions)
            .flatMap(d->WebServiceUtil.getOnErrorLogAndSkipMono(getLadder(region, d).zipWith(Mono.just(d))));
    }

    public Flux<Tuple2<BlizzardLadder, Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>>> getLadders
    (
        Iterable<? extends Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> ladderIds,
        long startingFromEpochSeconds,
        Map<Region, Set<Long>> errors
    )
    {
        return Flux.fromIterable(ladderIds)
            .flatMap(d->WebServiceUtil.getOnErrorLogAndSkipMono
            (
                startingFromEpochSeconds < 1
                    ? getLadder(d.getT2(), d.getT4()).zipWith(Mono.just(d))
                    : getFilteredLadder(d.getT2(), d.getT4().getLadderId(), startingFromEpochSeconds).zipWith(Mono.just(d)),
                t->errors.get(d.getT2()).add(d.getT4().getLadderId())
            ));
    }

    public Mono<BlizzardLadder> getLadder
    (
        Region region,
        BlizzardTierDivision division
    )
    {
        return getLadder(region, division.getLadderId());
    }

    public Flux<Tuple2<BlizzardLadder, BlizzardTierDivision>> getLadders
    (
        Region region,
        BlizzardTierDivision[] divisions
    )
    {
        return Flux.fromArray(divisions)
            .flatMap(d->WebServiceUtil.getOnErrorLogAndSkipMono(getLadder(region, d).zipWith(Mono.just(d))));
    }

    public Mono<Tuple3<Region, BlizzardPlayerCharacter[], Long>> getProfileLadderId(Region region, long ladderId)
    {
        return getWebClient(region)
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
                try
                {
                    JsonNode members = objectMapper.readTree(s).at("/ladderMembers");
                    BlizzardPlayerCharacter[] characters;
                    if(members.size() < PROFILE_LADDER_RETRY_COUNT)
                    {
                        characters = new BlizzardPlayerCharacter[1];
                        characters[0] = objectMapper
                            .treeToValue(members.get(members.size() - 1).get("character"), BlizzardPlayerCharacter.class);
                    } else {
                        characters = new BlizzardPlayerCharacter[PROFILE_LADDER_RETRY_COUNT];
                        int offset = members.size() / PROFILE_LADDER_RETRY_COUNT;
                        for(int i = 0; i < PROFILE_LADDER_RETRY_COUNT; i++)
                            characters[i] = objectMapper
                                .treeToValue(members.get(offset * i).get("character"), BlizzardPlayerCharacter.class);
                    }
                    return Tuples.of(region, characters, ladderId);
                }
                catch (JsonProcessingException e)
                {
                    throw new IllegalStateException("Invalid json structure", e);
                }
            })
            .retryWhen(rateLimiters.get(region).retryWhen(getRetry(region, WebServiceUtil.RETRY_SKIP_NOT_FOUND)))
            .delaySubscription(rateLimiters.get(region).requestSlot())
            .doOnRequest(s->requests.get(region).incrementAndGet())
            .doOnError(t->errors.get(region).incrementAndGet());
    }

    public Flux<Tuple3<Region, BlizzardPlayerCharacter[], Long>> getProfileLadderIds
    (Region region, long from, long toExcluded)
    {
        return Flux.fromStream(LongStream.range(from, toExcluded).boxed())
            .flatMap(l->WebServiceUtil.getOnErrorLogAndSkipLogLevelMono(
                getProfileLadderId(region, l),
                (t)->ExceptionUtils.getRootCause(t) instanceof WebClientResponseException.NotFound
                    ? LogUtil.LogLevel.DEBUG
                    : LogUtil.LogLevel.WARNING));
    }

    public Flux<Tuple3<Region, BlizzardPlayerCharacter[], Long>> getProfileLadderIds
    (Region region, Iterable<? extends Long> ids)
    {
        return Flux.fromIterable(ids)
            .flatMap(l->WebServiceUtil.getOnErrorLogAndSkipLogLevelMono(
                getProfileLadderId(region, l),
                (t)->ExceptionUtils.getRootCause(t) instanceof WebClientResponseException.NotFound
                    ? LogUtil.LogLevel.DEBUG
                    : LogUtil.LogLevel.WARNING));
    }

    public Mono<BlizzardProfileLadder> getProfileLadder
    (Tuple3<Region, BlizzardPlayerCharacter[], Long> id, Set<QueueType> queueTypes)
    {
        return chainProfileLadderMono(id, 0, queueTypes);
    }

    private Mono<BlizzardProfileLadder> chainProfileLadderMono
    (Tuple3<Region, BlizzardPlayerCharacter[], Long> id, int ix, Set<QueueType> queueTypes)
    {
        int prevIx = ix - 1;
        if(ix > 0) LOG.debug("Profile ladder not found {} times: {}/{}/{}",
            ix, id.getT2()[prevIx].getRealm(), id.getT2()[prevIx].getId(), id.getT3());
        return Mono.defer(()->
        {
            if(ix < id.getT2().length)
            {
                return getProfileLadderMono(id.getT1(), id.getT2()[ix],id.getT3(), queueTypes)
                    .onErrorResume((t)->{
                        if(t.getMessage().startsWith("Invalid game mode")) return Mono.error(t);
                        LOG.debug(t.getMessage(), t);
                        return chainProfileLadderMono(id, ix + 1, queueTypes);
                    });
            }
            return Mono.error(new NoRetryException(
                "Profile ladder not found",
                "Profile ladder not found {}/{}/{}",
                id.getT2()[prevIx].getRealm(), id.getT2()[prevIx].getId(), id.getT3()
            ));
        });
    }

    protected Mono<BlizzardProfileLadder> getProfileLadderMono
    (Region region, BlizzardPlayerCharacter character, long id, Set<QueueType> queueTypes)
    {
        return getWebClient(region)
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
                    return extractProfileLadder(s, id, queueTypes);
                }
                catch (JsonProcessingException e)
                {
                    throw new IllegalStateException("Invalid json structure", e);
                }
            })
            .retryWhen(rateLimiters.get(region).retryWhen(getRetry(region, WebServiceUtil.RETRY_SKIP_NOT_FOUND)))
            .delaySubscription(rateLimiters.get(region).requestSlot())
            .doOnRequest(s->requests.get(region).incrementAndGet())
            .doOnError(t->errors.get(region).incrementAndGet());
    }

    private Mono<BlizzardProfileLadder> extractProfileLadder(String s, long ladderId, Set<QueueType> queues)
    throws JsonProcessingException
    {
        JsonNode root = objectMapper.readTree(s);

        BlizzardLadderMembership[] memberships = objectMapper.treeToValue(root.at("/allLadderMemberships"), BlizzardLadderMembership[].class);
        BlizzardLadderMembership currentMembership = Arrays.stream(memberships)
            .filter(m->m.getLadderId().equals(ladderId))
            .findAny().orElse(null);
        if(currentMembership == null)
        {
            LOG.debug("Current ladder membership not found {}", ladderId);
            return Mono.error(new NoRetryException("Current ladder membership not found. Player moved to a new division?"));
        }
        //the length can be in 1-3 range depending on team format and type
        String[] membershipItems = currentMembership.getLocalizedGameMode().split(" ");
        TeamFormat teamFormat = TeamFormat.from(membershipItems[0]); //always present
        TeamType teamType = membershipItems.length < 3 ? TeamType.ARRANGED : TeamType.from(membershipItems[1]);
        QueueType queueType = QueueType.from(StatsService.VERSION, teamFormat);
        BaseLeague.LeagueType leagueType = BaseLeague.LeagueType.from(root.at("/league").asText());

        if (!queues.contains(queueType))
            return Mono.error(new NoRetryException("Invalid game mode: " + currentMembership.getLocalizedGameMode()));

        BlizzardProfileLadder ladder = new BlizzardProfileLadder(
            objectMapper.treeToValue(root.at("/ladderTeams"), BlizzardProfileTeam[].class),
            new BaseLeague(leagueType, queueType, teamType)
        );
        return Mono.just(ladder);
    }

    public Flux<Tuple2<BlizzardProfileLadder, Tuple3<Region, BlizzardPlayerCharacter[], Long>>> getProfileLadders
    (Iterable<? extends Tuple3<Region, BlizzardPlayerCharacter[], Long>> ids, Set<QueueType> queueTypes)
    {
        return Flux.fromIterable(ids)
            .flatMap(id->WebServiceUtil
                .getOnErrorLogAndSkipLogLevelMono(
                    getProfileLadder(id, queueTypes),
                    (t)->t.getMessage().startsWith("Invalid game mode")
                        ? LogUtil.LogLevel.DEBUG
                        : LogUtil.LogLevel.WARNING)
                .zipWith(Mono.just(id)));
    }

    public Mono<Tuple2<BlizzardMatches, PlayerCharacter>> getMatches(PlayerCharacter playerCharacter)
    {
        return getWebClient(playerCharacter.getRegion())
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
            .retryWhen(rateLimiters.get(playerCharacter.getRegion()).retryWhen(getRetry(playerCharacter.getRegion(), WebServiceUtil.RETRY)))
            .delaySubscription(rateLimiters.get(playerCharacter.getRegion()).requestSlot())
            .doOnRequest(s->requests.get(playerCharacter.getRegion()).incrementAndGet())
            .doOnError(t->errors.get(playerCharacter.getRegion()).incrementAndGet());
    }

    public Flux<Tuple2<BlizzardMatches, PlayerCharacter>> getMatches
    (Iterable<? extends PlayerCharacter> playerCharacters, Set<PlayerCharacter> errors)
    {
        return Flux.fromIterable(playerCharacters)
            .flatMap(p->WebServiceUtil.getOnErrorLogAndSkipMono(getMatches(p), t->errors.add(p)));
    }

}
