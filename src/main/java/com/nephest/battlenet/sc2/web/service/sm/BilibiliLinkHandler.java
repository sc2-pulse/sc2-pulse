// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.sm;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import com.nephest.battlenet.sc2.util.MiscUtil;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class BilibiliLinkHandler
implements SocialMediaLinkResolver
{

    public static final Pattern USER_URL_PATTERN = Pattern
        .compile("^" + SocialMedia.BILIBILI.getBaseUserUrl() + "/(\\d+)/?$");

    public static void resolve(SocialMediaLink link)
    {
        Matcher matcher = USER_URL_PATTERN.matcher(link.getUrl());
        if(!matcher.matches()) return;

        if(link.getUrl().endsWith("/"))
            link.setUrl(link.getUrl().substring(0, link.getUrl().length() - 1));
        String id = matcher.group(1);
        if(MiscUtil.tryParseLong(id) == null) return; //check if within long bounds

        link.setServiceUserId(id);
    }

    @Override
    public Mono<Void> resolve(Collection<? extends SocialMediaLink> link)
    {
        link.forEach(BilibiliLinkHandler::resolve);
        return Mono.empty();
    }

    @Override
    public SocialMedia getSupportedSocialMedia()
    {
        return SocialMedia.BILIBILI;
    }

}
