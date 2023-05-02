// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.liquipedia;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPlayer;
import com.nephest.battlenet.sc2.model.liquipedia.query.revision.LiquipediaMediaWikiRevisionQueryResult;
import com.nephest.battlenet.sc2.model.liquipedia.query.revision.RevisionPage;
import com.nephest.battlenet.sc2.model.liquipedia.query.revision.RevisionSlot;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class LiquipediaParser
{

    public static final Map<SocialMedia, String> WIKI_TEXT_LINKS = Map.of
    (
        SocialMedia.TWITCH, "twitch",
        SocialMedia.TWITTER, "twitter",
        SocialMedia.DISCORD, "discord",
        SocialMedia.YOUTUBE, "youtube",
        SocialMedia.INSTAGRAM, "instagram"
    );

    private LiquipediaParser(){}

    public static List<LiquipediaPlayer> parse(LiquipediaMediaWikiRevisionQueryResult result)
    {
        return result.getQuery().getPages().stream()
            .map(LiquipediaParser::parse)
            .collect(Collectors.toList());
    }

    private static LiquipediaPlayer parse(RevisionPage revisionPage)
    {
        String name = revisionPage.getTitle();
        RevisionSlot mainSlot = !revisionPage.getRevisions().isEmpty()
            ? revisionPage.getRevisions().get(0).getSlots().get("main")
            : null;
        String text = mainSlot != null ? mainSlot.getContent() : null;
        List<String> links = text != null ? parseWikiTextLinks(text) : List.of();
        return new LiquipediaPlayer(name, links);
    }

    private static List<String> parseWikiTextLinks(String text)
    {
        int infoboxIx = text.indexOf("{Infobox player");
        String linkText = infoboxIx != -1
            ? text.substring(infoboxIx, text.indexOf("<br", infoboxIx))
            : text;
        List<String> links = new ArrayList<>(WIKI_TEXT_LINKS.size());
        for(Map.Entry<SocialMedia, String> entry : WIKI_TEXT_LINKS.entrySet())
        {
            String link = parseWikiTextLink(linkText, entry.getKey(), entry.getValue());
            if(link != null) links.add(link);
        }
        return links;
    }

    private static String parseWikiTextLink(String text, SocialMedia type, String typeName)
    {
        int ix = text.indexOf(typeName + "=");
        if(ix == -1) return null;

        int idIx = ix + typeName.length() + 1;
        String id = text.substring(idIx, text.indexOf("\n", idIx)).trim();
        return id.startsWith("http")
            ? sanitizeUrl(id)
            : type.getBaseUrl() + "/" + id;
    }

    public static String sanitizeUrl(String url)
    {
        url = url.trim();
        return url.startsWith("https://web.archive.org/web/")
            ? url.substring(url.indexOf("http:", 7))
            : url;
    }

}
