// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.Account;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ClanDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterLinkDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.web.service.external.ExternalCharacterSearch;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
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
    public static final String URL_MARKER = "://";
    public static final String STARCRAFT2_COM_PROFILE_URL_PREFIX = "https://starcraft2.com/profile";

    private final PlayerCharacterDAO playerCharacterDAO;
    private final AccountDAO accountDAO;
    private final ClanDAO clanDAO;
    private final LadderCharacterDAO ladderCharacterDAO;
    private final PlayerCharacterLinkDAO playerCharacterLinkDAO;
    private final Map<SocialMedia, ExternalCharacterSearch> externalSearch;

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
        PlayerCharacterLinkDAO playerCharacterLinkDAO,
        List<ExternalCharacterSearch> externalSearches
    )
    {
        this.playerCharacterDAO = playerCharacterDAO;
        this.accountDAO = accountDAO;
        this.clanDAO = clanDAO;
        this.ladderCharacterDAO = ladderCharacterDAO;
        this.playerCharacterLinkDAO = playerCharacterLinkDAO;
        externalSearch = externalSearches.stream()
            .collect(Collectors.toMap
            (
                ExternalCharacterSearch::getSupportedSocialMedia,
                Function.identity(),
                (l, r)->{throw new IllegalStateException("Unexpected merge");},
                ()->new EnumMap<>(SocialMedia.class)
            ));
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
        if(term.contains(URL_MARKER)) return findDistinctCharactersByUrl(term);
        return ladderCharacterDAO.findDistinctCharacters(term);
    }

    private List<LadderDistinctCharacter> findDistinctCharactersByUrl(String term)
    {
        if(term.startsWith(STARCRAFT2_COM_PROFILE_URL_PREFIX))
            return ladderCharacterDAO.findDistinctCharacterByProfileLink(term)
                .map(List::of)
                .orElse(List.of());

        SocialMedia type = SocialMedia.fromBaseUserUrlPrefix(term);
        if(!externalSearch.containsKey(type)) return List.of();

        String relativeUrl = PlayerCharacterLink.getRelativeUrl(term).orElse(null);
        if(relativeUrl == null) return List.of();

        List<LadderDistinctCharacter> foundCharacters = playerCharacterLinkDAO.find(type, relativeUrl)
            .stream()
            .map(link->ladderCharacterDAO.findDistinctCharacterByCharacterId(link.getPlayerCharacterId()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
        if(!foundCharacters.isEmpty()) return foundCharacters;

        return WebServiceUtil.getOnErrorLogAndSkipMono(externalSearch.get(type).find(term))
            .map(id->ladderCharacterDAO.findDistinctCharacterByProfileLink(id.generateProfileSuffix()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .doOnNext
            (
                c->playerCharacterLinkDAO.merge(new PlayerCharacterLink
                (
                    c.getMembers().getCharacter().getId(),
                    type,
                    relativeUrl
                ))
            )
            .flux()
            .collectList()
            .block();
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
