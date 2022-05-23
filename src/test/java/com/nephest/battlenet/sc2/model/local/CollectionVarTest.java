// Copyright (C) 2020-2022 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.nephest.battlenet.sc2.model.local.dao.VarDAO;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CollectionVarTest
{

    @Mock
    private VarDAO varDAO;

    private CollectionVar<List<Integer>, Integer> collectionVar;

    @BeforeEach
    public void beforeEach()
    {
        collectionVar = new CollectionVar<List<Integer>, Integer>
        (
            varDAO,
            "key",
            i->i == null ? "" : String.valueOf(i),
            s->s == null || s.isEmpty() ? null : Integer.valueOf(s),
            ArrayList::new,
            Collectors.toList(),
            false
        );
    }

    @Test
    public void testNullDeserialization()
    {
        assertNotNull(collectionVar.load());
        assertTrue(collectionVar.getValue().isEmpty());
    }

    @Test
    public void testEmptySerialization()
    {
        collectionVar.setValueAndSave(new ArrayList<>());
        verify(varDAO).merge("key", null);
    }

    @Test
    public void testDeserialization()
    {
        when(varDAO.find("key")).thenReturn(Optional.of("3,1,5"));
        assertArrayEquals(new Integer[]{3, 1, 5}, collectionVar.load().toArray(Integer[]::new));
    }

    @Test
    public void testSerialization()
    {
        collectionVar.setValueAndSave(List.of(3, 1, 5));
        verify(varDAO).merge("key", "3,1,5");
    }

    @Test
    public void testEmptyValueDeserialization()
    {
        when(varDAO.find("key")).thenReturn(Optional.of("3,,5"));
        assertArrayEquals(new Integer[]{3, null, 5}, collectionVar.load().toArray(Integer[]::new));
    }

    @Test
    public void testNullValueSerialization()
    {
        List<Integer> list = new ArrayList<>();
        list.add(3);
        list.add(null);
        list.add(5);
        collectionVar.setValueAndSave(list);
        verify(varDAO).merge("key", "3,,5");
    }

}
