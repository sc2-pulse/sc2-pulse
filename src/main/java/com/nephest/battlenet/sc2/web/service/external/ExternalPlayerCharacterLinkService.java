// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.external;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.PlayerCharacterLink;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterLinkDAO;
import com.nephest.battlenet.sc2.web.service.WebServiceUtil;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    public Mono<ExternalLinkResolveResult> getLinks(PlayerCharacter playerCharacter)
    {
        return getLinks(Set.of(playerCharacter)).next();
    }

    public Flux<ExternalLinkResolveResult> getLinks(Set<PlayerCharacter> playerCharacters)
    {
        if(playerCharacters.isEmpty()) return Flux.empty();

        Map<Long, List<PlayerCharacterLink>> links = playerCharacterLinkDAO
            .find(playerCharacters.stream().map(PlayerCharacter::getId).collect(Collectors.toSet()))
            .stream()
            .collect(Collectors.groupingBy(PlayerCharacterLink::getPlayerCharacterId));
        return Flux.fromIterable(playerCharacters)
            .flatMap(playerCharacter->
                getLinks(playerCharacter, links.getOrDefault(playerCharacter.getId(), List.of())))
            .collectList()
            .flatMapMany
            (
                results->saveStaticLinks
                (
                    results.stream()
                        .map(ExternalLinkResolveResultMeta::newLinks)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toSet())
                )
                    .thenMany(Flux.fromIterable(results)
                        .map(ExternalLinkResolveResultMeta::result))
            );
    }

    private Mono<ExternalLinkResolveResultMeta> getLinks
    (
        PlayerCharacter playerCharacter,
        List<PlayerCharacterLink> existingLinks
    )
    {
        Set<SocialMedia> existingTypes = existingLinks.stream()
            .map(PlayerCharacterLink::getType)
            .collect(Collectors.toSet());
        Set<SocialMedia> missingTypes = resolvers.values().stream()
            .map(ExternalCharacterLinkResolver::getSupportedSocialMedia)
            .filter(type->!existingTypes.contains(type))
            .collect(Collectors.toSet());
        return resolveLinks(playerCharacter, missingTypes)
            .collectList()
            .map(resolvedLinks->{
                Set<SocialMedia> failedTypes = resolvedLinks.stream()
                    .filter(link->link.getRelativeUrl() == null)
                    .map(PlayerCharacterLink::getType)
                    .collect(Collectors.toSet());
                Set<PlayerCharacterLink> validResolvedLinks = resolvedLinks.stream()
                    .filter(link->link.getRelativeUrl() != null)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
                List<PlayerCharacterLink> allLinks = Stream.of(existingLinks, validResolvedLinks)
                    .flatMap(Collection::stream)
                    .toList();
                return new ExternalLinkResolveResultMeta
                (
                    new ExternalLinkResolveResult(allLinks, failedTypes),
                    existingLinks,
                    validResolvedLinks
                );
            });
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

    private Mono<Void> saveStaticLinks(Set<PlayerCharacterLink> missingLinks)
    {
        if(missingLinks.isEmpty()) return Mono.empty();

        Set<PlayerCharacterLink> linksToSave = missingLinks.stream()
            .filter(link->resolvers.get(link.getType()).isStatic())
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return WebServiceUtil.blockingRunnable(()->playerCharacterLinkDAO.merge(linksToSave));
    }

    private record ExternalLinkResolveResultMeta
    (
        @NotNull ExternalLinkResolveResult result,
        @NotNull List<PlayerCharacterLink> existingLinks,
        @NotNull Set<PlayerCharacterLink> newLinks
    ){}

}
