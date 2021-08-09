// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.dao;

import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.EvidenceVoteDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderEvidenceVote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.ConversionService;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

@Repository
public class LadderEvidenceVoteDAO
{

    private static final String FIND_ALL =
        "SELECT "
        + EvidenceVoteDAO.STD_SELECT + ", "
        + AccountDAO.STD_SELECT
        + "FROM evidence_vote "
        + "INNER JOIN account ON evidence_vote.voter_account_id = account.id "
        + "ORDER BY updated DESC";

    private static final String FIND_BY_EVIDENCE_IDS =
        "SELECT "
        + EvidenceVoteDAO.STD_SELECT + ", "
        + AccountDAO.STD_SELECT
        + "FROM evidence_vote "
        + "INNER JOIN account ON evidence_vote.voter_account_id = account.id "
        + "WHERE evidence_id IN (:evidenceIds) "
        + "ORDER BY updated DESC";

    public static final RowMapper<LadderEvidenceVote> STD_ROW_MAPPER = (rs, i)->new LadderEvidenceVote
    (
        EvidenceVoteDAO.STD_ROW_MAPPER.mapRow(rs, i),
        AccountDAO.getStdRowMapper().mapRow(rs, i)
    );

    private final NamedParameterJdbcTemplate template;
    private final ConversionService conversionService;

    @Autowired
    public LadderEvidenceVoteDAO
    (
        @Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.template = template;
        this.conversionService = conversionService;
    }

    public List<LadderEvidenceVote> findAll()
    {
        return template.query(FIND_ALL, STD_ROW_MAPPER);
    }

    public List<LadderEvidenceVote> findByEvidenceIds(Integer... evidenceIds)
    {
        if(evidenceIds.length == 0) return List.of();
        return template.query
        (
            FIND_BY_EVIDENCE_IDS,
            new MapSqlParameterSource().addValue("evidenceIds", Set.of(evidenceIds)),
            STD_ROW_MAPPER
        );
    }

}
