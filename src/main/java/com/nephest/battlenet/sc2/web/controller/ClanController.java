// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/clan")
public class ClanController
{

    public static final String MIN_ADDITIONAL_CURSOR_FILTER = "1";
    public static final String MAX_ADDITIONAL_CURSOR_FILTER = "9999";

    @Autowired
    private ClanDAO clanDAO;

    @GetMapping("/cursor/{cursor}/{cursorValue}/{idCursor}/{page}/{pageDiff}")
    public PagedSearchResult<List<Clan>> findByCursor
    (
        @PathVariable("cursor") ClanDAO.Cursor cursor,
        @PathVariable("cursorValue") int cursorValue,
        @PathVariable("idCursor") int idCursor,
        @PathVariable("page") int page,
        @PathVariable("pageDiff") int pageDiff,
        @RequestParam(name="minActiveMembers", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER) int minActiveMembers,
        @RequestParam(name="maxActiveMembers", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER) int maxActiveMembers,
        @RequestParam(name="minGamesPerActiveMember", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER) int minGamesPerActiveMember,
        @RequestParam(name="maxGamesPerActiveMember", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER) int maxGamesPerActiveMember,
        @RequestParam(name="minAvgRating", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER) int minAvgRating,
        @RequestParam(name="maxAvgRating", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER) int maxAvgRating,
        @RequestParam(name="region", required = false) Region region
    )
    {
        return clanDAO.findByCursor
        (
            cursor, cursorValue, idCursor,
            minActiveMembers, maxActiveMembers,
            minGamesPerActiveMember, maxGamesPerActiveMember,
            minAvgRating, maxAvgRating,
            region,
            page, pageDiff
        );
    }

    @GetMapping("/tag/{tag}")
    public List<Clan> findByTag(@PathVariable("tag") String tag)
    {
        return clanDAO.findByTag(tag);
    }

    @GetMapping("/tag-or-name/{term}")
    public List<Clan> findByTagOrName(@PathVariable("term") String term)
    {
        return clanDAO.findByTagOrName(term);
    }

    @GetMapping("/id/{ids}")
    public List<Clan> findByIds(@PathVariable("ids") Integer[] ids)
    {
        return clanDAO.findByIds(ids);
    }


}
