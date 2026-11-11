// Copyright (C) 2020-2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.ad;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Objects;

/**
 * Rich ad data.
 *
 * @param name ad name, used as an alt/title text
 * @param url target URL the ad links to
 * @param imageFileNames image file names by device type. An ad may have no
 * image for a specific device type, in which case it is not shown on that
 * device.
 * @param isSponsored controls the "sponsored" link relation
 */
public record AdEntity
(
    String name,
    String url,
    Map<DeviceType, String> imageFileNames,
    @JsonProperty("isSponsored") boolean isSponsored
)
{

    public AdEntity
    {
        Objects.requireNonNull(name);
        Objects.requireNonNull(url);
        imageFileNames = imageFileNames == null
            ? Map.of()
            : Map.copyOf(imageFileNames);
    }


}
