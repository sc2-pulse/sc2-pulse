// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Identifiable;
import java.time.OffsetDateTime;
import java.util.Objects;
import javax.validation.constraints.NotNull;

public class PlayerCharacterReport
implements java.io.Serializable
{

    public enum PlayerCharacterReportType
    implements Identifiable
    {
        CHEATER(1, "Cheater"),
        LINK(2, "Wintrader account pool"),
        GRIEFER(3, "Stream sniper/team killer");

        private final int id;
        private final String description;

        PlayerCharacterReportType(int id, String description)
        {
            this.id = id;
            this.description = description;
        }

        public static PlayerCharacterReportType from(int id)
        {
            for(PlayerCharacterReportType t : PlayerCharacterReportType.values())
                if(t.getId() == id) return t;
            throw new IllegalArgumentException("Invalid id " + id);
        }

        @Override
        public int getId()
        {
            return id;
        }

        public String getDescription()
        {
            return description;
        }

    }

    private static final long serialVersionUID = 1L;

    private Integer id;

    @NotNull
    private Long playerCharacterId;

    private Long additionalPlayerCharacterId;

    @NotNull
    private PlayerCharacterReportType type;

    private Boolean status;

    @NotNull
    private Boolean restrictions;

    @NotNull
    private OffsetDateTime statusChangeDateTime;

    public PlayerCharacterReport(){}

    public PlayerCharacterReport
    (
        Integer id,
        Long playerCharacterId,
        Long additionalPlayerCharacterId,
        PlayerCharacterReportType type,
        Boolean status,
        Boolean restrictions,
        OffsetDateTime statusChangeDateTime
    )
    {
        this.id = id;
        this.playerCharacterId = playerCharacterId;
        this.additionalPlayerCharacterId = additionalPlayerCharacterId;
        this.type = type;
        this.status = status;
        this.restrictions = restrictions;
        this.statusChangeDateTime = statusChangeDateTime;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (!(o instanceof PlayerCharacterReport)) return false;
        PlayerCharacterReport that = (PlayerCharacterReport) o;
        return playerCharacterId.equals(that.playerCharacterId)
            && Objects.equals(additionalPlayerCharacterId, that.additionalPlayerCharacterId)
            && type == that.type;
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(playerCharacterId, additionalPlayerCharacterId, type);
    }

    @Override
    public String toString()
    {
        return "PlayerCharacterReport{"
            + "playerCharacterId="
            + playerCharacterId
            + ", additionalPlayerCharacterId="
            + additionalPlayerCharacterId
            + ", type="
            + type
            + '}';
    }

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public Long getPlayerCharacterId()
    {
        return playerCharacterId;
    }

    public void setPlayerCharacterId(Long playerCharacterId)
    {
        this.playerCharacterId = playerCharacterId;
    }

    public Long getAdditionalPlayerCharacterId()
    {
        return additionalPlayerCharacterId;
    }

    public void setAdditionalPlayerCharacterId(Long additionalPlayerCharacterId)
    {
        this.additionalPlayerCharacterId = additionalPlayerCharacterId;
    }

    public PlayerCharacterReportType getType()
    {
        return type;
    }

    public void setType(PlayerCharacterReportType type)
    {
        this.type = type;
    }

    public Boolean getStatus()
    {
        return status;
    }

    public void setStatus(Boolean status)
    {
        this.status = status;
    }

    public Boolean getRestrictions()
    {
        return restrictions;
    }

    public void setRestrictions(Boolean restrictions)
    {
        this.restrictions = restrictions;
    }

    public OffsetDateTime getStatusChangeDateTime()
    {
        return statusChangeDateTime;
    }

    public void setStatusChangeDateTime(OffsetDateTime statusChangeDateTime)
    {
        this.statusChangeDateTime = statusChangeDateTime;
    }

}
