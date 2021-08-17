// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class VarService
{

    @Autowired
    private VarDAO varDAO;

    @Cacheable(cacheNames = "var-strings")
    public String getMessage(String key)
    {
        return varDAO.find(key).orElse(null);
    }

    @CacheEvict(allEntries = true, cacheNames = "var-strings")
    public void evictCache(){}

}
