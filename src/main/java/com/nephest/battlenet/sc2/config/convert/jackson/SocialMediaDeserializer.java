// Copyright (C) 2020 Oleksandr Masniuk and contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.convert.jackson;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.nephest.battlenet.sc2.model.SocialMedia;

public class SocialMediaDeserializer
extends KeyDeserializer
{

    @Override
    public Object deserializeKey(String s, DeserializationContext deserializationContext)
    {
        return SocialMedia.fromRevealedName(s);
    }

}
