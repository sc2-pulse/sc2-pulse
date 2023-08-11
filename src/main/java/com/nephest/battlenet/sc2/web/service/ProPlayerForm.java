// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.model.local.ProPlayer;
import com.nephest.battlenet.sc2.model.local.SocialMediaLink;
import java.util.List;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;

@Validated
public class ProPlayerForm
{

    @Valid @NotNull
    private ProPlayer proPlayer;

    @Valid @NotNull
    private List<@NotBlank @Size(max = SocialMediaLink.URL_MAX_LENGTH) String> links;

    public ProPlayerForm()
    {
    }

    public ProPlayerForm
    (
        ProPlayer proPlayer,
        List<@NotBlank @Size(max = SocialMediaLink.URL_MAX_LENGTH) String> links
    )
    {
        this.proPlayer = proPlayer;
        this.links = links;
    }

    public ProPlayer getProPlayer()
    {
        return proPlayer;
    }

    public void setProPlayer(ProPlayer proPlayer)
    {
        this.proPlayer = proPlayer;
    }

    public List<String> getLinks()
    {
        return links;
    }

    public void setLinks(List<String> links)
    {
        this.links = links;
    }

}
