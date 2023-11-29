// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nephest.battlenet.sc2.config.AllTestConfig;
import com.nephest.battlenet.sc2.model.local.dao.PatchDAO;
import com.nephest.battlenet.sc2.model.local.dao.PatchReleaseDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPatch;
import com.nephest.battlenet.sc2.web.service.liquipedia.LiquipediaAPI;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = AllTestConfig.class)
@AutoConfigureMockMvc
@TestPropertySource("classpath:application.properties")
@TestPropertySource("classpath:application-private.properties")
public class SC2MetaServiceIT
{

    @Autowired
    private PatchDAO patchDAO;

    @Autowired
    private PatchReleaseDAO patchReleaseDAO;

    @Autowired
    private SC2MetaService sc2MetaService;

    @Autowired
    private LiquipediaAPI api;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    public void beforeEach(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-postgres.sql"));
        }
    }

    @AfterAll
    public static void afterAll(@Autowired DataSource dataSource)
    throws SQLException
    {
        try(Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema-drop-postgres.sql"));
        }
    }

    @Test
    public void testPatch()
    throws Exception
    {
        List<LadderPatch> recentPatches = api
            .parsePatches()
            .take(5)
            .collectList()
            .flatMapMany(patches->api.parsePatches(patches))
            .map(SC2MetaService::convertToLadderPatch)
            .collectList()
            .block();

        mvc.perform
        (
            get("/api/meta/patch")
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isNotFound())
            .andReturn();

        LadderPatch firstPatch = recentPatches.get(recentPatches.size() - 1);
        patchDAO.merge(Set.of(firstPatch.getPatch()));
        Assertions.assertThat
        (
            sc2MetaService.updatePatches().stream()
                .sorted(Comparator.comparing(p->p.getPatch().getBuild(), Comparator.reverseOrder()))
                .collect(Collectors.toUnmodifiableList())
        )
            .usingRecursiveComparison()
            .ignoringFields("patch.id")
            .isEqualTo(recentPatches);

        List<LadderPatch> foundPatches2 = objectMapper.readValue(mvc.perform
        (
            get("/api/meta/patch")
                .queryParam
                (
                    "buildMin",
                    String.valueOf(recentPatches.get(1).getPatch().getBuild())
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString(), new TypeReference<>(){});


        Assertions.assertThat(foundPatches2)
            .usingRecursiveComparison()
            .withEqualsForType(OffsetDateTime::isEqual, OffsetDateTime.class)
            .ignoringFields("patch.id")
            .isEqualTo(recentPatches.subList(0, 2));
    }

}
