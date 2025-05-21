// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.springframework.http.MediaType.APPLICATION_JSON;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.model.bilibili.BilibiliStream;
import com.nephest.battlenet.sc2.model.bilibili.BilibiliStreamSearch;
import com.nephest.battlenet.sc2.model.bilibili.BilibiliStreamSearchData;
import com.nephest.battlenet.sc2.web.util.ReactorRateLimiter;
import java.util.Comparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class BilibiliAPI
extends BaseAPI
{

    public static final int SLOTS_PER_PERIOD = 2;
    public static final long STAR_CRAFT_2_PARENT_AREA_ID = 2;
    public static final long STAR_CRAFT_2_AREA_ID = 93;
    public static final Comparator<BilibiliStream> COMPARATOR = Comparator
        .comparing(s->s.getWatchedShow().getNum(), Comparator.reverseOrder());

    private final ReactorRateLimiter rateLimiter = new ReactorRateLimiter();

    @Autowired
    public BilibiliAPI(ObjectMapper objectMapper)
    {
        initClient(objectMapper);
    }

    @Scheduled(cron="* * * * * *")
    public void refreshReactorSlots()
    {
        rateLimiter.refreshSlots(SLOTS_PER_PERIOD);
    }

    private void initClient(ObjectMapper objectMapper)
    {
        setWebClient(WebServiceUtil.getWebClientBuilder(objectMapper)
            .baseUrl("https://api.live.bilibili.com")
            .build());
    }

    public Flux<BilibiliStream> getStreams(long parentAreaId, long areaId)
    {
        return getStreams(parentAreaId, areaId, 1);
    }

    private Flux<BilibiliStream> getStreams(long parentAreaId, long areaId, int page)
    {
        return getWebClient()
            .get()
            .uri
            (
                b->b.path("/xlive/web-interface/v1/second/getList")
                    .queryParam("platform", "web")
                    .queryParam("sort_type", "live_time")
                    .queryParam("parent_area_id", String.valueOf(parentAreaId))
                    .queryParam("area_id", String.valueOf(areaId))
                    .queryParam("page", String.valueOf(page))
                    .build()
            )
            .accept(APPLICATION_JSON)
            .retrieve()
            .bodyToMono(BilibiliStreamSearch.class)
            .map(BilibiliStreamSearch::getData)
            .flatMapMany(data->getStreamsRecursively(parentAreaId, areaId, page, data))
            .map(BilibiliAPI::fix)
            .sort(COMPARATOR)
            .retryWhen(rateLimiter.retryWhen(getRetry(WebServiceUtil.RETRY)))
            .delaySubscription(Mono.defer(rateLimiter::requestSlot));
    }

    private Flux<BilibiliStream> getStreamsRecursively
    (
        long parentAreaId,
        long areaId,
        int page,
        BilibiliStreamSearchData data
    )
    {
        return data.getHasMore() != null && data.getHasMore() > 0
            ? Flux.concat(
                Flux.fromIterable(data.getStreams()),
                getStreams(parentAreaId, areaId, page + 1))
            : Flux.fromIterable(data.getStreams());
    }

    private static BilibiliStream fix(BilibiliStream stream)
    {
        if(stream.getUserCover() != null)
            stream.setUserCover(fixUrlScheme(stream.getUserCover()));
        if(stream.getSystemCover() != null)
            stream.setSystemCover(fixUrlScheme(stream.getSystemCover()));
        if(stream.getFace() != null)
            stream.setFace(fixUrlScheme(stream.getFace()));
        return stream;
    }

    private static String fixUrlScheme(String url)
    {
        return url.replaceFirst("http://", "https://");
    }

}
