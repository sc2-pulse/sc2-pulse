// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.liquipedia;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPatch;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPlayer;
import com.nephest.battlenet.sc2.model.liquipedia.query.revision.LiquipediaMediaWikiRevisionQueryResult;
import com.nephest.battlenet.sc2.web.service.BaseAPI;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class LiquipediaAPI
extends BaseAPI
{

    public static final String BASE_URL = "https://liquipedia.net/starcraft2/api.php";
    public static final int REQUESTS_PER_PERIOD = 1;
    private final ReactorRateLimiter rateLimiter = new ReactorRateLimiter();
    private final int PATCH_BATCH_SIZE = 20;

    @Autowired
    public LiquipediaAPI
    (
        ObjectMapper objectMapper,
        @Value("${com.nephest.battlenet.sc2.useragent}") String userAgent
    )
    {
        initClient(objectMapper, userAgent);
    }

    private void initClient(ObjectMapper objectMapper, String userAgent)
    {
        setWebClient(WebServiceUtil.getWebClientBuilder(objectMapper, 800 * 1024)
            .baseUrl(BASE_URL)
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .build());
    }

    @Scheduled(cron="*/4 * * * * *")
    public void refreshRateLimiter()
    {
        rateLimiter.refreshSlots(REQUESTS_PER_PERIOD);
    }

    public static UriBuilder wikiContentQuery(UriBuilder builder, Set<String> names)
    {
        return builder.queryParam("action", "query")
            .queryParam("titles", String.join("|", names))
            .queryParam("prop", "revisions")
            .queryParam("rvslots", "*")
            .queryParam("rvprop", "content")
            .queryParam("formatversion", "2")
            .queryParam("format", "json");
    }

    public Mono<LiquipediaMediaWikiRevisionQueryResult> getPage(Set<String> names)
    {
        if(names.isEmpty()) return Mono.empty();

        return getWebClient()
            .get()
            .uri(builder->wikiContentQuery(builder, names).build())
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(LiquipediaMediaWikiRevisionQueryResult.class)
            .delaySubscription(rateLimiter.requestSlot());
    }

    public Flux<LiquipediaPlayer> parsePlayers(Set<String> names)
    {
        return getPage(names)
            .map(LiquipediaParser::parse)
            .flatMap(this::processRedirects)
            .flatMapIterable(Function.identity());
    }

    public Flux<LiquipediaPatch> parsePatches()
    {
        return getPage(Set.of("Patches"))
            .flatMapIterable(LiquipediaParser::parsePatchList);
    }

    public Flux<LiquipediaPatch> parsePatches(Iterable<? extends LiquipediaPatch> patchList)
    {
        LiquipediaPatch[] balanceUpdates = StreamSupport.stream(patchList.spliterator(), false)
            .filter(LiquipediaPatch::isBalanceUpdate)
            .toArray(LiquipediaPatch[]::new);
        Map<String, LiquipediaPatch> patches = StreamSupport.stream(patchList.spliterator(), false)
            .collect(Collectors.toMap(LiquipediaPatch::getVersion, Function.identity()));
        return Flux.fromIterable(patchList)
            .filter(patch->!patch.isBalanceUpdate())
            .map(LiquipediaPatch::getVersion)
            .distinct()
            .map(version->"Patch_" + version)
            .buffer(PATCH_BATCH_SIZE, HashSet::new)
            .flatMap(this::getPage)
            .flatMapIterable(LiquipediaParser::parsePatches)
            .map(patch->LiquipediaParser.mergePatch(patches.get(patch.getVersion()), patch))
            .concatWithValues(balanceUpdates)
            .sort(Comparator.comparing(LiquipediaPatch::getBuild, Comparator.reverseOrder()));
    }

    private Mono<Collection<LiquipediaPlayer>> processRedirects
    (
        Collection<LiquipediaPlayer> players
    )
    {
        Map<String, String> redirects = players.stream()
            .filter(player->player.getRedirect() != null)
            .collect(Collectors.toMap(LiquipediaPlayer::getRedirect, LiquipediaPlayer::getQueryName));
        if(redirects.isEmpty()) return Mono.just(players);

        return getPage(redirects.keySet())
            .map(LiquipediaParser::parse)
            .map(redirectedPlayers->
            {
                redirectedPlayers.forEach(p->p.setQueryName(redirects.get(p.getName())));
                players.removeIf(player->player.getRedirect() != null);
                players.addAll(redirectedPlayers);
                return players;
            });
    }

}
