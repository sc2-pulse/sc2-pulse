// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static com.nephest.battlenet.sc2.model.BaseLeague.LeagueType.GRANDMASTER;
import static com.nephest.battlenet.sc2.model.TeamFormat.ARCHON;
import static com.nephest.battlenet.sc2.model.TeamFormat._1V1;
import static org.springframework.http.MediaType.ALL;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.BaseLeague;
import com.nephest.battlenet.sc2.model.PlayerCharacterNaturalId;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.TeamFormat;
import com.nephest.battlenet.sc2.model.TeamType;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardDataSeason;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLadder;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLadderLeague;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLadderMembership;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeague;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLeagueTier;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardLegacyProfile;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardMatches;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardPlayerCharacter;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfile;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileLadder;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardProfileTeam;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardSeason;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTeam;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardTierDivision;
import com.nephest.battlenet.sc2.model.blizzard.cache.BlizzardCachePatchRoot;
import com.nephest.battlenet.sc2.model.local.DoubleVar;
import com.nephest.battlenet.sc2.model.local.DurationVar;
import com.nephest.battlenet.sc2.model.local.InstantVar;
import com.nephest.battlenet.sc2.model.local.League;
import com.nephest.battlenet.sc2.model.local.LongVar;
import com.nephest.battlenet.sc2.model.local.Patch;
import com.nephest.battlenet.sc2.model.local.Var;
import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import com.nephest.battlenet.sc2.util.LogUtil;
import com.nephest.battlenet.sc2.util.MiscUtil;
import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import javax.annotation.PostConstruct;
import javax.validation.ValidationException;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.RemoveAuthorizedClientOAuth2AuthorizationFailureHandler;
import org.springframework.security.oauth2.client.web.reactive.function.client.ServletOAuth2AuthorizedClientExchangeFilterFunction;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple3;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuple5;
import reactor.util.function.Tuples;
import reactor.util.retry.RetrySpec;

