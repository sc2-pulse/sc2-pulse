// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.config.security.BlizzardOidcUser;
import com.nephest.battlenet.sc2.model.Partition;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.blizzard.BlizzardFullPlayerCharacter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class PersonalService
{

    private final BlizzardSC2API api;


    @Autowired
    public PersonalService(BlizzardSC2API api)
    {
        this.api = api;
    }

    public Optional<BlizzardOidcUser> getOidcUser()
    {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if(!(principal instanceof BlizzardOidcUser)) return Optional.empty();

        return Optional.of((BlizzardOidcUser) principal);
    }

    public List<BlizzardFullPlayerCharacter> getCharacters()
    {
        return getOidcUser()
            .map(u->u.getAccount().getPartition() == Partition.GLOBAL
                ? api.getPlayerCharacters(Region.EU, Long.parseLong(u.getSubject()))
                    .collectList().block()
                : new ArrayList<BlizzardFullPlayerCharacter>())
            .orElse(List.of());
    }

}
