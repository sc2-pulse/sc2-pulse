// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model;

public enum SocialMedia
implements Identifiable
{

    ALIGULAC(1, "aligulac"),
    TWITCH(2, "twitch"),
    LIQUIPEDIA(3, "liquipedia", "liquidpedia"),
    TWITTER(4, "twitter"),
    INSTAGRAM(5, "instagram"),
    DISCORD(6, "discord"),
    YOUTUBE(7, "youtube"),
    UNKNOWN(8, "");

    private final int id;
    private final String name;
    private final String revealedName;

    SocialMedia(int id, String name, String revealedName)
    {
        this.id = id;
        this.name = name;
        this.revealedName = revealedName;
    }

    SocialMedia(int id, String name)
    {
        this(id, name, null);
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

}
