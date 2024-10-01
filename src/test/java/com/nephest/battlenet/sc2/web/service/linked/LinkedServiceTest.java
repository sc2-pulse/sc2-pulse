// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.linked;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.nephest.battlenet.sc2.model.SocialMedia;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class LinkedServiceTest
{

    private static final class TestDiscordLinkedAccountSupplier
    implements LinkedAccountSupplier
    {

        @Override
        public SocialMedia getSocialMedia()
        {
            return SocialMedia.DISCORD;
        }

        @Override
        public Optional<?> getAccountByPulseAccountId(long pulseAccountId)
        {
            return Optional.empty();
        }

    }

    @Test
    public void whenMultipleAccountSuppliersWithSameMediaType_thenThrowException()
    {
        assertThrows
        (
            IllegalArgumentException.class,
            ()->new LinkedAccountService(List.of(
                new TestDiscordLinkedAccountSupplier(),
                new TestDiscordLinkedAccountSupplier()
            )),
            "Suppliers must have unique media"
        );
    }

}
