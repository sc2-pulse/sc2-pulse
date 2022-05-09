// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SearchService
{

    public static final int MIN_CHARACTER_NAME_LENGTH = 4;
    public static final int MIN_CLAN_TAG_LENGTH = 2;
    public static final String CLAN_START_DELIMITER = "[";
    public static final String CLAN_END_DELIMITER = "]";
    public static final String CLAN_SEARCH_FORMAT = CLAN_START_DELIMITER + "%1$s" + CLAN_END_DELIMITER;
    public static final String BATTLE_TAG_MARKER = "#";

    private final PlayerCharacterDAO playerCharacterDAO;
    private final AccountDAO accountDAO;
    private final ClanDAO clanDAO;

    public enum SearchType
    {
        GENERAL,
        BATTLE_TAG,
        CLAN;

        public static SearchType of(String term)
        {
            if(term.startsWith(CLAN_START_DELIMITER))
            {
                return CLAN;
            }
            else if (term.contains(BATTLE_TAG_MARKER))
            {
                return BATTLE_TAG;
            }
            return GENERAL;
        }

    }

    @Autowired
    public SearchService(PlayerCharacterDAO playerCharacterDAO, AccountDAO accountDAO, ClanDAO clanDAO)
    {
        this.playerCharacterDAO = playerCharacterDAO;
        this.accountDAO = accountDAO;
        this.clanDAO = clanDAO;
    }

    public List<String> suggest(String term, int limit)
    {
        switch(SearchType.of(term))
        {
            case BATTLE_TAG:
                return accountDAO.findBattleTags(term, limit);
            case CLAN:
                return clanTagsToSearchTerms(clanDAO.findTags(extractClanTag(term), limit));
            default:
                return playerCharacterDAO.findNamesWithoutDiscriminator(term, limit);
        }
    }

    public List<String> suggestIfQuick(String term, int limit)
    {
        return isQuickSearch(term) ? suggest(term, limit) : List.of();
    }

    public boolean isQuickSearch(String term)
    {
        if(Account.isFakeBattleTag(term)) return false;

        switch(SearchType.of(term))
        {
            case CLAN:
                return term.length() >= MIN_CLAN_TAG_LENGTH;
            case BATTLE_TAG:
                return true;
            default:
                return term.length() >= MIN_CHARACTER_NAME_LENGTH;
        }
    }

    public static String extractClanTag(String term)
    {
        if(term == null) return null;

        return term.length() >= MIN_CLAN_TAG_LENGTH
            ? term.endsWith(CLAN_END_DELIMITER) ? term.substring(1, term.length() - 1) : term.substring(1)
            : "";
    }

    public static List<String> clanTagsToSearchTerms(List<String> tags)
    {
        return tags.stream()
            .map(tag->String.format(CLAN_SEARCH_FORMAT, tag))
            .collect(Collectors.toList());
    }

}
