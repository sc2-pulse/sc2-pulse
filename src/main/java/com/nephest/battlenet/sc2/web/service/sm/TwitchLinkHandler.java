// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.sm;

import com.github.twitch4j.TwitchClient;
import com.github.twitch4j.helix.domain.User;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import com.nephest.battlenet.sc2.twitch.Twitch;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import rx.RxReactiveStreams;

@Component
@Twitch
public class TwitchLinkHandler
implements SocialMediaLinkResolver, SocialMediaLinkUpdater
{

    private final TwitchClient twitchClient;

    @Autowired
    public TwitchLinkHandler(TwitchClient twitchClient)
    {
        this.twitchClient = twitchClient;
    }

    @Override
    public Mono<Void> resolve(Collection<? extends SocialMediaLink> links)
    {
        Map<String, List<SocialMediaLink>> loginLinks = links.stream()
            .collect(Collectors.groupingBy(l->l.getUrl().substring(l.getUrl().lastIndexOf("/") + 1)));
        List<String> twitchLogins = new ArrayList<>(loginLinks.keySet());

        return Flux.from(RxReactiveStreams.toPublisher(
            twitchClient.getHelix().getUsers(null, null, twitchLogins).toObservable()))
                .flatMap(u->Flux.fromIterable(u.getUsers()))
                .map(u->{
                    loginLinks.get(u.getLogin())
                        .forEach(link->link.setServiceUserId(u.getId()));
                    return u;
                })
                .then();
    }

    @Override
    public Flux<SocialMediaLink> update(Collection<? extends SocialMediaLink> links)
    {
        links.stream()
            .filter(l->l.getType() != getSupportedSocialMedia()).findAny()
            .ifPresent(l->{throw new IllegalArgumentException("Invalid link: " + l);});

        Map<String, List<SocialMediaLink>> idLinks = links.stream()
            .filter(link->link.getServiceUserId() != null)
            .collect(Collectors.groupingBy(SocialMediaLink::getServiceUserId));
        List<String> twitchIds = new ArrayList<>(idLinks.keySet());

        return Flux.from(RxReactiveStreams.toPublisher(
                twitchClient.getHelix().getUsers(null, twitchIds, null).toObservable()))
            .flatMap(u->Flux.fromIterable(u.getUsers()))
            .flatMapIterable(user->update(user, idLinks.get(user.getId())));
    }

    private List<SocialMediaLink> update(User user, Collection<SocialMediaLink> links)
    {
        return links.stream()
            .filter(link->update(user, link))
            .collect(Collectors.toList());
    }

    private boolean update(User user, SocialMediaLink link)
    {
        String url = link.getType().getBaseUserUrl() + "/" + user.getLogin();
        if(link.getUrl().equals(url)) return false;

        link.setUrl(url);
        link.setUpdated(OffsetDateTime.now());
        return true;
    }

    @Override
    public SocialMedia getSupportedSocialMedia()
    {
        return SocialMedia.TWITCH;
    }

}
