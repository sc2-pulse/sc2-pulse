// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig
{

    @Value("${cache.ttl:#{'P7D'}}")
    private Duration CACHE_TTL;

    @Bean
    public Caffeine<Object, Object> caffeineConfig()
    {
        return Caffeine.newBuilder().expireAfterAccess(CACHE_TTL);
    }

    @Bean
    public CacheManager cacheManager(Caffeine<Object, Object> caffeine)
    {
        CaffeineCacheManager caffeineCacheManager = new CaffeineCacheManager();
        caffeineCacheManager.setCaffeine(caffeine);
        return caffeineCacheManager;
    }

}
