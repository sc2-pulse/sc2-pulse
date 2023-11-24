// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Patch;
import com.nephest.battlenet.sc2.model.local.dao.PatchDAO;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SC2MetaService
{

    private static final Logger LOG = LoggerFactory.getLogger(SC2MetaService.class);

    public static final OffsetDateTime PATCH_START = OffsetDateTime
        .of(2009, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    public static final int PATCH_BATCH_SIZE = 2;

    private final PatchDAO patchDAO;
    private final BlizzardSC2API blizzardSC2API;

    @Autowired @Lazy
    private SC2MetaService sc2MetaService;

    @Autowired
    public SC2MetaService(PatchDAO patchDAO, BlizzardSC2API blizzardSC2API)
    {
        this.patchDAO = patchDAO;
        this.blizzardSC2API = blizzardSC2API;
    }

    protected void setSc2MetaService(SC2MetaService sc2MetaService)
    {
        this.sc2MetaService = sc2MetaService;
    }

    @Cacheable(cacheNames = "meta-patch")
    public List<Patch> getPatches()
    {
        return patchDAO.findByPublishedMin(PATCH_START);
    }

    public List<Patch> getPatches(OffsetDateTime publishedMin)
    {
        List<Patch> existingPatches = sc2MetaService.getPatches();
        if(existingPatches.isEmpty()) return List.of();

        if(existingPatches.get(existingPatches.size() - 1).getPublished().isAfter(publishedMin))
            return existingPatches;

        int ix = 0;
        for(Patch patch : existingPatches)
        {
            if(patch.getPublished().isBefore(publishedMin)) break;
            ix++;
        }
        return existingPatches.subList(0, ix);
    }

    @Scheduled(cron="0 15 5 * * *")
    @CacheEvict(cacheNames = "meta-patch", allEntries = true)
    public int updatePatches()
    {
        int count = 0;
        long minId = patchDAO.findByPublishedMin(PATCH_START).stream()
            .mapToLong(Patch::getBuild)
            .max()
            .orElse(-1)
            + 1;
        while(true)
        {
            Set<Patch> patches = updatePatches(minId);
            if(patches.isEmpty()) break;

            count += patches.size();
            minId = patches.stream()
                .mapToLong(Patch::getBuild)
                .max()
                .orElseThrow()
                + 1;
        }

        if(count > 0) LOG.info("Updated {} patches", count);
        return count;
    }

    private Set<Patch> updatePatches(long minId)
    {
        Set<Patch> patches = blizzardSC2API.getPatches(Region.US, minId, null, PATCH_BATCH_SIZE)
            .collect(Collectors.toSet())
            .block();
        patchDAO.merge(patches);
        return patches;
    }

}
