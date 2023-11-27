// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.liquipedia;

import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPatch;
import com.nephest.battlenet.sc2.model.liquipedia.LiquipediaPlayer;
import com.nephest.battlenet.sc2.model.liquipedia.query.revision.LiquipediaMediaWikiRevisionQueryResult;
import com.nephest.battlenet.sc2.model.liquipedia.query.revision.Normalization;
import com.nephest.battlenet.sc2.model.liquipedia.query.revision.RevisionPage;
import com.nephest.battlenet.sc2.model.liquipedia.query.revision.RevisionSlot;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class LiquipediaParser
{

    private static final Logger LOG = LoggerFactory.getLogger(LiquipediaParser.class);

    public static final Map<SocialMedia, String> WIKI_TEXT_LINKS = Map.of
    (
        SocialMedia.TWITCH, "twitch",
        SocialMedia.TWITTER, "twitter",
        SocialMedia.DISCORD, "discord",
        SocialMedia.YOUTUBE, "youtube",
        SocialMedia.INSTAGRAM, "instagram"
    );

    public static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of
    (
        DateTimeFormatter.ofPattern("d MMMM[,] yyyy").withLocale(Locale.US),
        DateTimeFormatter.ofPattern("MMMM d['st']['nd']['rd']['th'][,] yyyy").withLocale(Locale.US)
    );
    public static final Predicate<String> DATE_PREDICATE = str->
    {
        String sanitized = str.trim().toLowerCase();
        return !sanitized.isEmpty()
            && !sanitized.equals("n/a")
            && !sanitized.equals("tbd")
            && !sanitized.equals("unknown");
    };


    public static final String PATCH_HEADER = "[[Patch";
    public static final LocalDate FIRST_PATCH_DATE = LocalDate.of(2010, 7, 30);
    private static final Map<Region, String> REGION_NAMES;
    static
    {
        Map<Region, String> names = new EnumMap<>(Region.class);
        names.put(Region.US, "na");
        names.put(Region.EU, "eu");
        names.put(Region.KR, "kor");
        REGION_NAMES = Collections.unmodifiableMap(names);
    }

    private LiquipediaParser(){}

    public static List<LiquipediaPlayer> parse(LiquipediaMediaWikiRevisionQueryResult result)
    {
        return result.getQuery().getPages().stream()
            .map(page->parse(result.getQuery().getNormalizations(), page))
            .collect(Collectors.toList());
    }

    private static LiquipediaPlayer parse
    (
        List<Normalization> normalizations,
        RevisionPage revisionPage
    )
    {
        String name = revisionPage.getTitle();
        RevisionSlot mainSlot = revisionPage.getRevisions() != null
            && !revisionPage.getRevisions().isEmpty()
                ? revisionPage.getRevisions().get(0).getSlots() != null
                    ? revisionPage.getRevisions().get(0).getSlots().get("main")
                    : null
                : null;
        String text = mainSlot != null ? mainSlot.getContent() : null;
        String queryName = getPlayerQueryName(normalizations, name);
        if(text == null ) return new LiquipediaPlayer(name, queryName, List.of());

        String redirect = getRedirect(text);
        if(redirect != null) return LiquipediaPlayer.redirect(name, queryName, redirect);

        return new LiquipediaPlayer(name, queryName, parseWikiTextLinks(text));
    }

    private static String getPlayerQueryName
    (
        List<Normalization> normalizations,
        String name
    )
    {
        if(normalizations == null || normalizations.isEmpty()) return name;

        return normalizations.stream()
            .filter(normalization->normalization.getTo().equals(name))
            .map(Normalization::getFrom)
            .findAny()
            .orElse(name);
    }

    private static String getRedirect(String text)
    {
        return text.startsWith("#REDIRECT")
            ? text.substring(text.indexOf("[[") + 2, text.indexOf("]]")).trim()
            : null;
    }

    private static List<String> parseWikiTextLinks(String text)
    {
        int infoboxIx = text.indexOf("{Infobox player");
        String linkText;
        if(infoboxIx != -1)
        {
            int infoboxEndIx = text.indexOf("{PlayerIntroduction", infoboxIx);
            linkText = infoboxEndIx != -1
                ? text.substring(infoboxIx, infoboxEndIx)
                : text;
        } else
        {
            linkText = text;
        }
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
        return id.isEmpty()
            ? null
            : id.startsWith("http")
                ? sanitizeUrl(id)
                : type.getBaseUrl() + "/" + sanitizeUserUrl(id);
    }

    public static String sanitizeUrl(String url)
    {
        url = url.trim();
        return url.startsWith("https://web.archive.org/web/")
            ? url.substring(url.indexOf("http", 7))
            : url;
    }

    public static String sanitizeUserUrl(String url)
    {
        url = url.trim();
        if(url.startsWith("/")) url = url.substring(1);

        return url;
    }

    public static List<LiquipediaPatch> parsePatchList(LiquipediaMediaWikiRevisionQueryResult patchList)
    {
        List<RevisionPage> pages = patchList.getQuery().getPages();
        if(pages.isEmpty()) return List.of();

        String content = pages.get(0).getRevisions().get(0).getSlots().get("main").getContent();
        int ix = 0;
        List<LiquipediaPatch> patches = new ArrayList<>();
        while(true)
        {
            int beginIx = content.indexOf(PATCH_HEADER, ix);
            if(beginIx == -1) break;

            int endIx = content.indexOf("[[", beginIx + PATCH_HEADER.length());
            if(endIx == -1) break;

            String[] patchData = content.substring(beginIx, endIx).split("\n\\|");
            String version = parsePatchVersion(patchData[0]);
            Map<Region, LocalDate> releases = parsePatchListReleases(patchData[1], version);
            if(releases.values().stream().anyMatch(r->r.isBefore(FIRST_PATCH_DATE))) break;

            long build = Long.parseLong(patchData[2].trim());
            Boolean versus = isBalanceUpdateVersion(version) ? true : null;
            patches.add(new LiquipediaPatch(build, version, releases, versus));
            ix = endIx;
        }
        return patches;
    }

    public static boolean isBalanceUpdateVersion(String version)
    {
        return version.contains("BU");
    }

    private static String parsePatchVersion(String rawVersion)
    {
        String[] versions = rawVersion.split("\\|");
        String version = versions[versions.length - 1].trim();
        return version.substring(versions.length > 1 ? 6 : 8, version.length() - 2);
    }

    private static Map<Region, LocalDate> parsePatchListReleases(String releaseStr, String version)
    {
        LocalDate released = parseDate(releaseStr.trim());

        Map<Region, LocalDate> releases = new EnumMap<>(Region.class);
        releases.put(Region.US, released);
        if(version.endsWith("BU"))
        {
            releases.put(Region.EU, released);
            releases.put(Region.KR, released);
        }
        return releases;
    }

    public static LocalDate parseDate(String str)
    {
        for(DateTimeFormatter formatter : DATE_TIME_FORMATTERS)
        {
            try
            {
                return LocalDate.parse(str, formatter);
            }
            catch (Exception e)
            {
                LOG.debug(e.getMessage(), e);
            }
        }

        throw new IllegalArgumentException(str);
    }

    public static List<LiquipediaPatch> parsePatches(LiquipediaMediaWikiRevisionQueryResult patchList)
    {
        return patchList.getQuery().getPages().stream()
            .map(page->page.getRevisions().get(0).getSlots().get("main").getContent())
            .map(LiquipediaParser::parsePatch)
            .collect(Collectors.toList());
    }

    private static LiquipediaPatch parsePatch(String content)
    {
        Map<String, String> header = parsePatchHeader(content);
        Long build = Optional.ofNullable(header.get("version")).map(Long::parseLong).orElse(null);
        String version = header.get("name").substring(6);
        Map<Region, LocalDate> releases = parsePatchReleases(header);
        if(releases.isEmpty()) throw new IllegalStateException(
            "Invalid patch " + version + ", no release dates found");

        boolean versus = isVersusPatch(header, content);
        return new LiquipediaPatch(build, version, releases, versus);
    }

    private static Map<String, String> parsePatchHeader(String content)
    {
        int startIx = content.indexOf("{{Infobox patch");
        int endIx = content.indexOf("}}", startIx);
        return Arrays.stream(content.substring(startIx, endIx).split("\n\\|"))
            .map(strEntry->strEntry.trim().split("="))
            .filter(splitEntry->splitEntry.length == 2)
            .collect(Collectors.toMap(pair->pair[0].trim(), pair->pair[1].trim()));
    }

    private static Map<Region, LocalDate> parsePatchReleases(Map<String, String> header)
    {
        return REGION_NAMES.entrySet().stream()
            .map(entry->new Object[]{
                entry.getKey(),
                Optional.ofNullable(header.get(entry.getValue() + "release"))
                    .filter(DATE_PREDICATE)
                    .map(LiquipediaParser::parseDate)
                    .orElse(null)
            })
            .filter(entry->entry[1] != null)
            .collect(Collectors.toMap(entry->(Region) entry[0], entry->(LocalDate) entry[1]));
    }

    private static boolean isVersusPatch(Map<String, String> header, String content)
    {
        String lower = content.toLowerCase();
        return
        (
            header.entrySet().stream()
                .filter(e->e.getKey().startsWith("highlight"))
                .map(Map.Entry::getValue)
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(highlight->highlight.contains("versus") || highlight.contains("balance"))
                    && !lower.contains("balance test mod")
        )
            || lower.contains("map pool");
    }

    public static LiquipediaPatch mergePatch
    (
        LiquipediaPatch patchFromList,
        LiquipediaPatch patchDetails
    )
    {
        return patchDetails.getBuild() == null
            ? new LiquipediaPatch
                (
                    patchFromList.getBuild(),
                    patchDetails.getVersion(),
                    patchDetails.getReleases(),
                    patchDetails.isVersus()
                )
            : patchDetails;
    }

}
