// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.liquipedia;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaMediaWikiParseResult;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPlayer;
import com.nephest.battlenet.sc2.web.service.BaseAPI;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
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

    @Scheduled(cron="0,30 * * * * *")
    public void refreshRateLimiter()
    {
        rateLimiter.refreshSlots(REQUESTS_PER_PERIOD);
    }

    public Mono<LiquipediaMediaWikiParseResult> getPlayer(String name)
    {
        return getWebClient()
            .get()
            .uri
            (
                b->b.queryParam("action", "parse")
                    .queryParam("page", name)
                    .queryParam("format", "json")
                    .build()
            )
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(LiquipediaMediaWikiParseResult.class)
            .delaySubscription(rateLimiter.requestSlot());
    }

    public Mono<LiquipediaPlayer> parsePlayer(String name)
    {
        return getPlayer(name)
            .map(r->LiquipediaParser.parsePlayer(r.getParse().getText().getValue()));
    }

}
