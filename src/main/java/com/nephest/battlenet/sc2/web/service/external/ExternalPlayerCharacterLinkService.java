// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.external;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterLinkDAO;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class ExternalPlayerCharacterLinkService
{

    private static final Logger LOG = LoggerFactory.getLogger(ExternalPlayerCharacterLinkService.class);

    private final Map<SocialMedia, ExternalCharacterLinkResolver> resolvers;
    private final PlayerCharacterLinkDAO playerCharacterLinkDAO;

    public ExternalPlayerCharacterLinkService
    (
        List<ExternalCharacterLinkResolver> resolvers,
        PlayerCharacterLinkDAO playerCharacterLinkDAO
    )
    {
        this.resolvers = resolvers.stream()
            .collect(Collectors.toMap
            (
                ExternalCharacterLinkResolver::getSupportedSocialMedia,
                Function.identity(),
                (l, r)->{throw new IllegalStateException("Unexpected merge");},
                ()->new EnumMap<>(SocialMedia.class)
            ));
        this.playerCharacterLinkDAO = playerCharacterLinkDAO;
    }

    public ExternalLinkResolveResult getLinks(PlayerCharacter playerCharacter)
    {
        List<PlayerCharacterLink> existingLinks = playerCharacterLinkDAO.find(playerCharacter.getId());
        List<PlayerCharacterLink> missingLinks = getMissingLinks(playerCharacter, existingLinks)
            .collectList()
            .block();
        List<PlayerCharacterLink> resolvedLinks = new ArrayList<>(missingLinks.size());
        Set<SocialMedia> failedMedia = EnumSet.noneOf(SocialMedia.class);
        for(PlayerCharacterLink link : missingLinks)
        {
            if(link.getRelativeUrl() != null)
            {
                resolvedLinks.add(link);
            }
            else
            {
                failedMedia.add(link.getType());
            }
        }
        saveStaticLinks(resolvedLinks);
        existingLinks.addAll(resolvedLinks);
        return new ExternalLinkResolveResult(existingLinks, failedMedia);
    }

    private Flux<PlayerCharacterLink> getMissingLinks
    (
        PlayerCharacter playerCharacter,
        List<PlayerCharacterLink> existingLinks
    )
    {
        Set<SocialMedia> existingTypes = existingLinks.stream()
            .map(PlayerCharacterLink::getType)
            .collect(Collectors.toSet());
        return Flux.fromStream
        (
            resolvers.values().stream()
                .filter(resolver->!existingTypes.contains(resolver.getSupportedSocialMedia()))
                .map(resolver->resolver
                    .resolveLink(playerCharacter)
                    .onErrorResume
                    (
                        WebServiceUtil::isClientResponseException,
                        t->
                        {
                            LOG.error(t.getMessage());
                            return Mono.just(new PlayerCharacterLink
                            (
                                playerCharacter.getId(),
                                resolver.getSupportedSocialMedia(),
                                null
                            ));
                        }
                    )
                )
        )
            .flatMap(Function.identity());
    }

    private void saveStaticLinks(List<PlayerCharacterLink> missingLinks)
    {
        if(missingLinks.isEmpty()) return;

        List<PlayerCharacterLink> linksToSave = missingLinks.stream()
            .filter(link->resolvers.get(link.getType()).isStatic())
            .collect(Collectors.toList());
        playerCharacterLinkDAO.merge(linksToSave.toArray(PlayerCharacterLink[]::new));
    }

}
