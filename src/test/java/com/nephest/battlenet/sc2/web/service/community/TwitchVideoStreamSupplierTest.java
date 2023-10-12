// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.nephest.battlenet.sc2.web.service.TwitchAPI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TwitchVideoStreamSupplierTest
{

    @Mock
    private TwitchAPI api;

    private TwitchVideoStreamSupplier supplier;

    @BeforeEach
    public void beforeEach()
    {
        supplier = new TwitchVideoStreamSupplier(api);
    }

    @CsvSource
    ({
        "https://static-cdn.jtvnw.net/jtv_user_pictures/"
            + "ed0caac8-947b-412f-8a54-411588450540-profile_image-50x50.png, "
        + "https://static-cdn.jtvnw.net/jtv_user_pictures/"
            + "ed0caac8-947b-412f-8a54-411588450540-profile_image-50x50.png ",

        "https://static-cdn.jtvnw.net/jtv_user_pictures/"
            + "ed0caac8-947b-412f-8a54-411588450540-profile_image.png, "
        + "https://static-cdn.jtvnw.net/jtv_user_pictures/"
            + "ed0caac8-947b-412f-8a54-411588450540-profile_image.png ",

        "https://static-cdn.jtvnw.net/jtv_user_pictures/"
            + "ed0caac8-947b-412f-8a54-100x10050540-profile_image.png, "
        + "https://static-cdn.jtvnw.net/jtv_user_pictures/"
            + "ed0caac8-947b-412f-8a54-100x10050540-profile_image.png ",

        "https://static-cdn.jtvnw.net/jtv_user_pictures/"
            + "ed0caac8-947b-412f-8a54-100x10050540-profile_image-100x100.png, "
        + "https://static-cdn.jtvnw.net/jtv_user_pictures/"
            + "ed0caac8-947b-412f-8a54-100x10050540-profile_image-50x50.png ",
    })
    @ParameterizedTest
    public void normalizeStreamProfileImageUrlDimensions(String in, String out)
    {
        assertEquals(out, TwitchVideoStreamSupplier.normalizeStreamProfileImageUrlDimensions(in));
    }

}
