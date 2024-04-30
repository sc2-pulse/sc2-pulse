// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.sm;

import com.github.twitch4j.helix.domain.User;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import com.nephest.battlenet.sc2.twitch.Twitch;
import com.nephest.battlenet.sc2.web.service.TwitchAPI;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@Twitch
public class TwitchLinkHandler
implements SocialMediaLinkResolver, SocialMediaLinkUpdater
{

    private final TwitchAPI api;

    @Autowired
    public TwitchLinkHandler(TwitchAPI api)
    {
        this.api = api;
    }

    @Override
    public Mono<Void> resolve(Collection<? extends SocialMediaLink> links)
    {
        Map<String, List<SocialMediaLink>> loginLinks
            = links.stream().collect(Collectors.groupingBy(l->
                l.getUrl().substring(l.getUrl().lastIndexOf("/") + 1).toLowerCase()));

        return api.getUsersByLogins(loginLinks.keySet())
                .map(u->{
                    loginLinks.get(u.getLogin().toLowerCase()).forEach(link->resolve(u, link));
                    return u;
                })
                .then();
    }

    private void resolve(User user, SocialMediaLink link)
    {
        link.setServiceUserId(user.getId());
        update(user, link);
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

        return api.getUsersByIds(idLinks.keySet())
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
        link.setUpdated(SC2Pulse.offsetDateTime());
        return true;
    }

    @Override
    public SocialMedia getSupportedSocialMedia()
    {
        return SocialMedia.TWITCH;
    }

}
