// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.linked;

import com.nephest.battlenet.sc2.model.SocialMedia;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LinkedAccountService
{

    private final List<LinkedAccountSupplier> suppliers;

    @Autowired
    public LinkedAccountService(List<LinkedAccountSupplier> suppliers)
    {
        verifySuppliers(suppliers);
        this.suppliers = suppliers;
    }

    private void verifySuppliers(List<LinkedAccountSupplier> suppliers)
    {
        Set<SocialMedia> existingMedia = EnumSet.noneOf(SocialMedia.class);
        if(suppliers.stream().anyMatch(s->!existingMedia.add(s.getSocialMedia())))
            throw new IllegalArgumentException("Suppliers must have unique media");
    }

    public Map<SocialMedia, Object> getAccounts(long pulseAccountId)
    {
        return suppliers.stream()
            .map(s->new Object[]{
                s.getSocialMedia(),
                s.getAccountByPulseAccountId(pulseAccountId).orElse(null)
            })
            .filter(data->data[1] != null)
            .collect(Collectors.toMap(
                data->(SocialMedia) data[0],
                data->data[1]
            ));
    }

}
