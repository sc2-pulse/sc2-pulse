// Copyright (C) 2020-2025 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller.group;

import com.nephest.battlenet.sc2.config.openapi.TeamLegacyUids;
import com.nephest.battlenet.sc2.model.local.dao.TeamDAO;
import com.nephest.battlenet.sc2.model.local.inner.TeamLegacyUid;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

@Component
@Validated
public class TeamGroupArgumentResolver
implements HandlerMethodArgumentResolver
{

    private static final MethodParameter TEAM_PARAMETER;
    private static final MethodParameter LEGACY_UID_PARAMETER;
    private static final MethodParameter FROM_SEASON_PARAMETER;
    private static final MethodParameter TO_SEASON_PARAMETER;
    static
    {
        try
        {
            Method method = TeamGroupArgumentResolver.class.getDeclaredMethod
            (
                "getTeamIdsDescriptor",
                Set.class, Set.class, Integer.class, Integer.class
            );
            TEAM_PARAMETER = new MethodParameter(method, 0);
            LEGACY_UID_PARAMETER = new MethodParameter(method, 1);
            FROM_SEASON_PARAMETER = new MethodParameter(method, 2);
            TO_SEASON_PARAMETER = new MethodParameter(method, 3);
        }
        catch (NoSuchMethodException e)
        {
            throw new RuntimeException(e);
        }
    }

    public static final int TEAMS_MAX = 10000;
    public static final int LEGACY_UIDS_MAX = TEAMS_MAX / 1000;

    private final RequestParamMethodArgumentResolver paramResolver
        = new RequestParamMethodArgumentResolver(true);
    private final TeamGroupArgumentResolver resolver;
    private final TeamDAO teamDAO;

    @Autowired
    public TeamGroupArgumentResolver(TeamDAO teamDAO, @Lazy TeamGroupArgumentResolver resolver)
    {
        this.teamDAO = teamDAO;
        this.resolver = resolver;
    }

    public static Optional<String> checkIds
    (
        Set<Long> teamIds,
        Set<TeamLegacyUid> legacyUids
    )
    {
        String msg = null;
        if (teamIds.isEmpty() && legacyUids.isEmpty())
        {
            msg = "At least one group id is required";
        }
        else if(teamIds.size() > TEAMS_MAX)
        {
            msg = "Max size of teams exceeded: " + TEAMS_MAX;
        }
        else if(legacyUids.size() > LEGACY_UIDS_MAX)
        {
            msg = "Max size of legacyUids exceeded: " + LEGACY_UIDS_MAX;
        }
        return Optional.ofNullable(msg);
    }

    public static Optional<ResponseEntity<?>> areIdsInvalid
    (
        Set<Long> teamIds,
        Set<TeamLegacyUid> legacyUids
    )
    {
        return checkIds(teamIds, legacyUids)
            .map(error->ResponseEntity.badRequest().body(error));
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter)
    {
        return parameter.getParameterAnnotation(TeamGroup.class) != null;
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
        Set<Long> teamIds = (Set<Long>) paramResolver
            .resolveArgument(TEAM_PARAMETER, mavContainer, webRequest, binderFactory);
        Set<TeamLegacyUid> legacyUids = (Set<TeamLegacyUid>) paramResolver
            .resolveArgument(LEGACY_UID_PARAMETER, mavContainer, webRequest, binderFactory);
        Integer fromSeason = (Integer) paramResolver
            .resolveArgument(FROM_SEASON_PARAMETER, mavContainer, webRequest, binderFactory);
        Integer toSeason = (Integer) paramResolver
            .resolveArgument(TO_SEASON_PARAMETER, mavContainer, webRequest, binderFactory);
        String error = checkIds(teamIds, legacyUids).orElse(null);
        if(error != null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error);

        Set<Long> result = resolver.resolve(teamIds, legacyUids, fromSeason, toSeason);
        TeamGroup annotation = parameter.getParameterAnnotation(TeamGroup.class);
        if(annotation.flatRequired() && result.isEmpty())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Flattened team group is empty");
        if(result.size() > TEAMS_MAX)
            throw new ResponseStatusException
            (
                HttpStatus.BAD_REQUEST,
                "Max size of teams exceeded: " + TEAMS_MAX
            );
        return result;
    }

    public Set<Long> resolve
    (
        Set<Long> teamIds,
        @Valid Set<TeamLegacyUid> legacyUids,
        Integer fromSeason,
        Integer toSeason
    )
    {
        return Stream.of
        (
            resolveIds(teamIds, fromSeason, toSeason),
            resolveLegacyUids(legacyUids, fromSeason, toSeason)
        )
            .flatMap(Collection::stream)
            .collect(Collectors.toSet());
    }

    private Collection<Long> resolveIds
    (
        Set<Long> ids,
        Integer fromSeason,
        Integer toSeason
    )
    {
        if(ids.isEmpty()) return List.of();
        if(fromSeason == null && toSeason == null) return ids;

        return teamDAO.findIdsByIds(ids, fromSeason, toSeason);
    }

    private List<Long> resolveLegacyUids
    (
        Set<TeamLegacyUid> legacyUids,
        Integer fromSeason,
        Integer toSeason
    )
    {
        if(legacyUids.isEmpty()) return List.of();

        return teamDAO.findIdsByLegacyUids(legacyUids, fromSeason, toSeason);
    }

    private void getTeamIdsDescriptor
    (
        @RequestParam(name = "teamId", required = false, defaultValue = "") Set<Long> teamIds,
        @RequestParam(name = "legacyUid", required = false, defaultValue = "") @Valid @TeamLegacyUids Set<TeamLegacyUid> legacyUid,
        @RequestParam(name = "seasonMin", required = false) @Min(0L) Integer fromSeason,
        @RequestParam(name = "seasonMax", required = false) @Min(0L) Integer toSeason
    )
    {
    }

}
