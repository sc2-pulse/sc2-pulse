// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterLinkDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderCharacterDAO;
import com.nephest.battlenet.sc2.model.util.PostgreSQLUtils;
import com.nephest.battlenet.sc2.model.validation.NotFakeSc2Name;
import com.nephest.battlenet.sc2.web.service.external.ExternalCharacterSearch;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

@Validated
@Service
public class SearchService
{

    public static final int MIN_CLAN_TAG_LENGTH = 2;
    public static final String CLAN_START_DELIMITER = "[";
    public static final String CLAN_END_DELIMITER = "]";
    public static final String BATTLE_TAG_MARKER = "#";
    public static final String URL_MARKER = "://";
    public static final Pattern STARCRAFT2_COM_PROFILE_URL_PATTERN = Pattern.compile
    (
        "^https://starcraft2(\\.blizzard)?.com"
        + "(/\\p{L}\\p{L}-\\p{L}\\p{L})?"
        + "/profile/[0-9]/[0-9]/[0-9]+$"
    );
    public static final int ID_SEARCH_MAX_SEASONS = 10;

    private final PlayerCharacterDAO playerCharacterDAO;
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
        LadderCharacterDAO ladderCharacterDAO,
        PlayerCharacterLinkDAO playerCharacterLinkDAO,
        List<ExternalCharacterSearch> externalSearches
    )
    {
        this.playerCharacterDAO = playerCharacterDAO;
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

    public List<Long> findIds
    (
        @Valid @NotBlank @NotFakeSc2Name String name,
        boolean caseSensitive,
        Region region,
        @Valid @Size(max = ID_SEARCH_MAX_SEASONS) Set<Integer> seasons,
        Set<QueueType> queues
    )
    {
        if(seasons.size() > 1 && queues.size() > 1)
            throw new IllegalArgumentException("Unsupported season and queue combination");

        String likeName = PostgreSQLUtils.escapeLikePattern(name) + "#%";
        return playerCharacterDAO.findIds(likeName, caseSensitive, region, seasons, queues);
    }

    public List<LadderDistinctCharacter> findDistinctCharacters(String term)
    {
        if(term.contains(URL_MARKER)) return findDistinctCharactersByUrl(term);
        return ladderCharacterDAO.findDistinctCharacters(term);
    }

    private List<LadderDistinctCharacter> findDistinctCharactersByUrl(String term)
    {
        if(STARCRAFT2_COM_PROFILE_URL_PATTERN.matcher(term).matches())
            return ladderCharacterDAO.findDistinctCharacterByProfileLink(term)
                .map(List::of)
                .orElse(List.of());

        SocialMedia type = SocialMedia.fromLaxUserUrl(term);
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
                c->playerCharacterLinkDAO.merge(Set.of(new PlayerCharacterLink
                (
                    c.getMembers().getCharacter().getId(),
                    type,
                    relativeUrl
                )))
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

}
