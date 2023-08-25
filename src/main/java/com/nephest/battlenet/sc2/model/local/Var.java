// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.util.function.Function;
import javax.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Var<T>
{

    private static final Logger LOG = LoggerFactory.getLogger(Var.class);

    @NotNull
    private final VarDAO varDAO;

    @NotNull
    private final String key;

    private T value;

    @NotNull
    private final Function<String, T> deserializer;

    @NotNull
    private final Function<T, String> serializer;

    public Var(VarDAO varDAO, String key, Function<T, String> serializer, Function<String, T> deserializer, boolean load)
    {
        this.varDAO = varDAO;
        this.key = key;
        this.deserializer = deserializer;
        this.serializer = serializer;
        if(load) load();
    }

    public Var(VarDAO varDAO, String key, Function<T, String> serializer, Function<String, T> deserializer)
    {
        this(varDAO, key, serializer, deserializer, true);
    }

    public T load()
    {
        this.value = deserializer.apply(varDAO.find(key).orElse(null));
        LOG.debug("Loaded var {}: {}", key, value);
        return this.value;
    }

    /**
     * Try to load the var. Exceptions are logged and ignored.
     * @return true if successfully loaded without exceptions, false otherwise.
     */
    public boolean tryLoad()
    {
        try
        {
            load();
            return true;
        }
        catch (RuntimeException e)
        {
            LOG.error(e.getMessage(), e);
            return false;
        }
    }

    public void save()
    {
        varDAO.merge(key, serializer.apply(value));
    }

    public VarDAO getVarDAO()
    {
        return varDAO;
    }

    public String getKey()
    {
        return key;
    }

    public T getValue()
    {
        return value;
    }

    public void setValue(T value)
    {
        this.value = value;
    }

    public void setValueAndSave(T value)
    {
        setValue(value);
        save();
    }

    public Function<String, T> getDeserializer()
    {
        return deserializer;
    }

    public Function<T, String> getSerializer()
    {
        return serializer;
    }

}
