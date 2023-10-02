// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.nephest.battlenet.sc2.web.service.community.CommunityService;
import java.util.List;
import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig
{

    @Bean
    public CacheManagerCustomizer<CaffeineCacheManager> cacheManagerCustomizer(List<CaffeineCache> caches) {
        return cacheManager->caches.forEach(cache->
            cacheManager.registerCustomCache(cache.getName(), cache.getNativeCache()));
    }

    @Bean
    public CaffeineCache communityVideoStream(CommunityService communityService)
    {
        return new CaffeineCache
        (
            "community-video-stream",
            Caffeine.newBuilder()
                .refreshAfterWrite(CommunityService.STREAM_CACHE_REFRESH_AFTER)
                .expireAfterAccess(CommunityService.STREAM_CACHE_EXPIRE_AFTER)
                .build(b->communityService.getStreamsNoCache())
        );
    }

    @Bean
    public CaffeineCache communityVideoStreamFeatured(CommunityService communityService)
    {
        return new CaffeineCache
        (
            "community-video-stream-featured",
            Caffeine.newBuilder()
                .refreshAfterWrite(CommunityService.STREAM_CACHE_REFRESH_AFTER)
                .expireAfterAccess(CommunityService.FEATURED_STREAM_CACHE_EXPIRE_AFTER)
                .build(b->communityService.getFeaturedStreamsNoCache())
        );
    }

}
