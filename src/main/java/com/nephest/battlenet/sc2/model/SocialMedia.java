// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

public enum SocialMedia
implements Identifiable
{

    ALIGULAC(1, "aligulac", null, "http://aligulac.com/players"),
    TWITCH(2, "twitch", null, "https://www.twitch.tv"),
    LIQUIPEDIA(3, "liquipedia", "liquidpedia", "https://liquipedia.net/starcraft2"),
    TWITTER(4, "twitter", null, "https://twitter.com"),
    INSTAGRAM(5, "instagram", null, "https://www.instagram.com"),
    DISCORD(6, "discord", null, null),
    YOUTUBE(7, "youtube", null, "https://www.youtube.com/c"),
    UNKNOWN(8, "", null, null),
    BATTLE_NET(9, "battlenet", null, "battlenet:://starcraft/profile");

    private final int id;
    private final String name;
    private final String revealedName;
    private final String baseUserUrl;

    SocialMedia(int id, String name, String revealedName, String baseUserUrl)
    {
        this.id = id;
        this.name = name;
        this.revealedName = revealedName;
        this.baseUserUrl = baseUserUrl;
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

    public static SocialMedia fromRevealedName(String name)
    {
        String lowerCaseName = name.toLowerCase();
        for(SocialMedia media : SocialMedia.values())
            if((media.getRevealedName() != null && media.getRevealedName().equals(lowerCaseName))
                || media.getName().equals(lowerCaseName)) return media;

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

    public String getRevealedName()
    {
        return revealedName;
    }

    public String getBaseUserUrl()
    {
        return baseUserUrl;
    }

}
