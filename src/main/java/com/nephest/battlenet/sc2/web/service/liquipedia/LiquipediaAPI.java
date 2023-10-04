// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.liquipedia;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPlayer;
import com.nephest.battlenet.sc2.model.liquipedia.query.revision.LiquipediaMediaWikiRevisionQueryResult;
import com.nephest.battlenet.sc2.web.service.BaseAPI;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class LiquipediaAPI
extends BaseAPI
{

    public static final String BASE_URL = "https://liquipedia.net/starcraft2/api.php";
    public static final int REQUESTS_PER_PERIOD = 1;
    private final ReactorRateLimiter rateLimiter = new ReactorRateLimiter();
    private final String userAgent;

    @Autowired
    public LiquipediaAPI
    (
        ObjectMapper objectMapper,
        @Value("${com.nephest.battlenet.sc2.url.public:#{'local dev environment'}}") String uaUrl,
        @Value("${contacts.email:}") String uaEmail
    )
    {
        userAgent = "SC2Pulse (" + uaUrl + (uaEmail != null ? "; " + uaEmail : "") + ")";
        initClient(objectMapper);
    }

    private void initClient(ObjectMapper objectMapper)
    {
        setWebClient(WebServiceUtil.getWebClientBuilder(objectMapper, 800 * 1024)
            .baseUrl(BASE_URL)
            .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
            .build());
    }

    public String getUserAgent()
    {
        return userAgent;
    }

    @Scheduled(cron="*/4 * * * * *")
    public void refreshRateLimiter()
    {
        rateLimiter.refreshSlots(REQUESTS_PER_PERIOD);
    }

    public Mono<LiquipediaMediaWikiRevisionQueryResult> getPlayer(Set<String> names)
    {
        if(names.isEmpty()) return Mono.empty();

        return getWebClient()
            .get()
            .uri
            (
                b->b.queryParam("action", "query")
                    .queryParam("titles", String.join("|", names))
                    .queryParam("prop", "revisions")
                    .queryParam("rvslots", "*")
                    .queryParam("rvprop", "content")
                    .queryParam("formatversion", "2")
                    .queryParam("format", "json")
                    .build()
            )
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(LiquipediaMediaWikiRevisionQueryResult.class)
            .delaySubscription(rateLimiter.requestSlot());
    }

    public Flux<LiquipediaPlayer> parsePlayers(Set<String> names)
    {
        return getPlayer(names)
            .map(LiquipediaParser::parse)
            .flatMap(this::processRedirects)
            .flatMapIterable(Function.identity());
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

        return getPlayer(redirects.keySet())
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