@Service
public class BlizzardSC2API
extends BaseAPI
{

    private static final Logger LOG = LoggerFactory.getLogger(BlizzardSC2API.class);

    public static final float REQUESTS_PER_SECOND_CAP = 10f;
    public static final float REQUESTS_PER_HOUR_CAP = 36000f
        - REQUESTS_PER_SECOND_CAP * 5; //max clock desync: 5 seconds
    public static final float REQUESTS_PER_SECOND_CAP_WEB = 10f;
    public static final int DELAY = 1000;
    public static final int FIRST_SEASON = 28;
    public static final int PROFILE_LADDER_RETRY_COUNT = 3;
    public static final Duration ERROR_RATE_FRAME = Duration.ofMinutes(60);
    public static final Duration HEALTH_SAVE_FRAME = Duration.ofMinutes(3);
    public static final double RETRY_ERROR_RATE_THRESHOLD = 40.0;
    public static final double FORCE_REGION_ERROR_RATE_THRESHOLD = 40.0;
    public static final Duration AUTO_FORCE_REGION_MAX_DURATION = Duration.ofDays(7);
    public static final Duration IO_TIMEOUT = Duration.ofSeconds(50);
    public static final Duration SHORT_IO_TIMEOUT = Duration.ofSeconds(20);
    public static final String SYSTEM_REQUEST_LIMIT_PRIORITY_NAME = "system";
    public static final int SYSTEM_HOURLY_REQUEST_LIMIT_PRIORITY_SLOTS = 200;
    /*
        This data is mainly used in ladder discovery process when starting with an empty DB. The values should be
        manually updated when a new season begins.
        Season 50
     */
    public static final Map<Region, Long> LAST_LADDER_IDS = Map.of
    (
        Region.US, 307074L,
        Region.EU, 244849L,
        Region.KR, 77775L,
        Region.CN, 67627L
    );
    public static final Map<Region, Region> DEFAULT_REGION_REDIRECTS = Map.of
    (
        Region.US, Region.KR,
        Region.EU, Region.KR,
        Region.KR, Region.US,
        Region.CN, Region.CN
    );

    private String regionUri;
    private final Map<Region, Var<Region>> forceRegions = new EnumMap<>(Region.class);
    private final Map<Region, InstantVar> forceRegionInstants = new EnumMap<>(Region.class);
    private final ObjectMapper objectMapper;
    private final Map<Region, WebClient> clients = new EnumMap<>(Region.class);
    private WebClient unauthorizedClient;
    private final Map<Region, LongVar> ignoreClientSslErrors = new EnumMap<>(Region.class);
    private final Map<Region, DurationVar> clientTimeouts = new EnumMap<>(Region.class);
    private final Map<Region, DoubleVar> requestsPerSecondCaps = new EnumMap<>(Region.class);
    private final Map<Region, DoubleVar> requestsPerHourCaps = new EnumMap<>(Region.class);
    private final Map<Region, ReactorRateLimiter> rateLimiters = new HashMap<>();
    private final Map<Region, ReactorRateLimiter> hourlyRateLimiters = new EnumMap<>(Region.class);
    private final Map<Region, List<ReactorRateLimiter>> regionalRateLimiters = new EnumMap<>(Region.class);
    private final Map<Region, List<ReactorRateLimiter>> regionalWebRateLimiters = new EnumMap<>(Region.class);
    private final Map<Region, APIHealthMonitor> healthMonitors = new EnumMap<>(Region.class);
    private final ReactorRateLimiter webRateLimiter = new ReactorRateLimiter();
    private final Map<Region, APIHealthMonitor> webHealthMonitors = new EnumMap<>(Region.class);
    private final VarDAO varDAO;
    private final GlobalContext globalContext;

    @Value("${com.nephest.battlenet.sc2.api.force.region.auto:#{'false'}}")
    private boolean autoForceRegion = false;
    private final boolean separateRequestLimits;

    @Autowired
    public BlizzardSC2API
    (
        ObjectMapper objectMapper,
        OAuth2AuthorizedClientManager auth2AuthorizedClientManager,
        RemoveAuthorizedClientOAuth2AuthorizationFailureHandler failureHandler,
        VarDAO varDAO,
        GlobalContext globalContext,
        @Value("${com.nephest.battlenet.sc2.api.request.limit.separate:#{'false'}}") boolean separateRequestLimits
    )
    {
        this.globalContext = globalContext;
        initVars(varDAO, globalContext.getActiveRegions());
        initWebClient
        (
            objectMapper,
            auth2AuthorizedClientManager,
            failureHandler,
            globalContext.getActiveRegions()
        );
        this.objectMapper = objectMapper;
        this.varDAO = varDAO;
        this.separateRequestLimits = separateRequestLimits;
        init(globalContext.getActiveRegions());
        initErrorRates(varDAO, globalContext.getActiveRegions());
        initRequestLimiters(separateRequestLimits);
        Flux.interval(HEALTH_SAVE_FRAME).doOnNext(i->saveHealth()).subscribe();
    }

    @PostConstruct
    public void postConstruct()
    {
        if(isAutoForceRegion())
        {
            LOG.warn("Auto force region is enabled");
            autoForceRegion();
        }
    }

    private void initVars(VarDAO varDAO, Set<Region> activeRegions)
    {
        for(Region r : activeRegions)
        {
            requestsPerHourCaps.put(r, new DoubleVar(varDAO, r.getId() + ".blizzard.api.rph", false));
            requestsPerSecondCaps.put(r, new DoubleVar(varDAO, r.getId() + ".blizzard.api.rps", false));
            ignoreClientSslErrors.put(r, new LongVar(varDAO, r.getId() + ".blizzard.api.ssl.error.ignore", false));
            clientTimeouts.put(r, new DurationVar(varDAO, r.getId() + ".blizzard.api.timeout", false));
        }
        Stream.of
        (
            requestsPerHourCaps.values().stream(),
            requestsPerSecondCaps.values().stream(),
            ignoreClientSslErrors.values().stream(),
            clientTimeouts.values().stream()
        )
            .flatMap(Function.identity())
            .map(v->(Var<?>) v)
            .forEach(Var::tryLoad);
        clientTimeouts.values().stream()
            .filter(v->v.getValue() == null)
            .forEach(v->v.setValue(IO_TIMEOUT));
    }

    private void initErrorRates(VarDAO varDAO, Set<Region> activeRegions)
    {
        for(Region r : activeRegions)
        {
            healthMonitors.put(r, new APIHealthMonitor(varDAO, r.getId() + ".blizzard.api"));
            webHealthMonitors.put(r, new APIHealthMonitor(varDAO, r.getId() + ".blizzard.api.web"));
        }
    }

    private void initRequestLimiters(boolean separate)
    {
        if(separate) LOG.warn("Using a separate rate limiter for each region");
        ReactorRateLimiter rateLimiter = new ReactorRateLimiter();
        ReactorRateLimiter hourlyLimiter = new ReactorRateLimiter();
        for(Region r : globalContext.getActiveRegions())
        {
            rateLimiters.put(r, separate ? new ReactorRateLimiter() : rateLimiter);
            hourlyRateLimiters.put(r, separate ? new ReactorRateLimiter() : hourlyLimiter);
            regionalRateLimiters.put(r, List.of(hourlyRateLimiters.get(r), rateLimiters.get(r)));
            regionalWebRateLimiters.put
            (
                r,
                List.of(hourlyRateLimiters.get(r), rateLimiters.get(r), webRateLimiter)
            );
        }

        addHourlyRequestLimitPriority
        (
            SYSTEM_REQUEST_LIMIT_PRIORITY_NAME,
            SYSTEM_HOURLY_REQUEST_LIMIT_PRIORITY_SLOTS
        );
        addRequestLimitPriority(SYSTEM_REQUEST_LIMIT_PRIORITY_NAME, 1);
        addWebRequestLimitPriority(SYSTEM_REQUEST_LIMIT_PRIORITY_NAME, 1);

        hourlyRateLimiters.forEach((r, limiter)-> limiter.refreshSlots(
            (int) (getRequestsPerHourCap(r) - healthMonitors.get(r).getRequests())));
    }

    private void init(Set<Region> activeRegions)
    {
        initForceRegion(activeRegions);
    }

    private void initForceRegion(Set<Region> activeRegions)
    {
        //catch exceptions to allow service autowiring for tests
        try
        {
            for(Region region : activeRegions)
            {
                forceRegions.put(region, new Var<>
                    (
                        varDAO,
                        region.getId() + ".blizzard.api.region.force",
                        r->r == null ? null : String.valueOf(r.getId()),
                        s->s == null || s.isEmpty() ? null : Region.from(Integer.parseInt(s)),
                        false
                    )
                );
                forceRegionInstants.put(region,
                    new InstantVar(varDAO, region.getId() + ".blizzard.api.region.force.timestamp", false));
            }
            for(Map.Entry<Region, Var<Region>> e : forceRegions.entrySet())
            {
                Region force = e.getValue().load();
                if(force != null) LOG.warn("Force region loaded: {}->{}", e.getKey(), force);
            }
            for(Map.Entry<Region, InstantVar> e : forceRegionInstants.entrySet())
            {
                Instant timestamp = e.getValue().load();
                if(timestamp != null) LOG.debug("Force region timestamp loaded: {} {}", e.getKey(), timestamp);
            }
        }
        catch(RuntimeException ex)
        {
            LOG.warn(ex.getMessage(), ex);
        }
    }

    protected void autoForceRegion()
    {
        if(!isAutoForceRegion()) return;
        for(Region region : globalContext.getActiveRegions())
        {
            if(forceRegions.get(region).getValue() != null)
            {
                Instant ts = forceRegionInstants.get(region).getValue();
                if(ts == null
                    || Instant.now().getEpochSecond() - ts.getEpochSecond() > AUTO_FORCE_REGION_MAX_DURATION.toSeconds())
                {
                    LOG.info("{} API host redirect timeout reached, removing redirect", region);
                    setForceRegion(region, null);
                }
            }
            else
            {
                if(healthMonitors.get(region).getErrorRate() <= FORCE_REGION_ERROR_RATE_THRESHOLD) continue;

                LOG.warn
                (
                    "{} API health threshold reached({}/{}), redirecting to default host",
                    region,
                    healthMonitors.get(region).getErrorRate(),
                    FORCE_REGION_ERROR_RATE_THRESHOLD
                );
                setForceRegion(region);
            }
        }
    }

    public Region getDefaultOrHealthyForceRegion(Region region)
    {
        Region defaultRegion = DEFAULT_REGION_REDIRECTS.get(region);
        if(getErrorRate(region, false) <= FORCE_REGION_ERROR_RATE_THRESHOLD) return defaultRegion;

        for(Map.Entry<Region, APIHealthMonitor> entry : healthMonitors.entrySet())
            if(entry.getKey() != region
                && entry.getValue().getErrorRate() > 0
                && entry.getValue().getErrorRate() <= FORCE_REGION_ERROR_RATE_THRESHOLD)
                    return entry.getKey();

        return defaultRegion;
    }

    protected APIHealthMonitor getHealthMonitor(Region region, boolean web)
    {
        return web
            ? webHealthMonitors.get(getRegion(region))
            : healthMonitors.get(getRegion(region));
    }

    public double getErrorRate(Region region, boolean web)
    {
        return web
            ? webHealthMonitors.get(getRegion(region)).getErrorRate()
            : healthMonitors.get(getRegion(region)).getErrorRate();
    }

    public boolean isSeparateRequestLimits()
    {
        return separateRequestLimits;
    }

    public double getRequestCapProgress(Region region)
    {
        return (healthMonitors.get(region).getRequests() + webHealthMonitors.get(region).getRequests())
            / (double) getRequestsPerHourCap(region);
    }

    public double getRequestCapProgress()
    {
        DoubleStream progressStream = globalContext.getActiveRegions().stream()
            .mapToDouble(this::getRequestCapProgress);
        return isSeparateRequestLimits() ? progressStream.max().orElse(0) : progressStream.sum();
    }

    public boolean requestCapNotReached()
    {
        return getRequestCapProgress() < MiscUtil.getHourProgress(LocalDateTime.now());
    }

    public void addHourlyRequestLimitPriority(String name, int slots)
    {
        hourlyRateLimiters.values().forEach(l->l.addPriorityLimiter(new ReactorRateLimiter(name, slots)));
    }

    public void addRequestLimitPriority(String name, int slots)
    {
        rateLimiters.values().forEach(l->l.addPriorityLimiter(new ReactorRateLimiter(name, slots)));
    }

    public void addWebRequestLimitPriority(String name, int slots)
    {
        webRateLimiter.addPriorityLimiter(new ReactorRateLimiter(name, slots));
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
        clients.replaceAll((r, v) -> client);
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

    private void initWebClient
    (
        ObjectMapper objectMapper,
        OAuth2AuthorizedClientManager auth2AuthorizedClientManager,
        RemoveAuthorizedClientOAuth2AuthorizationFailureHandler failureHandler,
        Set<Region> activeRegions
    )
    {
        for(Region region : activeRegions)
        {
            if(isIgnoreClientSslErrors(region))
                LOG.warn("Ignoring {} web client ssl errors", region);
            ServletOAuth2AuthorizedClientExchangeFilterFunction oauth2Client =
                new ServletOAuth2AuthorizedClientExchangeFilterFunction(auth2AuthorizedClientManager);
            oauth2Client.setDefaultClientRegistrationId("sc2-sys-" + region.name().toLowerCase());
            oauth2Client.setAuthorizationFailureHandler(failureHandler);
            HttpClient httpClient = getHttpClient(getTimeout(region), isIgnoreClientSslErrors(region));
            //some endpoints return invalid content type headers, ignore the headers and handle all types
            clients.put(region, WebServiceUtil.getWebClientBuilder(objectMapper, 600 * 1024, ALL)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .apply(oauth2Client.oauth2Configuration()).build());
        }
        HttpClient httpClient = getHttpClient(IO_TIMEOUT, false);
        unauthorizedClient = WebServiceUtil.getWebClientBuilder(objectMapper, 1024 * 1024, ALL)
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    protected void setRegionUri(String uri)
    {
        this.regionUri = uri;
    }

    public boolean isAutoForceRegion()
    {
        return autoForceRegion;
    }

    public void setAutoForceRegion(boolean autoForceRegion)
    {
        this.autoForceRegion = autoForceRegion;
    }

    public void setForceRegion(Region target)
    {
        setForceRegion(target, getDefaultOrHealthyForceRegion(target));
    }

    public void setForceRegion(Region target, Region force)
    {
        forceRegions.get(target).setValueAndSave(force);
        forceRegionInstants.get(target).setValueAndSave(force == null ? null : Instant.now());
        LOG.warn("Redirecting API host: {}->{}", target, force);
    }

    protected void setForceRegionInstant(Region region, Instant instant)
    {
        forceRegionInstants.get(region).setValueAndSave(instant);
    }
    
    protected Region getRegion(Region targetRegion)
    {
        Region forceRegion = forceRegions.get(targetRegion).getValue();
        return forceRegion != null ? forceRegion : targetRegion;
    }

    public Region getForceRegion(Region region)
    {
        return forceRegions.get(region).getValue();
    }

    private void setRequestCap
    (
        Map<Region, ? extends Var<Double>> caps,
        Region region,
        Float cap,
        Float maxCap,
        String capName
    )
    {
        if(cap != null && (cap < 0 || cap > maxCap))
        {
            throw new IllegalArgumentException
            (
                "Invalid cap value: " + cap
                + ", valid range: 0-" + maxCap
            );
        }
        Double dCap = cap != null ? cap.doubleValue() : null;
        if(isSeparateRequestLimits())
        {
            caps.get(region).setValueAndSave(dCap);
        }
        else
        {
            caps.values().forEach(v->v.setValueAndSave(dCap));
        }
        LOG.info
        (
            capName + " cap, {}: {}",
            isSeparateRequestLimits() ? region : globalContext.getActiveRegions(),
            cap
        );
    }

    public void setRequestsPerSecondCap(Region region, Float cap)
    {
        setRequestCap(requestsPerSecondCaps, region, cap, REQUESTS_PER_SECOND_CAP, "RPS");
    }

    public float getRequestsPerSecondCap(Region region)
    {
        Double override = requestsPerSecondCaps.get(region).getValue();
        return override != null ? override.floatValue() : REQUESTS_PER_SECOND_CAP;
    }

    public void setRequestsPerHourCap(Region region, Float cap)
    {
        setRequestCap(requestsPerHourCaps, region, cap, REQUESTS_PER_HOUR_CAP, "RPH");
    }

    public float getRequestsPerHourCap(Region region)
    {
        Double override = requestsPerHourCaps.get(region).getValue();
        return override != null ? override.floatValue() : REQUESTS_PER_HOUR_CAP;
    }

    private void refreshReactorSlots
    (
        Map<Region, ReactorRateLimiter> limiters,
        Function<Region, Float> limitFunction
    )
    {
        if(isSeparateRequestLimits())
        {
            limiters.forEach((key, value)->value.refreshSlots(limitFunction.apply(key)));
        }
        else
        {
            limiters.entrySet().stream()
                .findAny()
                .ifPresent(e->e.getValue().refreshSlots(limitFunction.apply(e.getKey())));
        }
    }

    @Scheduled(cron="* * * * * *")
    public void refreshReactorSlots()
    {
        refreshReactorSlots(rateLimiters, this::getRequestsPerSecondCap);
        webRateLimiter.refreshSlots(REQUESTS_PER_SECOND_CAP_WEB);
    }

    @Scheduled(cron="0 0 * * * *")
    public void refreshHourlyReactorSlots()
    {
        refreshReactorSlots(hourlyRateLimiters, this::getRequestsPerHourCap);
    }

    @Scheduled(cron="0 0 * * * *")
    public void processErrorRates()
    {
        calculateErrorRates();
        if(autoForceRegion) autoForceRegion();
    }

    private void calculateErrorRates()
    {
        healthMonitors.forEach((region, monitor)->LOG.debug("{} error rate: {}%", region, monitor.update()));
        webHealthMonitors.forEach((region, monitor)->LOG.debug("{} web error rate: {}%", region, monitor.update()));
    }

    private void saveHealth()
    {
        healthMonitors.values().forEach(APIHealthMonitor::save);
        webHealthMonitors.values().forEach(APIHealthMonitor::save);
    }

    private void autoTimeout()
    {
        for(Region region : globalContext.getActiveRegions())
        {
            Duration timeout = getErrorRate(region, false) < RETRY_ERROR_RATE_THRESHOLD
                ? IO_TIMEOUT
                : SHORT_IO_TIMEOUT;
            setTimeout(region, timeout);
        }
    }

    private static HttpClient getHttpClient(Duration ioTimeout, boolean ignoreSslErrors)
    {
        HttpClient httpClient = WebServiceUtil
            .getHttpClient(WebServiceUtil.CONNECT_TIMEOUT, ioTimeout);
        if(ignoreSslErrors) httpClient = httpClient
            .secure(t->t.sslContext(WebServiceUtil.INSECURE_SSL_CONTEXT));
        return httpClient;
    }

    public void setIgnoreClientSslErrors(Region region, boolean ignoreErrors)
    {
        boolean currentVal = ignoreClientSslErrors.get(region).getValue() != null;
        if(currentVal == ignoreErrors) return;

        HttpClient httpClient = getHttpClient(getTimeout(region), ignoreErrors);
        WebClient client = clients.get(region).mutate()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
        clients.put(region, client);
        ignoreClientSslErrors.get(region).setValueAndSave(ignoreErrors ? 1L : null);
        LOG.info("Ignoring {} web client ssl errors: {}", region, ignoreErrors);
    }

    public Boolean isIgnoreClientSslErrors(Region region)
    {
        return ignoreClientSslErrors.get(region).getValue() != null;
    }

    public void setTimeout(Region region, Duration timeout)
    {
        timeout = timeout != null ? timeout : IO_TIMEOUT;
        if(getTimeout(region).equals(timeout)) return;

        HttpClient httpClient = getHttpClient(timeout, isIgnoreClientSslErrors(region));
        WebClient client = clients.get(region).mutate()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
        clients.put(region, client);
        clientTimeouts.get(region).setValueAndSave(timeout);
        LOG.info("Changed {} web client timeout: {}", region, timeout);
    }

    public Duration getTimeout(Region region)
    {
        return clientTimeouts.get(region).getValue();
    }
    
    private RetrySpec getRetry(Region region, RetrySpec wanted, boolean web)
    {
        return getErrorRate(region, web) > RETRY_ERROR_RATE_THRESHOLD
            ? WebServiceUtil.RETRY_NEVER 
            : getRetry(wanted);
    }

    public ApiContext getContext(Region region, boolean web)
    {
        return new ApiContext
        (
            web ? regionalWebRateLimiters.get(region) : regionalRateLimiters.get(region),
            web ? webHealthMonitors.get(region) : healthMonitors.get(region),
            web ? region.getBaseWebUrl() : region.getBaseUrl()
        );
    }

    public Mono<BlizzardSeason> getSeason(Region region, Integer id)
    {
        return getWebClient(region)
            .get()
            .uri(regionUri != null ? regionUri : (region.getBaseUrl() + "data/sc2/season/{0}"), id)
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardDataSeason.class).cast(BlizzardSeason.class)
            .retryWhen(ReactorRateLimiter.retryWhen(
                regionalRateLimiters.get(region), getRetry(region, WebServiceUtil.RETRY, false)))
            .delaySubscription(ReactorRateLimiter.requestSlot(regionalRateLimiters.get(region)))
            .doOnRequest(s->healthMonitors.get(region).addRequest())
            .doOnError(t->healthMonitors.get(region).addError());
    }

    public Mono<BlizzardSeason> getCurrentSeason(Region originalRegion)
    {
        Region region = getRegion(originalRegion);
        return getWebClient(region)
            .get()
            .uri(regionUri != null ? regionUri : (region.getBaseUrl() + "sc2/ladder/season/{0}"), originalRegion.getId())
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardSeason.class)
            .retryWhen(ReactorRateLimiter.retryWhen(
                regionalRateLimiters.get(region), getRetry(region, WebServiceUtil.RETRY, false)))
            .delaySubscription(ReactorRateLimiter.requestSlot(regionalRateLimiters.get(region)))
            .doOnRequest(s->healthMonitors.get(region).addRequest())
            .doOnError(t->healthMonitors.get(region).addError());
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
        return getCurrentSeason(region)
            .flatMap(season->validate(season, startFrom))
            .onErrorResume(t->getLastSeason(region, startFrom))
            .flatMap(season->validate(season, startFrom));
    }

    private static Mono<BlizzardSeason> validate(BlizzardSeason season, int minSeason)
    {
        return season.getId() == null || season.getId() < minSeason
            ? Mono.error(new ValidationException("Invalid season id: " + season.getId()
                + ", expected " + minSeason + " or greater"))
            : Mono.just(season);
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
            .retryWhen(ReactorRateLimiter.retryWhen(
                regionalRateLimiters.get(region), getRetry(region, WebServiceUtil.RETRY, false)))
            .delaySubscription(ReactorRateLimiter.requestSlot(regionalRateLimiters.get(region)))
            .doOnRequest(s->healthMonitors.get(region).addRequest())
            .doOnError(t->healthMonitors.get(region).addError());

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
        Long id,
        String priorityName
    )
    {
        return getWebClient(region)
            .get()
            .uri(regionUri != null ? regionUri : (region.getBaseUrl() + "data/sc2/ladder/{0}"), id)
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BlizzardLadder.class)
            .retryWhen(ReactorRateLimiter.retryWhen(
                regionalRateLimiters.get(region), getRetry(region, WebServiceUtil.RETRY, false), priorityName))
            .delaySubscription(ReactorRateLimiter.requestSlot(regionalRateLimiters.get(region), priorityName))
            .doOnRequest(s->healthMonitors.get(region).addRequest())
            .doOnError(t->healthMonitors.get(region).addError());
    }

    public Mono<BlizzardLadder> getLadder
    (
        Region region,
        Long id
    )
    {
        return getLadder(region, id, null);
    }

    public Mono<BlizzardLadder> getFilteredLadder
    (
        Region region,
        Long id,
        long startingFromEpochSeconds,
        String priorityName
    )
    {
        return getWebClient(region)
            .get()
            .uri(regionUri != null ? regionUri : (region.getBaseUrl() + "data/sc2/ladder/{0}"), id)
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(String.class)
            .map(s->extractNewTeams(s, startingFromEpochSeconds))
            .retryWhen(ReactorRateLimiter.retryWhen(
                regionalRateLimiters.get(region), getRetry(region, WebServiceUtil.RETRY, false), priorityName))
            .delaySubscription(ReactorRateLimiter.requestSlot(regionalRateLimiters.get(region), priorityName))
            .doOnRequest(s->healthMonitors.get(region).addRequest())
            .doOnError(t->healthMonitors.get(region).addError());
    }

    public Mono<BlizzardLadder> getFilteredLadder
    (
        Region region,
        Long id,
        long startingFromEpochSeconds
    )
    {
        return getFilteredLadder(region, id, startingFromEpochSeconds, null);
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
        Map<Region, Set<Long>> errors,
        String priorityName
    )
    {
        return Flux.fromIterable(ladderIds)
            .flatMap(d->WebServiceUtil.getOnErrorLogAndSkipMono
            (
                startingFromEpochSeconds < 1 || errors.get(d.getT2()).contains(d.getT4().getLadderId())
                    ? getLadder(d.getT2(), d.getT4(), priorityName).zipWith(Mono.just(d))
                    : getFilteredLadder(d.getT2(), d.getT4().getLadderId(), startingFromEpochSeconds, priorityName).zipWith(Mono.just(d)),
                t->errors.get(d.getT2()).add(d.getT4().getLadderId())
            ));
    }

    public Flux<Tuple2<BlizzardLadder, Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>>> getLadders
    (
        Iterable<? extends Tuple4<BlizzardLeague, Region, BlizzardLeagueTier, BlizzardTierDivision>> ladderIds,
        long startingFromEpochSeconds,
        Map<Region, Set<Long>> errors
    )
    {
        return getLadders(ladderIds, startingFromEpochSeconds, errors, null);
    }

    public Mono<BlizzardLadder> getLadder
    (
        Region region,
        BlizzardTierDivision division,
        String priorityName
    )
    {
        return getLadder(region, division.getLadderId(), priorityName);
    }

    public Mono<BlizzardLadder> getLadder
    (
        Region region,
        BlizzardTierDivision division
    )
    {
        return getLadder(region, division, null);
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

    public Mono<Tuple3<Region, BlizzardPlayerCharacter[], Long>> getProfileLadderId
    (Region originalRegion, long ladderId, boolean web)
    {
        Region region = getRegion(originalRegion);
        ApiContext context = getContext(region, web);
        RetrySpec retry = getRetry(region, WebServiceUtil.RETRY_SKIP_NOT_FOUND, web);
        /*
            profile id discovery is a very important task, add retry spec if there is a chance of getting
            the correct data
         */
        if(retry == WebServiceUtil.RETRY_NEVER && getErrorRate(region, web) < 100) retry = WebServiceUtil.RETRY;
        return getWebClient(region)
            .get()
            .uri
            (
                regionUri != null ? regionUri : (context.getBaseUrl() + "sc2/legacy/ladder/{0}/{1}"),
                originalRegion.getId(), ladderId
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
                    return Tuples.of(originalRegion, characters, ladderId);
                }
                catch (JsonProcessingException e)
                {
                    throw new IllegalStateException("Invalid json structure", e);
                }
            })
            .retryWhen(ReactorRateLimiter.retryWhen(context.getRateLimiters(), retry))
            .delaySubscription(ReactorRateLimiter.requestSlot(context.getRateLimiters()))
            .doOnRequest(s->context.getHealthMonitor().addRequest())
            .doOnError(t->context.getHealthMonitor().addError());
    }

    public Flux<Tuple3<Region, BlizzardPlayerCharacter[], Long>> getProfileLadderIds
    (Region region, long from, long toExcluded, boolean web)
    {
        return Flux.fromStream(LongStream.range(from, toExcluded).boxed())
            .flatMap(l->WebServiceUtil.getOnErrorLogAndSkipLogLevelMono(
                getProfileLadderId(region, l, web),
                (t)->ExceptionUtils.getRootCause(t) instanceof WebClientResponseException.NotFound
                    ? LogUtil.LogLevel.DEBUG
                    : LogUtil.LogLevel.WARNING));
    }

    public Flux<Tuple3<Region, BlizzardPlayerCharacter[], Long>> getProfileLadderIds
    (Region region, Iterable<? extends Long> ids, boolean web)
    {
        return Flux.fromIterable(ids)
            .flatMap(l->WebServiceUtil.getOnErrorLogAndSkipLogLevelMono(
                getProfileLadderId(region, l, web),
                (t)->ExceptionUtils.getRootCause(t) instanceof WebClientResponseException.NotFound
                    ? LogUtil.LogLevel.DEBUG
                    : LogUtil.LogLevel.WARNING));
    }

    public Mono<BlizzardProfileLadder> getProfileLadder
    (Tuple3<Region, BlizzardPlayerCharacter[], Long> id, Set<QueueType> queueTypes)
    {
        return getProfileLadder(id, queueTypes, false);
    }

    public Mono<BlizzardProfileLadder> getProfileLadder
    (
        Tuple3<Region, BlizzardPlayerCharacter[], Long> id,
        Set<QueueType> queueTypes,
        boolean web,
        String priorityName
    )
    {
        return chainProfileLadderMono(id, 0, queueTypes, web, priorityName);
    }

    public Mono<BlizzardProfileLadder> getProfileLadder
    (
        Tuple3<Region, BlizzardPlayerCharacter[], Long> id,
        Set<QueueType> queueTypes,
        boolean web
    )
    {
        return getProfileLadder(id, queueTypes, web, null);
    }

    private Mono<BlizzardProfileLadder> chainProfileLadderMono
    (
        Tuple3<Region, BlizzardPlayerCharacter[], Long> id,
        int ix,
        Set<QueueType> queueTypes,
        boolean web,
        String priorityName
    )
    {
        int prevIx = ix - 1;
        if(ix > 0) LOG.debug("Profile ladder not found {} times: {}/{}/{}",
            ix, id.getT2()[prevIx].getRealm(), id.getT2()[prevIx].getId(), id.getT3());
        return Mono.defer(()->
        {
            if(ix < id.getT2().length)
            {
                return getProfileLadderMono
                (
                    id.getT1(),
                    id.getT2()[ix],id.getT3(),
                    queueTypes,
                    web,
                    priorityName
                )
                    .onErrorResume((t)->{
                        if(t.getMessage().startsWith("Invalid game mode")) return Mono.error(t);
                        LOG.debug(t.getMessage(), t);
                        return chainProfileLadderMono(id, ix + 1, queueTypes, web);
                    });
            }
            return Mono.error(new NoRetryException(
                "Profile ladder not found",
                "Profile ladder not found {}/{}/{}",
                id.getT2()[prevIx].getRealm(), id.getT2()[prevIx].getId(), id.getT3()
            ));
        });
    }

    private Mono<BlizzardProfileLadder> chainProfileLadderMono
    (
        Tuple3<Region, BlizzardPlayerCharacter[], Long> id,
        int ix,
        Set<QueueType> queueTypes,
        boolean web
    )
    {
        return chainProfileLadderMono(id, ix, queueTypes, web, null);
    }


    public Mono<BlizzardProfileLadder> getProfileLadderMono
    (
        Region originalRegion,
        BlizzardPlayerCharacter character,
        long id,
        Set<QueueType> queueTypes,
        boolean web,
        String priorityName
    )
    {
        Region region = getRegion(originalRegion);
        ApiContext context = getContext(region, web);
        return getWebClient(region)
            .get()
            .uri
                (
                    regionUri != null ? regionUri : context.getBaseUrl() + "sc2/profile/{0}/{1}/{2}/ladder/{3}",
                    originalRegion.getId(), character.getRealm(), character.getId(), id
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
            .retryWhen(ReactorRateLimiter.retryWhen(
                context.getRateLimiters(), getRetry(region, WebServiceUtil.RETRY_SKIP_NOT_FOUND, web), priorityName))
            .delaySubscription(ReactorRateLimiter.requestSlot(context.getRateLimiters(), priorityName))
            .doOnRequest(s->context.getHealthMonitor().addRequest())
            .doOnError(t->context.getHealthMonitor().addError());
    }

    public Mono<BlizzardProfileLadder> getProfileLadderMono
    (
        Region originalRegion,
        BlizzardPlayerCharacter character,
        long id,
        Set<QueueType> queueTypes,
        boolean web
    )
    {
        return getProfileLadderMono(originalRegion, character, id, queueTypes, web, null);
    }

    protected Mono<BlizzardProfileLadder> extractProfileLadder(String s, long ladderId, Set<QueueType> queues)
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
    (
        Iterable<? extends Tuple3<Region, BlizzardPlayerCharacter[], Long>> ids,
        Set<QueueType> queueTypes,
        boolean web,
        String priorityName
    )
    {
        return Flux.fromIterable(ids)
            .flatMap(id->WebServiceUtil
                .getOnErrorLogAndSkipLogLevelMono(
                    getProfileLadder(id, queueTypes, web, priorityName),
                    (t)->t.getMessage().startsWith("Invalid game mode")
                        ? LogUtil.LogLevel.DEBUG
                        : LogUtil.LogLevel.WARNING)
                .zipWith(Mono.just(id)));
    }

    public Flux<Tuple2<BlizzardProfileLadder, Tuple3<Region, BlizzardPlayerCharacter[], Long>>> getProfileLadders
    (
        Iterable<? extends Tuple3<Region, BlizzardPlayerCharacter[], Long>> ids,
        Set<QueueType> queueTypes,
        boolean web
    )
    {
        return getProfileLadders(ids, queueTypes, web, null);
    }

    public Flux<Tuple2<BlizzardProfileLadder, Tuple3<Region, BlizzardPlayerCharacter[], Long>>> getProfileLadders
    (Iterable<? extends Tuple3<Region, BlizzardPlayerCharacter[], Long>> ids, Set<QueueType> queueTypes)
    {
        return getProfileLadders(ids, queueTypes, false);
    }

    public Mono<Tuple2<BlizzardMatches, PlayerCharacterNaturalId>> getMatches
    (
        PlayerCharacterNaturalId playerCharacter,
        boolean web,
        String priorityName
    )
    {
        Region region = getRegion(playerCharacter.getRegion());
        ApiContext context = getContext(region, web);
        return getWebClient(region)
            .get()
            .uri
            (
                regionUri != null
                    ? regionUri
                    : (context.getBaseUrl() + "sc2/legacy/profile/{0}/{1}/{2}/matches"),
                playerCharacter.getRegion().getId(),
                playerCharacter.getRealm(),
                playerCharacter.getBattlenetId()
            )
            .accept(web ? ALL : APPLICATION_JSON) //web API has invalid content type headers
            .retrieve()
            .bodyToMono(BlizzardMatches.class)
            .zipWith(Mono.just(playerCharacter))
            .retryWhen(ReactorRateLimiter.retryWhen(
                context.getRateLimiters(), getRetry(region, WebServiceUtil.RETRY, web), priorityName))
            .delaySubscription(ReactorRateLimiter.requestSlot(context.getRateLimiters(), priorityName))
            .doOnRequest(s->context.getHealthMonitor().addRequest())
            .doOnError(t->context.getHealthMonitor().addError());
    }

    public Mono<Tuple2<BlizzardMatches, PlayerCharacterNaturalId>> getMatches
    (
        PlayerCharacterNaturalId playerCharacter,
        boolean web
    )
    {
        return getMatches(playerCharacter, web, null);
    }

    public Flux<Tuple2<BlizzardMatches, PlayerCharacterNaturalId>> getMatches
    (
        Iterable<? extends PlayerCharacterNaturalId> playerCharacters,
        Set<PlayerCharacterNaturalId> errors,
        boolean web,
        String priorityName
    )
    {
        return Flux.fromIterable(playerCharacters)
            .flatMap(p->WebServiceUtil.getOnErrorLogAndSkipMono(getMatches(p, web, priorityName), t->errors.add(p)));
    }

    public Flux<Tuple2<BlizzardMatches, PlayerCharacterNaturalId>> getMatches
    (
        Iterable<? extends PlayerCharacterNaturalId> playerCharacters,
        Set<PlayerCharacterNaturalId> errors,
        boolean web
    )
    {
        return getMatches(playerCharacters, errors, web, null);
    }

    public Mono<Tuple2<BlizzardLegacyProfile, PlayerCharacterNaturalId>> getLegacyProfile
    (PlayerCharacterNaturalId playerCharacter, boolean web)
    {
        Region region = getRegion(playerCharacter.getRegion());
        ApiContext context = getContext(region, web);
        return getWebClient(region)
            .get()
            .uri
            (
                regionUri != null
                    ? regionUri
                    : (context.getBaseUrl() + "sc2/legacy/profile/{0}/{1}/{2}"),
                playerCharacter.getRegion().getId(),
                playerCharacter.getRealm(),
                playerCharacter.getBattlenetId()
            )
            .accept(web ? ALL : APPLICATION_JSON) //web API has invalid content type headers
            .retrieve()
            .bodyToMono(BlizzardLegacyProfile.class)
            .zipWith(Mono.just(playerCharacter))
            .retryWhen(ReactorRateLimiter.retryWhen(
                context.getRateLimiters(), getRetry(region, WebServiceUtil.RETRY_NEVER, web)))
            .delaySubscription(ReactorRateLimiter.requestSlot(context.getRateLimiters()))
            .doOnRequest(s->context.getHealthMonitor().addRequest())
            .doOnError(t->context.getHealthMonitor().addError());
    }

    public Flux<Tuple2<BlizzardLegacyProfile, PlayerCharacterNaturalId>> getLegacyProfiles
    (Iterable<? extends PlayerCharacterNaturalId> playerCharacters, boolean web)
    {
        return Flux.fromIterable(playerCharacters)
            .flatMap(p->WebServiceUtil.getOnErrorLogAndSkipMono(getLegacyProfile(p, web)));
    }

    public Mono<Tuple2<BlizzardProfile, PlayerCharacterNaturalId>> getProfile
    (PlayerCharacterNaturalId playerCharacter, boolean web)
    {
        Region region = getRegion(playerCharacter.getRegion());
        ApiContext context = getContext(region, web);
        return getWebClient(region)
            .get()
            .uri
            (
                regionUri != null
                    ? regionUri
                    : (context.getBaseUrl() + "sc2/profile/{0}/{1}/{2}"),
                playerCharacter.getRegion().getId(),
                playerCharacter.getRealm(),
                playerCharacter.getBattlenetId()
            )
            .accept(web ? ALL : APPLICATION_JSON) //web API has invalid content type headers
            .retrieve()
            .bodyToMono(BlizzardProfile.class)
            .zipWith(Mono.just(playerCharacter))
            .retryWhen(ReactorRateLimiter.retryWhen(
                context.getRateLimiters(), getRetry(region, WebServiceUtil.RETRY_NEVER, web)))
            .delaySubscription(ReactorRateLimiter.requestSlot(context.getRateLimiters()))
            .doOnRequest(s->context.getHealthMonitor().addRequest())
            .doOnError(t->context.getHealthMonitor().addError());
    }

    public Flux<Tuple2<BlizzardProfile, PlayerCharacterNaturalId>> getProfiles
    (Iterable<? extends PlayerCharacterNaturalId> playerCharacters, boolean web)
    {
        return Flux.fromIterable(playerCharacters)
            .flatMap(p->WebServiceUtil.getOnErrorLogAndSkipMono(getProfile(p, web)));
    }

    public Flux<BlizzardFullPlayerCharacter> getPlayerCharacters
    (
        Region region,
        Long profileId,
        boolean web
    )
    {
        if(region == Region.CN) throw new UnsupportedOperationException("CN region is not supported");
        ApiContext context = getContext(region, web);
        return getWebClient(region)
            .get()
            .uri
            (
                regionUri != null
                    ? regionUri
                    : (context.getBaseUrl() + "sc2/player/{0}"),
                profileId
            )
            .accept(web ? ALL : APPLICATION_JSON)
            .retrieve()
            .bodyToFlux(BlizzardFullPlayerCharacter.class)
            .retryWhen(ReactorRateLimiter.retryWhen
            (
                context.getRateLimiters(),
                getRetry(region, WebServiceUtil.RETRY_ONCE, web),
                SYSTEM_REQUEST_LIMIT_PRIORITY_NAME
            ))
            .delaySubscription(ReactorRateLimiter.requestSlot
            (
                context.getRateLimiters(),
                SYSTEM_REQUEST_LIMIT_PRIORITY_NAME
            ))
            .doOnRequest(s->context.getHealthMonitor().addRequest())
            .doOnError(t->context.getHealthMonitor().addError());
    }

    public Flux<BlizzardFullPlayerCharacter> getPlayerCharacters
    (
        Region region,
        Long profileId
    )
    {
        return webHealthMonitors.values().stream()
            .map(APIHealthMonitor::getRequests)
            .anyMatch(l->l > 0)
                ? getPlayerCharacters(region, profileId, true)
                : getPlayerCharacters(region, profileId, false)
                    .onErrorResume(e->getPlayerCharacters(region, profileId, true));
    }

    public Flux<Patch> getPatches(Region region, Long minId, Long maxId, int limit)
    {
        ApiContext context = getContext(region, true);
        return unauthorizedClient
            .get()
            .uri(region.getCacheUrl(),
                b->b
                .path("system/cms/oauth/api/patchnote/list")
                .queryParam("program", "s2")
                .queryParam("region", region.name())
                .queryParam("locale", "enUS")
                .queryParam("type", "RETAIL")
                .queryParam("page", "1")
                .queryParam("pageSize", limit)
                .queryParam("orderBy", "buildNumber")
                .queryParam("buildNumberMin", minId)
                .queryParam("buildNumberMax", maxId)
                .build()
            )
            .accept(ALL)
            .retrieve()
            .bodyToMono(BlizzardCachePatchRoot.class)
            .flatMapMany
            (
                root->root.getPatchNotes() != null
                    ? Flux.fromArray(root.getPatchNotes())
                    : Flux.empty()
            )
            .map(Patch::from)
            .retryWhen(ReactorRateLimiter.retryWhen(
                context.getRateLimiters(),
                getRetry(region, WebServiceUtil.RETRY, true),
                SYSTEM_REQUEST_LIMIT_PRIORITY_NAME))
            .delaySubscription(ReactorRateLimiter.requestSlot(context.getRateLimiters(),
                SYSTEM_REQUEST_LIMIT_PRIORITY_NAME))
            .doOnRequest(s->context.getHealthMonitor().addRequest())
            .doOnError(t->context.getHealthMonitor().addError());
    }

}
