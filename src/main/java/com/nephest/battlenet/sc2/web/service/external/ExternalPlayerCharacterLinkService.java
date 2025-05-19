// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.external;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterLinkDAO;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.commons.lang3.exception.ExceptionUtils;
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
        List<PlayerCharacterLink> existingLinks = playerCharacterLinkDAO
            .find(Set.of(playerCharacter.getId()));
        Set<SocialMedia> existingTypes = existingLinks.stream()
            .map(PlayerCharacterLink::getType)
            .collect(Collectors.toSet());
        Set<SocialMedia> missingTypes = resolvers.values().stream()
            .map(ExternalCharacterLinkResolver::getSupportedSocialMedia)
            .filter(type->!existingTypes.contains(type))
            .collect(Collectors.toSet());
        List<PlayerCharacterLink> resolvedLinks = resolveLinks(playerCharacter, missingTypes)
            .collectList()
            .block();
        Set<SocialMedia> failedTypes = resolvedLinks.stream()
            .filter(link->link.getRelativeUrl() == null)
            .map(PlayerCharacterLink::getType)
            .collect(Collectors.toSet());

        Set<PlayerCharacterLink> validResolvedLinks = new LinkedHashSet<>(resolvedLinks.size());
        for(PlayerCharacterLink link : resolvedLinks)
            if(link.getRelativeUrl() != null)
                validResolvedLinks.add(link);
        saveStaticLinks(validResolvedLinks);
        existingLinks.addAll(validResolvedLinks);
        return new ExternalLinkResolveResult(existingLinks, failedTypes);
    }

    private Flux<PlayerCharacterLink> resolveLinks
    (
        PlayerCharacter playerCharacter,
        Collection<SocialMedia> missingTypes
    )
    {
        return Flux.fromStream
        (
            resolvers.values().stream()
                .filter(resolver->missingTypes.contains(resolver.getSupportedSocialMedia()))
                .map(resolver->resolver
                    .resolveLink(playerCharacter)
                    .onErrorResume
                    (
                        WebServiceUtil::isClientResponseException,
                        t->
                        {
                            LOG.error(ExceptionUtils.getRootCauseMessage(t));
                            return !WebServiceUtil.isClientResponseNotFound(t)
                                ?
                                    Mono.just(new PlayerCharacterLink
                                    (
                                        playerCharacter.getId(),
                                        resolver.getSupportedSocialMedia(),
                                        null
                                    ))
                                : Mono.empty(); //not found is ok
                        }
                    )
                )
        )
            .flatMap(Function.identity());
    }

    private void saveStaticLinks(Set<PlayerCharacterLink> missingLinks)
    {
        if(missingLinks.isEmpty()) return;

        Set<PlayerCharacterLink> linksToSave = missingLinks.stream()
            .filter(link->resolvers.get(link.getType()).isStatic())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        playerCharacterLinkDAO.merge(linksToSave);
    }

}
