// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.link;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterLinkDAO;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ExternalPlayerCharacterLinkService
{

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

    public List<PlayerCharacterLink> getLinks(PlayerCharacter playerCharacter)
    {
        List<PlayerCharacterLink> existingLinks = playerCharacterLinkDAO.find(playerCharacter.getId());
        List<PlayerCharacterLink> missingLinks = getMissingLinks(playerCharacter, existingLinks)
            .collectList()
            .block();
        saveStaticLinks(missingLinks);
        existingLinks.addAll(missingLinks);
        return existingLinks;
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
                .map(resolver->resolver.resolveLink(playerCharacter))
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
