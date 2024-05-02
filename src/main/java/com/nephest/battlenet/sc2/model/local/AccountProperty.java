// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.Identifiable;
import jakarta.validation.constraints.NotNull;
import java.util.Objects;

public class AccountProperty
{

    private static final long serialVersionUID = 1L;

    public enum PropertyType
    implements Identifiable
    {
        PASSWORD(1);

        private final int id;

        PropertyType(int id)
        {
            this.id = id;
        }

        public static PropertyType from(int id)
        {
            for(PropertyType type : PropertyType.values())
                if(type.getId() == id) return type;
            throw new IllegalArgumentException("Invalid id:" + id);
        }

        @Override
        public int getId()
        {
            return id;
        }

    }

    @NotNull
    private Long accountId;

    @NotNull
    private PropertyType type;

    @NotNull
    private String value;

    public AccountProperty()
    {
    }

    public AccountProperty(Long accountId, PropertyType type, String value)
    {
        this.accountId = accountId;
        this.type = type;
        this.value = value;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {return true;}
        if (!(o instanceof AccountProperty that)) {return false;}
        return getAccountId().equals(that.getAccountId()) && getType() == that.getType();
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(getAccountId(), getType());
    }

    @Override
    public String toString()
    {
        return "AccountProperty{" + "accountId=" + accountId + ", type=" + type + '}';
    }

    public Long getAccountId()
    {
        return accountId;
    }

    public void setAccountId(Long accountId)
    {
        this.accountId = accountId;
    }

    public PropertyType getType()
    {
        return type;
    }

    public void setType(PropertyType type)
    {
        this.type = type;
    }

    public String getValue()
    {
        return value;
    }

    public void setValue(String value)
    {
        this.value = value;
    }

}
