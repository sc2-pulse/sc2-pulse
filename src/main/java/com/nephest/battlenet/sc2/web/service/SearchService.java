// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
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
    public static final Pattern GAME_LINK_PATTERN =
        Pattern.compile("^battlenet:+//starcraft/profile/.*");

    private final PlayerCharacterDAO playerCharacterDAO;
    private final AccountDAO accountDAO;
    private final ClanDAO clanDAO;
    private final LadderCharacterDAO ladderCharacterDAO;
    private final SC2ArcadeAPI arcadeAPI;
    private final ConversionService conversionService;

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
    public SearchService
    (
        PlayerCharacterDAO playerCharacterDAO,
        AccountDAO accountDAO,
        ClanDAO clanDAO,
        LadderCharacterDAO ladderCharacterDAO,
        SC2ArcadeAPI arcadeAPI,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.playerCharacterDAO = playerCharacterDAO;
        this.accountDAO = accountDAO;
        this.clanDAO = clanDAO;
        this.ladderCharacterDAO = ladderCharacterDAO;
        this.arcadeAPI = arcadeAPI;
        this.conversionService = conversionService;
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

    public List<LadderDistinctCharacter> findDistinctCharacters(String term)
    {
        if(GAME_LINK_PATTERN.matcher(term).matches())
        {
            String[] split = term.split("/");
            if(split.length < 2) throw new IllegalArgumentException("Invalid profile link");

            Region region = conversionService.convert(Integer.parseInt(split[split.length - 2]), Region.class);
            long gameId = Long.reverseBytes(Long.parseUnsignedLong(split[split.length - 1]));
            BlizzardFullPlayerCharacter character =
                WebServiceUtil.getOnErrorLogAndSkipMono(arcadeAPI.findByRegionAndGameId(region, gameId))
                    .block();

            return character != null
                ? ladderCharacterDAO.findDistinctCharacters(character.generateProfileSuffix())
                : List.of();
        }
        else
        {
            return ladderCharacterDAO.findDistinctCharacters(term);
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
