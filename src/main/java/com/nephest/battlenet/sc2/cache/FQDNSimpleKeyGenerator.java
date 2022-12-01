// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.cache;


import java.lang.reflect.Method;
import org.jetbrains.annotations.NotNull;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.cache.interceptor.SimpleKeyGenerator;
import org.springframework.stereotype.Component;

/**
 * This key uses object and method in addition to params to generate a key. It is useful if all you
 * need is a cacheable method, and you have a lot of different caches tied to a single point of
 * cache eviction. This generator allows you to use a single cache name while removing any cache
 * collisions due to equal method params. The downside is that you can't use it as a normal cache,
 * i.e. you can't add new cache entries from another class/method.
 */
@Component("fqdnSimpleKeyGenerator")
public class FQDNSimpleKeyGenerator
implements KeyGenerator
{

    @Override @NotNull
    public Object generate
    (
        @NotNull Object target,
        @NotNull Method method,
        Object @NotNull ... params
    )
    {
        Object[] fqdn = new Object[params.length + 2];
        fqdn[0] = target;
        fqdn[1] = method;
        System.arraycopy(params, 0, fqdn, 2, params.length);
        return SimpleKeyGenerator.generateKey(fqdn);
    }

}
