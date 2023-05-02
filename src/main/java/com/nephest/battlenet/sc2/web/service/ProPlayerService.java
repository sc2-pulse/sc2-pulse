// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayer;
import com.nephest.battlenet.sc2.model.aligulac.AligulacProPlayerRoot;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPlayer;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.ProTeam;
import com.nephest.battlenet.sc2.model.local.ProTeamMember;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerAccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProPlayerDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProTeamDAO;
import com.nephest.battlenet.sc2.model.local.dao.ProTeamMemberDAO;
import com.nephest.battlenet.sc2.model.local.dao.SocialMediaLinkDAO;
import com.nephest.battlenet.sc2.model.revealed.RevealedProPlayer;
import com.nephest.battlenet.sc2.web.service.liquipedia.LiquipediaAPI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ProPlayerService
{

    private static final Logger LOG = LoggerFactory.getLogger(ProPlayerService.class);

    public static final Pattern ALIGULAC_PROFILE_PATTERN =
        Pattern.compile("^https?://aligulac.com/players/(\\d+)(-.*)?/?$");
    public static final String ALIGULAC_PROFILE_PREFIX = "http://aligulac.com/players/";
    public static final String LIQUIPEDIA_PROFILE_PREFIX = "https://liquipedia.net/starcraft2/";
    public static final Set<SocialMedia> SUPPORTED_SOCIAL_MEDIA = EnumSet.of
    (
        SocialMedia.TWITCH,
        SocialMedia.YOUTUBE,
        SocialMedia.TWITTER,
        SocialMedia.DISCORD,
        SocialMedia.INSTAGRAM
    );

    private final ProPlayerDAO proPlayerDAO;
    private final ProTeamDAO proTeamDAO;
    private final ProTeamMemberDAO proTeamMemberDAO;
    private final SocialMediaLinkDAO socialMediaLinkDAO;
    private final ProPlayerAccountDAO proPlayerAccountDAO;

    private final SC2RevealedAPI sc2RevealedAPI;

    private AligulacAPI aligulacAPI;
    private final LiquipediaAPI liquipediaAPI;

    private int aligulacBatchSize = 100;
    private final int linkWebBatchSize = 10;
    private final int linkDbBatchSize = 100;

    @Autowired @Lazy
    private ProPlayerService proPlayerService;

    @Autowired
    public ProPlayerService
    (
        ProPlayerDAO proPlayerDAO,
        ProTeamDAO proTeamDAO,
        ProTeamMemberDAO proTeamMemberDAO,
        SocialMediaLinkDAO socialMediaLinkDAO,
        ProPlayerAccountDAO proPlayerAccountDAO,
        SC2RevealedAPI sc2RevealedAPI,
        AligulacAPI aligulacAPI,
        LiquipediaAPI liquipediaAPI
    )
    {
        this.proPlayerDAO = proPlayerDAO;
        this.proTeamDAO = proTeamDAO;
        this.proTeamMemberDAO = proTeamMemberDAO;
        this.socialMediaLinkDAO = socialMediaLinkDAO;
        this.proPlayerAccountDAO = proPlayerAccountDAO;
        this.sc2RevealedAPI = sc2RevealedAPI;
        this.aligulacAPI = aligulacAPI;
        this.liquipediaAPI = liquipediaAPI;
    }

    protected ProPlayerService getProPlayerService()
    {
        return proPlayerService;
    }

    protected void setProPlayerService(ProPlayerService proPlayerService)
    {
        this.proPlayerService = proPlayerService;
    }

    protected AligulacAPI getAligulacAPI()
    {
        return aligulacAPI;
    }

    protected void setAligulacAPI(AligulacAPI aligulacAPI)
    {
        this.aligulacAPI = aligulacAPI;
    }

    public void update()
    {
        updateAligulac();
        updateSocialMediaLinks();
        LOG.info("Updated pro players");
    }

    @Deprecated
    protected void updateRevealed()
    {
        for(RevealedProPlayer revealedProPlayer : sc2RevealedAPI.getPlayers().block().getPlayers())
        {
            //save only identified players
            if(revealedProPlayer.getFirstName() == null || revealedProPlayer.getFirstName().isEmpty()) continue;

            ProPlayer proPlayer = ProPlayer.of(revealedProPlayer);
            SocialMediaLink[] links = SocialMediaLink.of(proPlayer, revealedProPlayer);

            proPlayerDAO.merge(proPlayer);
            for(SocialMediaLink link : links) link.setProPlayerId(proPlayer.getId());
            socialMediaLinkDAO.merge(true, links);
            proPlayerAccountDAO.link(proPlayer.getId(), revealedProPlayer.getBnetTags());
        }
    }

    private void updateAligulac()
    {
        int ix = 0;
        int total = 0;
        List<ProPlayer> originalProPlayers = proPlayerDAO.findAligulacList();
        long[] ids = new long[getAligulacBatchSize()];
        ProPlayer[] proPlayers = new ProPlayer[getAligulacBatchSize()];

        for(ProPlayer proPlayer : originalProPlayers)
        {
            ids[ix] = proPlayer.getAligulacId();
            proPlayers[ix] = proPlayer;
            ix++; total++;
            if(ix == getAligulacBatchSize() || total == originalProPlayers.size())
            {
                AligulacProPlayer[] aligulacProPlayers = aligulacAPI.getPlayers(Arrays.copyOf(ids, ix)).block().getObjects();
                proPlayerService.updateAligulac(proPlayers, aligulacProPlayers, ix);
                ix = 0;
            }
        }
    }

    @CacheEvict(cacheNames={"pro-player-characters"}, allEntries=true)
    @Retryable(backoff = @Backoff(100L))
    @Transactional
    (
        propagation = Propagation.REQUIRES_NEW
    )
    public void updateAligulac
    (
        ProPlayer[] proPlayers,
        AligulacProPlayer[] aligulacProPlayers,
        int ix
    )
    {
        ArrayList<ProTeamMember> members = new ArrayList<>();
        ArrayList<Long> notMembers = new ArrayList<>();
        ArrayList<SocialMediaLink> links = new ArrayList<>(aligulacProPlayers.length * 2);
        //aligulac returns players in the same order they were requested
        for(int i = 0; i < aligulacProPlayers.length; i++)
        {
            ProPlayer.update(proPlayers[i], aligulacProPlayers[i]);
            ProTeam proTeam = ProTeam.of(aligulacProPlayers[i]);
            if(proTeam != null)
            {
                ProTeamMember member = new ProTeamMember
                (
                    proTeamDAO.merge(proTeam).getId(),
                    proPlayers[i].getId()
                );
                members.add(member);
            }
            else
            {
                notMembers.add(proPlayers[i].getId());
            }
            links.addAll(extractLinks(proPlayers[i], aligulacProPlayers[i]));
        }
        proPlayerDAO.mergeWithoutIds(Arrays.copyOf(proPlayers, ix));
        proTeamMemberDAO.merge(members.toArray(new ProTeamMember[0]));
        proTeamMemberDAO.remove(notMembers.toArray(Long[]::new));
        socialMediaLinkDAO.merge(false, links.toArray(SocialMediaLink[]::new));
    }

    public int getAligulacBatchSize()
    {
        return aligulacBatchSize;
    }

    protected void setAligulacBatchSize(int aligulacBatchSize)
    {
        if(aligulacBatchSize < 1) throw new IllegalArgumentException("Only positive values allowed");
        this.aligulacBatchSize = aligulacBatchSize;
    }

    public int getLinkWebBatchSize()
    {
        return linkWebBatchSize;
    }

    public int getLinkDbBatchSize()
    {
        return linkDbBatchSize;
    }

    public Optional<ProPlayer> importProfile(String url)
    {
        Long aligulacId = getAligulacProfileId(url);
        AligulacProPlayerRoot root = aligulacAPI.getPlayers(aligulacId).block();
        if(root == null || root.getObjects().length == 0) return Optional.empty();

        Triple<ProPlayer, List<SocialMediaLink>, ProTeam> proPlayerData =
            extractProPlayerData(root.getObjects()[0]);
        proPlayerService.importProfile
        (
            proPlayerData.getLeft(),
            proPlayerData.getRight(),
            proPlayerData.getMiddle().toArray(SocialMediaLink[]::new)
        );
        return Optional.of(proPlayerData.getLeft());
    }

    public static Triple<ProPlayer, List<SocialMediaLink>, ProTeam> extractProPlayerData
    (AligulacProPlayer aligulacProPlayer)
    {
        ProPlayer proPlayer = new ProPlayer();
        proPlayer.setAligulacId(aligulacProPlayer.getId());
        ProPlayer.update(proPlayer, aligulacProPlayer);

        return new ImmutableTriple<>
        (
            proPlayer,
            extractLinks(proPlayer, aligulacProPlayer),
            ProTeam.of(aligulacProPlayer)
        );
    }

    public static List<SocialMediaLink> extractLinks
    (
        ProPlayer proPlayer,
        AligulacProPlayer aligulacProPlayer
    )
    {
        List<SocialMediaLink> links = new ArrayList<>(2);
        links.add(new SocialMediaLink
        (
            proPlayer.getId(),
            SocialMedia.ALIGULAC,
            trimAligulacProfileLink(ALIGULAC_PROFILE_PREFIX + aligulacProPlayer.getId())
        ));
        if(aligulacProPlayer.getLiquipediaName() != null)
            links.add(new SocialMediaLink
            (
                proPlayer.getId(),
                SocialMedia.LIQUIPEDIA,
                LIQUIPEDIA_PROFILE_PREFIX + aligulacProPlayer.getLiquipediaName()
            ));
        return links;
    }

    @Retryable(backoff = @Backoff(delay = 2000L, maxDelay = 5000L))
    @Transactional
    public void importProfile(ProPlayer proPlayer, ProTeam proTeam, SocialMediaLink... links)
    {
        proPlayerDAO.merge(proPlayer);
        for(SocialMediaLink link : links) link.setProPlayerId(proPlayer.getId());
        socialMediaLinkDAO.merge(false, links);
        if(proTeam != null)
        {
            proTeamDAO.merge(proTeam);
            proTeamMemberDAO.merge(new ProTeamMember(proTeam.getId(), proPlayer.getId()));
        }
    }

    public static long getAligulacProfileId(String url)
    {
        Matcher matcher = ProPlayerService.ALIGULAC_PROFILE_PATTERN.matcher(url);
        if(!matcher.matches()) throw new IllegalArgumentException("Invalid url: " + url) ;

        return Long.parseLong(matcher.group(1));
    }

    public static String trimAligulacProfileLink(String url)
    {
        int pos = url.indexOf("-");
        return pos > 0 ? url.substring(0, pos) : url;
    }

    /**
     * Update social media links of pro players. This method is not thread safe.
     * @param ignoreParsingErrors Log errors and ignore them if true, throw exception if false.
     * @return async number of updated links.
     */
    public Mono<Integer> updateSocialMediaLinks(boolean ignoreParsingErrors)
    {
        List<SocialMediaLink> links = socialMediaLinkDAO.findByTypes(SocialMedia.LIQUIPEDIA);
        return getSocialMediaLinks(links, ignoreParsingErrors)
            .buffer(linkDbBatchSize)
            .flatMap(smLinks->Mono.fromCallable(()->
            {
                socialMediaLinkDAO.merge(false, smLinks.toArray(SocialMediaLink[]::new));
                return smLinks.size();
            }))
            .reduce(0, Integer::sum)
            .doOnNext(updatedCount->
            {
                if(updatedCount > 0) LOG.info("Updated {} social media links", updatedCount);
            });
    }

    public Mono<Integer> updateSocialMediaLinks()
    {
        return updateSocialMediaLinks(true);
    }

    private Flux<SocialMediaLink> getSocialMediaLinks
    (
        Collection<? extends SocialMediaLink> lpLinks,
        boolean ignoreParsingErrors
    )
    {
        Map<String, Long> idMap = lpLinks.stream()
            .map(lpLink->new ImmutablePair<>
            (
                PlayerCharacterLink.getRelativeUrl(lpLink.getUrl()).orElse(null),
                lpLink.getProPlayerId()
            ))
            .filter(pair->pair.getKey() != null)
            .collect(Collectors.toMap(Pair::getKey, Pair::getValue, (l, r)->l));
        return Flux.fromIterable(idMap.keySet())
            .buffer(linkWebBatchSize)
            .flatMap(lpNames->
            {
                Flux<LiquipediaPlayer> lpPlayers
                    = liquipediaAPI.parsePlayers(lpNames.toArray(String[]::new));
                return ignoreParsingErrors
                    ? lpPlayers.doOnError(ex->LOG.error(ex.getMessage(), ex)).onErrorComplete()
                    : lpPlayers;
            })
            .flatMap(lpPlayer->Flux.fromStream
            (
                extractSocialMediaLinks(idMap.get(lpPlayer.getQueryName()), lpPlayer
            )));
    }

    private static Stream<SocialMediaLink> extractSocialMediaLinks
    (
        Long proPlayerId,
        LiquipediaPlayer player
    )
    {
        return player.getLinks().stream()
            .map(url->new SocialMediaLink
            (
                proPlayerId,
                SocialMedia.fromBaseUrlPrefix(url),
                url
            ))
            .filter(link->SUPPORTED_SOCIAL_MEDIA.contains(link.getType()));
    }

}
