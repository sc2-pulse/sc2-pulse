// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileLadder;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.util.LogUtil;
import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import static org.springframework.http.MediaType.APPLICATION_JSON;


/*
    This is the web API, use it **ONLY** if everything else fails. You **MUST** follow the following rules:
        * use very low request rate
        * update once an hour
        * fetch only public data(ladders)
    Don't make blizzard angry, don't disrupt their web services, be nice.
 */
@Service
public class BlizzardSC2WebAPI
extends BaseAPI
implements ProfileLadderGetter
{

    private static final Logger LOG = LoggerFactory.getLogger(BlizzardSC2WebAPI.class);

    public static final int REQUESTS_PER_SECOND_CAP = 5;

    private String regionUri;
    private final BlizzardSC2API sc2API;
    private final ReactorRateLimiter rateLimiter = new ReactorRateLimiter();
    private final Map<Region, APIHealthMonitor> healthMonitors = new EnumMap<>(Region.class);

    @Autowired
    public BlizzardSC2WebAPI(ObjectMapper objectMapper, BlizzardSC2API sc2API, VarDAO varDAO)
    {
        this.sc2API = sc2API;
        setWebClient(WebServiceUtil.getWebClientBuilder(objectMapper, 500 * 1024).build());
        Flux.interval(Duration.ofSeconds(0), BlizzardSC2API.REQUEST_SLOT_REFRESH_TIME)
            .doOnNext(i->rateLimiter.refreshSlots(REQUESTS_PER_SECOND_CAP)).subscribe();
        initErrorRates(varDAO);
    }

    private void initErrorRates(VarDAO varDAO)
    {
        for(Region r : Region.values())
            healthMonitors.put(r, new APIHealthMonitor(varDAO, r.getId() + ".blizzard.api.web"));

        Flux.interval(BlizzardSC2API.ERROR_RATE_FRAME, BlizzardSC2API.ERROR_RATE_FRAME)
            .doOnNext(i->calculateErrorRates()).subscribe();
    }

    private void calculateErrorRates()
    {
        healthMonitors.forEach((region, monitor)->LOG.debug("{} error rate: {}%", region, monitor.update()));
    }

    protected void setRegionUri(String uri)
    {
        this.regionUri = uri;
    }

    public Mono<BlizzardProfileLadder> getProfileLadder
    (Tuple3<Region, BlizzardPlayerCharacter[], Long> id, Set<QueueType> queueTypes)
    {
        return sc2API.getProfileLadder(id, queueTypes, this);
    }

    @Override
    public Mono<BlizzardProfileLadder> getProfileLadderMono
    (Region region, BlizzardPlayerCharacter character, long id, Set<QueueType> queueTypes)
    {
        return getWebClient()
            .get()
            .uri
            (
                regionUri != null ? regionUri : (region.getBaseWebUrl() + "sc2/profile/{0}/{1}/{2}/ladder/{1}"),
                region.getId(), character.getRealm(), character.getId(), id
            )
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(String.class)
            .flatMap((s)->
            {
                try
                {
                    return sc2API.extractProfileLadder(s, id, queueTypes);
                }
                catch (JsonProcessingException e)
                {
                    throw new IllegalStateException("Invalid json structure", e);
                }
            })
            .delaySubscription(rateLimiter.requestSlot())
            .doOnRequest(s->healthMonitors.get(region).addRequest())
            .doOnError(t->healthMonitors.get(region).addError());
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

}
