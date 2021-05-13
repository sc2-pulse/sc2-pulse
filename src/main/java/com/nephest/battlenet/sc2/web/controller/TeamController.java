// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.controller;

import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.Region;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;
import com.nephest.battlenet.sc2.model.local.ladder.common.CommonTeamHistory;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderTeamStateDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/team")
public class TeamController
{

    @Autowired
    private LadderTeamStateDAO ladderTeamStateDAO;

    @Autowired
    private LadderSearchDAO ladderSearchDAO;

    @Autowired @Qualifier("sc2StatsConversionService")
    private ConversionService conversionService;

    @GetMapping("/history/common")
    public Map<String, CommonTeamHistory> getCommonHistory
    (@RequestParam MultiValueMap<String, String> params)
    {
        List<String> ids = params.get("legacyUid");
        if(ids == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "legacyUid parameter not found");

        Set<BigInteger> idSet = ids.stream()
            .map(sId->new BigInteger(sId.replaceAll("-", "")))
            .collect(Collectors.toSet());
        List<LadderTeamState> states = ladderTeamStateDAO.find(idSet);
        List<LadderTeam> teams = ladderSearchDAO.findLegacyTeams(idSet);
        return groupCommonHistory(ids, states, teams);
    }

    private Map<String, CommonTeamHistory> groupCommonHistory
    (List<String> ids, List<LadderTeamState> states, List<LadderTeam> teams)
    {
        Map<String, CommonTeamHistory> result = new HashMap<>();

        for(String strId : ids)
        {
            String[] split = strId.split("-");
            if(split.length < 3) throw new IllegalArgumentException("Invalid legacyUid length: " + split.length);

            QueueType queueType = conversionService.convert(Integer.valueOf(split[0]), QueueType.class);
            Region region = conversionService.convert(Integer.valueOf(split[1]), Region.class);
            BigInteger legacyId = new BigInteger(split[2]);

            List<LadderTeam> filteredTeams = teams.stream()
                .filter(t->t.getQueueType() == queueType && t.getRegion() == region && t.getLegacyId().equals(legacyId))
                .collect(Collectors.toList());
            Set<Long> teamIds = filteredTeams.stream().map(LadderTeam::getId).collect(Collectors.toSet());
            List<LadderTeamState> filteredStates = states.stream()
                .filter(s->teamIds.contains(s.getTeamState().getTeamId()))
                .collect(Collectors.toList());

            teams.removeAll(filteredTeams);
            states.removeAll(filteredStates);

            result.put(strId, new CommonTeamHistory(filteredTeams, filteredStates));
        }

        return result;
    }

}
