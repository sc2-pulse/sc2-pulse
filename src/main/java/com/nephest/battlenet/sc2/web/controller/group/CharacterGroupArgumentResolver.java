// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller.group;

import com.nephest.battlenet.sc2.model.local.ClanMember;
import com.nephest.battlenet.sc2.model.local.dao.ClanMemberDAO;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CharacterGroupArgumentResolver
implements HandlerMethodArgumentResolver
{

    private static final MethodParameter CHARACTER_PARAMETER;
    private static final MethodParameter CLAN_PARAMETER;
    static
    {
        try
        {
            Method method = CharacterGroupArgumentResolver.class.getDeclaredMethod
            (
                "getCharacterIdsDescriptor",
                Set.class, Set.class
            );
            CHARACTER_PARAMETER = new MethodParameter(method, 0);
            CLAN_PARAMETER = new MethodParameter(method, 1);
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static final int CHARACTERS_MAX = 500;
    public static final int CLANS_MAX = 20;

    private final RequestParamMethodArgumentResolver paramResolver
        = new RequestParamMethodArgumentResolver(true);
    private final ClanMemberDAO clanMemberDAO;

    @Autowired
    public CharacterGroupArgumentResolver(ClanMemberDAO clanMemberDAO)
    {
        this.clanMemberDAO = clanMemberDAO;
    }

    public static void checkIds
    (
        Set<Long> characterIds,
        Set<Integer> clanIds
    ) throws ServletRequestBindingException
    {
        String msg = null;
        if(characterIds.isEmpty() && clanIds.isEmpty())
        {
            msg = "At least one group id is required";
        }
        else if(characterIds.size() > CHARACTERS_MAX)
        {
            msg = "Max size of characters exceeded: " + CHARACTERS_MAX;
        }
        else if(clanIds.size() > CLANS_MAX)
        {
            msg = "Max size of clans exceeded: " + CLANS_MAX;
        }
        if(msg != null) throw new ServletRequestBindingException(msg);
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter)
    {
        return parameter.getParameterAnnotation(CharacterGroup.class) != null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object resolveArgument
    (
        @NonNull MethodParameter parameter,
        ModelAndViewContainer mavContainer,
        @NonNull NativeWebRequest webRequest,
        WebDataBinderFactory binderFactory
    ) throws Exception
    {
        Set<Long> characterIds = (Set<Long>) paramResolver
            .resolveArgument(CHARACTER_PARAMETER, mavContainer, webRequest, binderFactory);
        Set<Integer> clanIds = (Set<Integer>) paramResolver
            .resolveArgument(CLAN_PARAMETER, mavContainer, webRequest, binderFactory);
        checkIds(characterIds, clanIds);
        Set<Long> result = Stream.of(characterIds, resolveClans(clanIds))
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
        CharacterGroup annotation = parameter.getParameterAnnotation(CharacterGroup.class);
        if(annotation.flatRequired() && result.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flattened character group is empty");
        if(result.size() > CHARACTERS_MAX)
            throw new ServletRequestBindingException("Max size of characters exceeded: " + CHARACTERS_MAX);
        return result;
    }

    private Set<Long> resolveClans(Set<Integer> clanIds)
    {
        if(clanIds.isEmpty()) return Set.of();

        return clanMemberDAO.findByClanIds(clanIds.toArray(Integer[]::new)).stream()
            .map(ClanMember::getPlayerCharacterId)
            .collect(Collectors.toSet());
    }

    private void getCharacterIdsDescriptor
    (
        @RequestParam(name = "characterId", required = false, defaultValue = "") Set<Long> characterIds,
        @RequestParam(name = "clanId", required = false, defaultValue = "") Set<Integer> clanIds
    )
    {
    }

}
