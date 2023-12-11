// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service.community;

import static com.nephest.battlenet.sc2.web.service.BilibiliAPI.STAR_CRAFT_2_AREA_ID;
import static com.nephest.battlenet.sc2.web.service.BilibiliAPI.STAR_CRAFT_2_PARENT_AREA_ID;

import com.nephest.battlenet.sc2.model.SocialMedia;
import com.nephest.battlenet.sc2.model.bilibili.BilibiliStream;
import com.nephest.battlenet.sc2.web.service.BilibiliAPI;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class BilibiliVideoStreamSupplier
implements VideoStreamSupplier
{

    private final BilibiliAPI api;

    @Autowired
    public BilibiliVideoStreamSupplier(BilibiliAPI api)
    {
        this.api = api;
    }

    @Override
    public Flux<VideoStream> getStreams()
    {
        return api.getStreams(STAR_CRAFT_2_PARENT_AREA_ID, STAR_CRAFT_2_AREA_ID)
            .map(BilibiliVideoStreamSupplier::from);
    }

    public static VideoStream from(BilibiliStream bilibiliStream)
    {
        return new VideoStreamImpl
        (
            SocialMedia.BILIBILI,
            String.valueOf(bilibiliStream.getRoomId()),
            String.valueOf(bilibiliStream.getuId()),
            bilibiliStream.getuName(),
            bilibiliStream.getTitle(),
            Locale.CHINESE,
            bilibiliStream.getUrl(),
            bilibiliStream.getFace(),
            bilibiliStream.getSystemCover(),
            bilibiliStream.getWatchedShow().getNum()
        );
    }

}
