// Copyright (C) 2020-2024 Oleksandr Masniuk
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
        return cacheManager->{
            cacheManager.setAsyncCacheMode(true);
            for(CaffeineCache cache : caches)
            {
                try
                {
                    cacheManager.registerCustomCache(cache.getName(), cache.getAsyncCache());
                }
                catch (IllegalStateException ex)
                {
                    if(!ex.getMessage().startsWith("No Caffeine AsyncCache available")) throw ex;

                    cacheManager.registerCustomCache(cache.getName(), cache.getNativeCache());
                }
            }
        };
    }

    /*TODO
        Blocking in caffeine loader because Spring cache caches a wrong object when
        caffeine refreshes expired entries. Blocking calls should be removed when Spring cache
        supports it.
     */
    @Bean
    public CaffeineCache communityVideoStream(CommunityService communityService)
    {
        return new CaffeineCache
        (
            "community-video-stream",
            Caffeine.newBuilder()
                .refreshAfterWrite(CommunityService.STREAM_CACHE_REFRESH_AFTER)
                .expireAfterWrite(CommunityService.STREAM_CACHE_EXPIRE_AFTER)
                .buildAsync(b->communityService.getStreamsNoCache().block()),
            false
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
                .expireAfterWrite(CommunityService.FEATURED_STREAM_CACHE_EXPIRE_AFTER)
                .buildAsync(b->communityService.getFeaturedStreamsNoCache().block()),
            false
        );
    }

}
