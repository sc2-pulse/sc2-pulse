// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Identifiable;
import com.nephest.battlenet.sc2.model.local.inner.ClanMemberEventData;
import com.nephest.battlenet.sc2.model.util.SC2Pulse;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

public class ClanMemberEvent
{

    public enum EventType
    implements Identifiable
    {

        LEAVE(0),
        JOIN(1);

        private final int id;

        EventType(int id)
        {
            this.id = id;
        }

        public static EventType from(long id)
        {
            for(EventType type : EventType.values())
                if(type.getId() == id) return type;
            throw new IllegalArgumentException("Invalid id: " + id);
        }

        @Override
        public int getId()
        {
            return id;
        }

    }

    @NotNull
    private Long playerCharacterId;

    @NotNull
    private Integer clanId;

    @NotNull
    private EventType type;

    @NotNull
    private OffsetDateTime created;

    private Integer secondsSincePrevious;

    public ClanMemberEvent()
    {
    }

    public ClanMemberEvent
    (
        Long playerCharacterId,
        Integer clanId,
        EventType type,
        OffsetDateTime created,
        Integer secondsSincePrevious
    )
    {
        this.playerCharacterId = playerCharacterId;
        this.clanId = clanId;
        this.type = type;
        this.created = created;
        this.secondsSincePrevious = secondsSincePrevious;
    }

    public ClanMemberEvent
    (
        Long playerCharacterId,
        Integer clanId,
        EventType type,
        OffsetDateTime created
    )
    {
        this(playerCharacterId, clanId, type, created, null);
    }

    public static ClanMemberEvent from(PlayerCharacter character, Clan clan)
    {
        return new ClanMemberEvent
        (
            character.getId(),
            clan == null ? null : clan.getId(),
            clan == null ? ClanMemberEvent.EventType.LEAVE : ClanMemberEvent.EventType.JOIN,
            SC2Pulse.offsetDateTime()
        );
    }

    public static ClanMemberEvent from(ClanMemberEventData data)
    {
        return new ClanMemberEvent
        (
            data.getCharacter().getId(),
            data.getClan() == null ? null : data.getClan().getId(),
            data.getClan() == null ? ClanMemberEvent.EventType.LEAVE : ClanMemberEvent.EventType.JOIN,
            data.getCreatedAt().atOffset(ZoneOffset.UTC)
        );
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof ClanMemberEvent that)) {return false;}
        return getPlayerCharacterId().equals(that.getPlayerCharacterId())
            && getCreated().isEqual(that.getCreated());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getPlayerCharacterId(), getCreated());
    }

    @Override
    public String toString()
    {
        return "ClanMemberEvent{"
            + "playerCharacterId=" + playerCharacterId
            + ", created="
            + created + '}';
    }

    public Long getPlayerCharacterId()
    {
        return playerCharacterId;
    }

    public void setPlayerCharacterId(Long playerCharacterId)
    {
        this.playerCharacterId = playerCharacterId;
    }

    public Integer getClanId()
    {
        return clanId;
    }

    public void setClanId(Integer clanId)
    {
        this.clanId = clanId;
    }

    public EventType getType()
    {
        return type;
    }

    public void setType(EventType type)
    {
        this.type = type;
    }

    public OffsetDateTime getCreated()
    {
        return created;
    }

    public void setCreated(OffsetDateTime created)
    {
        this.created = created;
    }

    public Integer getSecondsSincePrevious()
    {
        return secondsSincePrevious;
    }

    public void setSecondsSincePrevious(Integer secondsSincePrevious)
    {
        this.secondsSincePrevious = secondsSincePrevious;
    }

}
