// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPatch;
import com.nephest.battlenet.sc2.model.local.Patch;
import com.nephest.battlenet.sc2.model.local.PatchRelease;
import com.nephest.battlenet.sc2.model.local.dao.PatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.PatchReleaseDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPatch;
import com.nephest.battlenet.sc2.web.service.liquipedia.LiquipediaAPI;
import com.nephest.battlenet.sc2.web.service.liquipedia.LiquipediaParser;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

@Service
public class SC2MetaService
{

    private static final Logger LOG = LoggerFactory.getLogger(SC2MetaService.class);

    public static final long PATCH_START = 16195L;
    public static final int PATCH_BATCH_SIZE = 2;

    private final PatchDAO patchDAO;
    private final PatchReleaseDAO patchReleaseDAO;
    private final LiquipediaAPI liquipediaAPI;

    @Autowired @Lazy
    private SC2MetaService sc2MetaService;

    @Autowired
    public SC2MetaService
    (
        PatchDAO patchDAO,
        PatchReleaseDAO patchReleaseDAO,
        LiquipediaAPI liquipediaAPI
    )
    {
        this.patchDAO = patchDAO;
        this.patchReleaseDAO = patchReleaseDAO;
        this.liquipediaAPI = liquipediaAPI;
    }

    protected void setSc2MetaService(SC2MetaService sc2MetaService)
    {
        this.sc2MetaService = sc2MetaService;
    }

    @Cacheable(cacheNames = "meta-patch")
    public List<LadderPatch> getPatches()
    {
        List<Patch> patches =  patchDAO.findByBuildMin(PATCH_START);
        Set<Integer> ids = patches.stream()
            .map(Patch::getId)
            .collect(Collectors.toSet());
        List<PatchRelease> releases = patchReleaseDAO.findByPatchIds(ids);
        return merge(patches, releases);
    }

    public List<LadderPatch> getPatches(Long buildMin)
    {
        List<LadderPatch> existingPatches = sc2MetaService.getPatches();
        if(existingPatches.isEmpty()) return List.of();

        if(existingPatches.get(existingPatches.size() - 1).getPatch().getBuild() > buildMin)
            return existingPatches;

        int ix = 0;
        for(LadderPatch patch : existingPatches)
        {
            if(patch.getPatch().getBuild() < buildMin) break;
            ix++;
        }
        return existingPatches.subList(0, ix);
    }

    @Scheduled(cron="0 15 5 * * *")
    @CacheEvict(cacheNames = "meta-patch", allEntries = true)
    public List<LadderPatch> updatePatches()
    {
        long minId = patchDAO.findByBuildMin(PATCH_START).stream()
            .mapToLong(Patch::getBuild)
            .max()
            .orElse(0);
        Set<LiquipediaPatch> patches = pullPatches(minId)
            .collect(Collectors.toSet())
            .block();
        List<LadderPatch> savedPatches = sc2MetaService.savePatches(patches);
        if(!savedPatches.isEmpty()) LOG.info("Updated {} patches", savedPatches.size());

        return savedPatches;
    }

    @Transactional
    public List<LadderPatch> savePatches(Collection<? extends LiquipediaPatch> liquipediaPatches)
    {
        Map<LiquipediaPatch, Patch> patchMap = liquipediaPatches.stream()
            .collect(Collectors.toMap(Function.identity(), SC2MetaService::convert));
        Set<Patch> patches = patchDAO.merge(Set.copyOf(patchMap.values()));

        Set<PatchRelease> releases = liquipediaPatches.stream()
            .map(lpPatch->SC2MetaService.convertReleases(lpPatch, patchMap.get(lpPatch).getId()))
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
        patchReleaseDAO.merge(releases);
        return merge(patches, releases);
    }

    private Flux<LiquipediaPatch> pullPatches(Long minId)
    {
        return liquipediaAPI.parsePatches()
            .filter(patch->patch.getBuild() >= minId)
            .collectList()
            .flatMapMany(liquipediaAPI::parsePatches);
    }

    public static Patch convert(LiquipediaPatch liquipediaPatch)
    {
        return new Patch
        (
            liquipediaPatch.getBuild(),
            liquipediaPatch.getVersion(),
            liquipediaPatch.isVersus()
        );
    }

    public static Set<PatchRelease> convertReleases(LiquipediaPatch liquipediaPatch, Integer patchId)
    {
        return liquipediaPatch.getReleases().entrySet().stream()
            .map(entry->new PatchRelease(
                patchId,
                entry.getKey(),
                LiquipediaParser.convert(entry.getValue(), entry.getKey())
            ))
            .collect(Collectors.toSet());
    }

    public static List<LadderPatch> merge
    (
        Collection<? extends Patch> patches,
        Collection<? extends PatchRelease> releases
    )
    {
        Map<Integer, List<PatchRelease>> groupedReleases = releases.stream()
            .collect(Collectors.groupingBy(PatchRelease::getPatchId));
        return patches.stream()
            .map(patch->new LadderPatch(
                patch,
                Optional.ofNullable(groupedReleases.get(patch.getId()))
                    .map(SC2MetaService::convertReleases)
                    .orElse(Map.of())
            ))
            .collect(Collectors.toList());
    }

    public static Map<Region, OffsetDateTime> convertReleases
    (
        Collection<? extends PatchRelease> releases
    )
    {
        return releases.stream().collect(Collectors.toMap(
            PatchRelease::getRegion,
            PatchRelease::getReleased,
            (l, r)->l,
            ()->new EnumMap<>(Region.class)
        ));
    }

    public static LadderPatch convertToLadderPatch(LiquipediaPatch liquipediaPatch)
    {
        return new LadderPatch
        (
            convert(liquipediaPatch),
            convertReleases(convertReleases(liquipediaPatch, null))
        );
    }

}
