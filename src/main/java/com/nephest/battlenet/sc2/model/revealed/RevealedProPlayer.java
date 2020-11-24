// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.revealed;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.nephest.battlenet.sc2.config.convert.jackson.SocialMediaMapDeserializer;
import com.nephest.battlenet.sc2.model.SocialMedia;

import javax.validation.constraints.NotNull;
import java.util.Map;

public class RevealedProPlayer
{

    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final Long[] EMPTY_LONG_ARRAY = new Long[0];

    @NotNull
    private String _id;

    private String player;

    private String firstName;

    private String lastName;

    private String currentTeam;

    private String[] bnetTags = EMPTY_STRING_ARRAY;

    private Long[] knownIds = EMPTY_LONG_ARRAY;

    @JsonDeserialize(using = SocialMediaMapDeserializer.class)
    private Map<SocialMedia, String> socialMedia;

    private Map<String, String> nationality;

    public RevealedProPlayer(){}

    public RevealedProPlayer
    (
        @NotNull String _id,
        String player,
        String firstName,
        String lastName,
        String currentTeam,
        String[] bnetTags,
        Long[] knownIds,
        Map<SocialMedia, String> socialMedia,
        Map<String, String> nationality
    )
    {
        this._id = _id;
        this.player = player;
        this.firstName = firstName;
        this.lastName = lastName;
        this.currentTeam = currentTeam;
        this.bnetTags = bnetTags;
        this.knownIds = knownIds;
        this.socialMedia = socialMedia;
        this.nationality = nationality;
    }

    public String get_id()
    {
        return _id;
    }

    public void set_id(String _id)
    {
        this._id = _id;
    }

    public String getPlayer()
    {
        return player;
    }

    public void setPlayer(String player)
    {
        this.player = player;
    }

    public String getFirstName()
    {
        return firstName;
    }

    public void setFirstName(String firstName)
    {
        this.firstName = firstName;
    }

    public String getLastName()
    {
        return lastName;
    }

    public void setLastName(String lastName)
    {
        this.lastName = lastName;
    }

    public String getCurrentTeam()
    {
        return currentTeam;
    }

    public void setCurrentTeam(String currentTeam)
    {
        this.currentTeam = currentTeam;
    }

    public String[] getBnetTags()
    {
        return bnetTags;
    }

    public void setBnetTags(String[] bnetTags)
    {
        this.bnetTags = bnetTags;
    }

    public Long[] getKnownIds()
    {
        return knownIds;
    }

    public void setKnownIds(Long[] knownIds)
    {
        this.knownIds = knownIds;
    }

    public Map<SocialMedia, String> getSocialMedia()
    {
        return socialMedia;
    }

    public void setSocialMedia(Map<SocialMedia, String> socialMedia)
    {
        this.socialMedia = socialMedia;
    }

    public Map<String, String> getNationality()
    {
        return nationality;
    }

    public void setNationality(Map<String, String> nationality)
    {
        this.nationality = nationality;
    }

}
