// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.liquipedia;

import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPlayer;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public final class LiquipediaParser
{

    private LiquipediaParser(){}


    public static LiquipediaPlayer parsePlayer(String html)
    {
        Element infobox = Jsoup.parse(html)
            .select(".fo-nttax-infobox > div .infobox-header")
            .stream()
            .filter(e->e.hasText() && e.text().equalsIgnoreCase("player information"))
            .map(e->e.closest(".fo-nttax-infobox"))
            .findFirst()
            .orElseThrow();
        Elements infoboxDivs = infobox.select("> div");
        List<String> links = infoboxDivs.get(findHeaderIndex(infoboxDivs, "links") + 1)
            .select("a").stream()
            .map(e->e.attributes().get("href"))
            .map(LiquipediaParser::sanitizeUrl)
            .collect(Collectors.toList());
        return new LiquipediaPlayer(links);
    }

    private static int findHeaderIndex(Elements infobox, String header)
    {
        return IntStream.range(0, infobox.size())
            .filter(i->infobox.get(i).hasText() && infobox.get(i).text().trim().equalsIgnoreCase(header))
            .findFirst()
            .orElseThrow();
    }

    public static String sanitizeUrl(String url)
    {
        url = url.trim();
        return url.startsWith("https://web.archive.org/web/")
            ? url.substring(url.indexOf("http:", 7))
            : url;
    }

}
