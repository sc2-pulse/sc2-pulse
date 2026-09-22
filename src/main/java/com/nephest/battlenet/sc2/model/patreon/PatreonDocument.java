// Copyright (C) 2026 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.patreon;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Patreon JSON:API document. Only the fields used by SC2 Pulse are mapped.
 *
 * @param data primary resource
 * @param included related resources of any type. Depends on webhook scopes,
 * so it can be missing.
 * @param <T> primary resource type
 */
public record PatreonDocument<T>
(
    T data,
    List<Resource> included
)
{

    /**
     * @param type resource type
     * @param id resource id
     * @return included resource with matching type and id
     */
    public Optional<Resource> findIncluded(String type, String id)
    {
        if(included == null || id == null) return Optional.empty();

        return included.stream()
            .filter(Objects::nonNull)
            .filter(r->Objects.equals(r.type(), type))
            .filter(r->Objects.equals(r.id(), id))
            .findFirst();
    }

    public record Resource
    (
        String id,
        String type,
        ResourceAttributes attributes
    )
    {}

    /**
     * Attributes of included resources. Fields are type specific.
     *
     * @param currency campaign currency
     */
    public record ResourceAttributes(String currency)
    {}

    public record ResourceRef(String id, String type)
    {}

    public record Relationship(ResourceRef data)
    {

        /**
         * @return id of the related resource, null if there is no relation
         */
        public String id()
        {
            return data == null ? null : data.id();
        }

    }

}
