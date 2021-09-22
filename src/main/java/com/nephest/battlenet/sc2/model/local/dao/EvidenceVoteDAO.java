// Copyright (C) 2020-2021 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.dao;

import com.nephest.battlenet.sc2.model.local.EvidenceVote;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class EvidenceVoteDAO
{

    public static final String STD_SELECT =
        "evidence_vote.evidence_id AS \"evidence_vote.evidence_id\", "
        + "evidence_vote.evidence_created AS \"evidence_vote.evidence_created\", "
        + "evidence_vote.voter_account_id AS \"evidence_vote.voter_account_id\", "
        + "evidence_vote.vote AS \"evidence_vote.vote\", "
        + "evidence_vote.updated AS \"evidence_vote.updated\" ";

    private static final String MERGE_QUERY =
        "WITH updated AS "
        + "("
            + "UPDATE evidence_vote "
            + "SET vote = :vote, "
            + "updated = :updated "
            + "WHERE evidence_id = :evidenceId "
            + "AND voter_account_id = :voterAccountId "
            + "RETURNING evidence_id, voter_account_id"
        + ") "
        + "INSERT INTO evidence_vote (evidence_id, evidence_created, voter_account_id, vote, updated) "
        + "SELECT :evidenceId, :evidenceCreated, :voterAccountId, :vote, :updated "
        + "WHERE NOT EXISTS(SELECT 1 FROM updated)";

    private static final String GET_ALL_QUERY = "SELECT " + STD_SELECT + " FROM evidence_vote ORDER BY updated DESC";
    private static final String GET_BY_EVIDENCE_IDS =
        "SELECT " + STD_SELECT
        + "FROM evidence_vote "
        + "WHERE evidence_id IN (:evidenceIds) "
        + "ORDER BY updated DESC";

    private final NamedParameterJdbcTemplate template;

    public static final RowMapper<EvidenceVote> STD_ROW_MAPPER = (rs, i)->new EvidenceVote
    (
        rs.getInt("evidence_vote.evidence_id"),
        rs.getObject("evidence_vote.evidence_created", OffsetDateTime.class),
        rs.getLong("evidence_vote.voter_account_id"),
        rs.getBoolean("evidence_vote.vote"),
        rs.getObject("evidence_vote.updated", OffsetDateTime.class)
    );

    @Autowired
    public EvidenceVoteDAO(@Qualifier("sc2StatsNamedTemplate") NamedParameterJdbcTemplate template)
    {
        this.template = template;
    }

    public EvidenceVote merge(EvidenceVote vote)
    {
        template.update(MERGE_QUERY, createParameters(vote));
        return vote;
    }

    public List<EvidenceVote> findAll()
    {
        return template.query(GET_ALL_QUERY, STD_ROW_MAPPER);
    }

    public List<EvidenceVote> findByEvidenceIds(Integer... evidenceIds)
    {
        if(evidenceIds.length == 0) return List.of();
        return template.query
        (
            GET_BY_EVIDENCE_IDS,
            new MapSqlParameterSource().addValue("evidenceIds", List.of(evidenceIds)),
            STD_ROW_MAPPER
        );
    }

    private MapSqlParameterSource createParameters(EvidenceVote vote)
    {
        return new MapSqlParameterSource()
            .addValue("evidenceId", vote.getEvidenceId())
            .addValue("evidenceCreated", vote.getEvidenceCreated())
            .addValue("voterAccountId", vote.getVoterAccountId())
            .addValue("vote", vote.getVote())
            .addValue("updated", vote.getUpdated());
    }

}
