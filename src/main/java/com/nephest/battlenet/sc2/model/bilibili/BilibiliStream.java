// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.bilibili;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Objects;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BilibiliStream
{

    @JsonProperty("roomid")
    private Long roomId;

    @JsonProperty("uid")
    private Long uId;

    private Long parentId, areaId, groupId;

    @JsonProperty("uname")
    private String uName;

    private String title, userCover, systemCover, face, parentName, areaName;

    private BilibiliStreamWatchedShow watchedShow;

    public BilibiliStream
    (
        Long roomId, Long uId, Long parentId, Long areaId, Long groupId,
        String title, String uName, String userCover, String systemCover, String face,
        String parentName, String areaName,
        BilibiliStreamWatchedShow watchedShow
    )
    {
        this.roomId = roomId;
        this.uId = uId;
        this.parentId = parentId;
        this.areaId = areaId;
        this.groupId = groupId;
        this.title = title;
        this.uName = uName;
        this.userCover = userCover;
        this.systemCover = systemCover;
        this.face = face;
        this.parentName = parentName;
        this.areaName = areaName;
        this.watchedShow = watchedShow;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof BilibiliStream)) {return false;}
        BilibiliStream that = (BilibiliStream) o;
        return Objects.equals(getuId(), that.getuId());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getuId());
    }

    public Long getRoomId()
    {
        return roomId;
    }

    public void setRoomId(Long roomId)
    {
        this.roomId = roomId;
    }

    public Long getuId()
    {
        return uId;
    }

    public void setuId(Long uId)
    {
        this.uId = uId;
    }

    public Long getParentId()
    {
        return parentId;
    }

    public void setParentId(Long parentId)
    {
        this.parentId = parentId;
    }

    public Long getAreaId()
    {
        return areaId;
    }

    public void setAreaId(Long areaId)
    {
        this.areaId = areaId;
    }

    public Long getGroupId()
    {
        return groupId;
    }

    public void setGroupId(Long groupId)
    {
        this.groupId = groupId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getuName()
    {
        return uName;
    }

    public void setuName(String uName)
    {
        this.uName = uName;
    }

    public String getUserCover()
    {
        return userCover;
    }

    public void setUserCover(String userCover)
    {
        this.userCover = userCover;
    }

    public String getSystemCover()
    {
        return systemCover;
    }

    public void setSystemCover(String systemCover)
    {
        this.systemCover = systemCover;
    }

    public String getFace()
    {
        return face;
    }

    public void setFace(String face)
    {
        this.face = face;
    }

    public String getParentName()
    {
        return parentName;
    }

    public void setParentName(String parentName)
    {
        this.parentName = parentName;
    }

    public String getAreaName()
    {
        return areaName;
    }

    public void setAreaName(String areaName)
    {
        this.areaName = areaName;
    }

    public BilibiliStreamWatchedShow getWatchedShow()
    {
        return watchedShow;
    }

    public void setWatchedShow(BilibiliStreamWatchedShow watchedShow)
    {
        this.watchedShow = watchedShow;
    }

    public String getUrl()
    {
        return "https://live.bilibili.com/" + getRoomId();
    }

}
