// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.CursorNavigation;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SortingOrder;
import com.nephest.battlenet.sc2.model.local.Clan;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.ladder.PagedSearchResult;
import com.nephest.battlenet.sc2.model.validation.CursorNavigableResult;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Hidden
@RestController
@RequestMapping("/api/clan")
public class ClanControllerLegacy
{

    public static final int MIN_ADDITIONAL_CURSOR_FILTER
        = ClanController.MIN_ADDITIONAL_CURSOR_FILTER;
    public static final int MAX_ADDITIONAL_CURSOR_FILTER
        = ClanController.MAX_ADDITIONAL_CURSOR_FILTER;
    private static final String MIN_ADDITIONAL_CURSOR_FILTER_STR
        = ClanController.MIN_ADDITIONAL_CURSOR_FILTER_STR;
    private static final String MAX_ADDITIONAL_CURSOR_FILTER_STR
        = ClanController.MAX_ADDITIONAL_CURSOR_FILTER_STR;
    public static final int ID_LIST_SIZE_MAX = 400;

    @Autowired
    private ClanDAO clanDAO;

    @Hidden
    @GetMapping("/cursor/{cursor}/{cursorValue}/{idCursor}/{page}/{pageDiff}")
    public PagedSearchResult<List<Clan>> findByCursorLegacy
    (
        @PathVariable("cursor") ClanDAO.Cursor cursor,
        @PathVariable("cursorValue") double cursorValue,
        @PathVariable("idCursor") int idCursor,
        @PathVariable("page") int page,
        @PathVariable("pageDiff") int pageDiff,
        @RequestParam(name="minActiveMembers", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER_STR) int minActiveMembers,
        @RequestParam(name="maxActiveMembers", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER_STR) int maxActiveMembers,
        @RequestParam(name="minGamesPerActiveMemberPerDay", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER_STR) double minGamesPerActiveMemberPerDay,
        @RequestParam(name="maxGamesPerActiveMemberPerDay", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER_STR) double maxGamesPerActiveMemberPerDay,
        @RequestParam(name="minAvgRating", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER_STR) int minAvgRating,
        @RequestParam(name="maxAvgRating", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER_STR) int maxAvgRating,
        @RequestParam(name="region", required = false) Region region
    )
    {
        return clanDAO.findByCursor
        (
            cursor, cursorValue, idCursor,
            minActiveMembers, maxActiveMembers,
            minGamesPerActiveMemberPerDay, maxGamesPerActiveMemberPerDay,
            minAvgRating, maxAvgRating,
            region,
            page, pageDiff
        );
    }

    @GetMapping("/cursor")
    public CursorNavigableResult<List<Clan>> findByCursor
    (
        @RequestParam("cursor") ClanDAO.Cursor cursor,
        @RequestParam(value = "cursorValue", required = false) Double cursorValue,
        @RequestParam(value = "idCursor", required = false) Integer idCursor,
        @RequestParam(value = "sortingOrder", defaultValue = "DESC") SortingOrder sortingOrder,
        @RequestParam(name="minActiveMembers", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER_STR) int minActiveMembers,
        @RequestParam(name="maxActiveMembers", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER_STR) int maxActiveMembers,
        @RequestParam(name="minGamesPerActiveMemberPerDay", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER_STR) double minGamesPerActiveMemberPerDay,
        @RequestParam(name="maxGamesPerActiveMemberPerDay", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER_STR) double maxGamesPerActiveMemberPerDay,
        @RequestParam(name="minAvgRating", defaultValue = MIN_ADDITIONAL_CURSOR_FILTER_STR) int minAvgRating,
        @RequestParam(name="maxAvgRating", defaultValue = MAX_ADDITIONAL_CURSOR_FILTER_STR) int maxAvgRating,
        @RequestParam(name="region", required = false) Region region
    )
    {
        if(cursorValue == null) cursorValue = sortingOrder == SortingOrder.DESC
            ? (double) MAX_ADDITIONAL_CURSOR_FILTER
            : (double) MIN_ADDITIONAL_CURSOR_FILTER;
        if(idCursor == null) idCursor = sortingOrder == SortingOrder.DESC
            ? Integer.MAX_VALUE
            : Integer.MIN_VALUE;
        return new CursorNavigableResult<>(clanDAO.findByCursor
        (
            cursor, cursorValue, idCursor,
            minActiveMembers, maxActiveMembers,
            minGamesPerActiveMemberPerDay, maxGamesPerActiveMemberPerDay,
            minAvgRating, maxAvgRating,
            region,
            2, sortingOrder == SortingOrder.DESC ? 1 : -1
        ).getResult(), new CursorNavigation(null, null));
    }

    @Hidden
    @GetMapping("/tag/{tag}")
    public List<Clan> findByTagLegacy(@PathVariable("tag") String tag)
    {
        return clanDAO.findByTag(tag);
    }

    @Hidden
    @GetMapping("/tag")
    public List<Clan> findByTagLegacy2(@RequestParam("term") String tag)
    {
        return clanDAO.findByTag(tag);
    }

    @Hidden
    @GetMapping("/tag-or-name/{term}")
    public List<Clan> findByTagOrNameLegacy(@PathVariable("term") String term)
    {
        return clanDAO.findByTagOrName(term);
    }

    @GetMapping("/tag-or-name")
    public List<Clan> findByTagOrName(@RequestParam("term") String term)
    {
        return clanDAO.findByTagOrName(term);
    }

    @GetMapping("/id/{ids}")
    public List<Clan> findByIds(@PathVariable("ids") Set<Integer> ids)
    {
        if(ids.size() > ID_LIST_SIZE_MAX)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                String.format("Ids list is too big, %1$s allowed", ID_LIST_SIZE_MAX));
        return clanDAO.findByIds(ids);
    }


}
