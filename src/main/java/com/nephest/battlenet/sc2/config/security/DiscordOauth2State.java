// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.config.security;

import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Base64;
import java.util.BitSet;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class DiscordOauth2State
{

    private final @NotNull byte[] id;
    private final @NotNull Set<Flag> flags;

    public enum Flag
    {

        LINKED_ROLE(0);

        private final int position;

        Flag(int position)
        {
            this.position = position;
        }

        public static Flag fromPosition(int position)
        {
            for(Flag flag : Flag.values())
                if(flag.getPosition() == position) return flag;

            throw new IllegalArgumentException("Invalid position: " + position);
        }

        public int getPosition()
        {
            return position;
        }

    }

    public DiscordOauth2State(@NotNull byte[] id, @NotNull Set<Flag> flags)
    {
        this.id = Arrays.copyOf(id, id.length);
        this.flags = Set.copyOf(flags);
    }

    public static DiscordOauth2State fromUrlString(@NotNull String state, int idLength)
    {
        if(state == null || state.isEmpty())
            throw new IllegalArgumentException("State expected");
        if(idLength < 1)
            throw new IllegalArgumentException("Positive idLength expected, got " + idLength);
        byte[] bytes = Base64.getUrlDecoder().decode(state);

        if(bytes.length < idLength)
            throw new IllegalArgumentException("Decoded state is shorted than idLength");

        return new DiscordOauth2State
        (
            Arrays.copyOfRange(bytes, 0, idLength),
            decodeFlags(Arrays.copyOfRange(bytes, idLength, bytes.length))
        );
    }

    @Override
    public String toString()
    {
        return "DiscordOauth2State{"
            + "id=" + Arrays.toString(id)
            + ", flags=" + flags
        + '}';
    }

    @Override
    public boolean equals(Object o)
    {
        if (!(o instanceof DiscordOauth2State that)) {return false;}
        return Objects.deepEquals(id, that.id) && Objects.equals(getFlags(), that.getFlags());
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(Arrays.hashCode(id), getFlags());
    }

    public String toUriString()
    {
        return Base64.getUrlEncoder().encodeToString(getBytes());
    }

    private byte[] getBytes()
    {
        byte[] flagBytes = encodeFlags(getFlags());
        byte[] bytes = Arrays.copyOf(id, id.length + flagBytes.length);
        System.arraycopy(flagBytes, 0, bytes, id.length, flagBytes.length);
        return bytes;
    }

    private static byte[] encodeFlags(Collection<Flag> flags)
    {
        BitSet bitSet = new BitSet(flags.size());
        flags.forEach(f->bitSet.set(f.getPosition()));
        return bitSet.toByteArray();
    }

    private static Set<Flag> decodeFlags(byte[] flags)
    {
        if(flags.length == 0) return Set.of();

        BitSet bitSet = BitSet.valueOf(flags);
        return bitSet.stream()
            .mapToObj(Flag::fromPosition)
            .collect(Collectors.toSet());
    }

    public Set<Flag> getFlags()
    {
        return flags;
    }

    public byte[] getIdCopy()
    {
        return Arrays.copyOf(id, id.length);
    }

}
