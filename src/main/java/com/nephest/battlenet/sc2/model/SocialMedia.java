// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

public enum SocialMedia
implements Identifiable
{

    ALIGULAC(1, "aligulac", "http://aligulac.com", "/players"),
    TWITCH(2, "twitch", "https://www.twitch.tv", "/"),
    LIQUIPEDIA(3, "liquipedia", "https://liquipedia.net", "/starcraft2"),
    TWITTER(4, "twitter", "https://twitter.com", "/"),
    INSTAGRAM(5, "instagram", "https://www.instagram.com", "/"),
    DISCORD(6, "discord", "https://discord.gg", null),
    YOUTUBE(7, "youtube", "https://www.youtube.com", "/channel"),
    UNKNOWN(8, "", null, null),
    BATTLE_NET(9, "battlenet", "battlenet:://starcraft", "/profile"),
    REPLAY_STATS(10, "replaystats", "https://sc2replaystats.com", "/player"),
    BILIBILI(11, "bilibili", "https://space.bilibili.com", "/");

    private final int id;
    private final String name;
    private final String baseUrl;
    private final String baseUserUrl;

    SocialMedia(int id, String name, String baseUrl, String baseUserUrlSuffix)
    {
        this.id = id;
        this.name = name;
        this.baseUrl = baseUrl;
        this.baseUserUrl = baseUserUrlSuffix == null
            ? null
            : baseUserUrlSuffix.equals("/")
                ? baseUrl
                : baseUrl + baseUserUrlSuffix;
    }

    public static SocialMedia from(int id)
    {
        for(SocialMedia media : SocialMedia.values())
            if(media.getId() == id) return media;

        throw new IllegalArgumentException("Invalid id");
    }

    public static SocialMedia from(String name)
    {
        String lowerCaseName = name.toLowerCase();
        for(SocialMedia media : SocialMedia.values())
            if(media.getName().equals(lowerCaseName)) return media;

        return UNKNOWN;
    }

    public static SocialMedia fromBaseUrlPrefix(String url)
    {
        if(url == null || url.isBlank()) return UNKNOWN;

        for(SocialMedia media : SocialMedia.values())
            if(media.getBaseUrl() != null && url.startsWith(media.getBaseUrl())) return media;

        return UNKNOWN;
    }

    public static SocialMedia fromBaseUserUrlPrefix(String url)
    {
        if(url == null || url.isBlank()) return UNKNOWN;

        for(SocialMedia media : SocialMedia.values())
            if(media.getBaseUserUrl() != null && url.startsWith(media.getBaseUserUrl())) return media;

        return UNKNOWN;
    }

    public static long getAligulacIdFromUrl(String url)
    {
        return Long.parseLong(url.replaceFirst(".*/([^/?]+).*", "$1").split("-")[0]);
    }

    @Override
    public int getId()
    {
        return id;
    }

    public String getName()
    {
        return name;
    }

    public String getBaseUrl()
    {
        return baseUrl;
    }

    public String getBaseUserUrl()
    {
        return baseUserUrl;
    }

    public String getBaseUserOrBaseUrl()
    {
        return baseUserUrl != null ? baseUserUrl : baseUrl;
    }

}
