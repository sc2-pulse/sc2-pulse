// Copyright (C) 2020-2024 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.model.local.ladder.common;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.nephest.battlenet.sc2.config.convert.jackson.ArrayToLadderTeamStateArrayListDeserializer;
import com.nephest.battlenet.sc2.config.convert.jackson.LadderTeamStateCollectionToArraySerializer;
import com.nephest.battlenet.sc2.model.discord.DiscordIdentity;
import com.nephest.battlenet.sc2.model.local.ladder.LadderDistinctCharacter;
import com.nephest.battlenet.sc2.model.local.ladder.LadderMatch;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerCharacterReport;
import com.nephest.battlenet.sc2.model.local.ladder.LadderPlayerCharacterStats;
import com.nephest.battlenet.sc2.model.local.ladder.LadderProPlayer;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamState;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class CommonCharacter
{

    @NotNull
    private List<LadderTeam> teams;

    @NotNull
    private List<LadderDistinctCharacter> linkedDistinctCharacters;

    @NotNull
    private List<LadderPlayerCharacterStats> stats;

    private LadderProPlayer proPlayer;

    private DiscordIdentity discordUser;

    private List<LadderMatch> matches;

    @JsonSerialize(using = LadderTeamStateCollectionToArraySerializer.class)
    @JsonDeserialize(using = ArrayToLadderTeamStateArrayListDeserializer.class)
    private List<LadderTeamState> history;

    @NotNull
    private List<LadderPlayerCharacterReport> reports;

    public CommonCharacter()
    {
    }

    public CommonCharacter
    (
        @NotNull List<LadderTeam> teams,
        @NotNull List<LadderDistinctCharacter> linkedDistinctCharacters,
        @NotNull List<LadderPlayerCharacterStats> stats,
        LadderProPlayer proPlayer,
        DiscordIdentity discordUser,
        List<LadderMatch> matches,
        List<LadderTeamState> history,
        List<LadderPlayerCharacterReport> reports
    )
    {
        this.teams = teams;
        this.linkedDistinctCharacters = linkedDistinctCharacters;
        this.stats = stats;
        this.proPlayer = proPlayer;
        this.discordUser = discordUser;
        this.matches = matches;
        this.history = history;
        this.reports = reports;
    }

    public List<LadderTeam> getTeams()
    {
        return teams;
    }

    public void setTeams(List<LadderTeam> teams)
    {
        this.teams = teams;
    }

    public List<LadderDistinctCharacter> getLinkedDistinctCharacters()
    {
        return linkedDistinctCharacters;
    }

    public void setLinkedDistinctCharacters(List<LadderDistinctCharacter> linkedDistinctCharacters)
    {
        this.linkedDistinctCharacters = linkedDistinctCharacters;
    }

    public List<LadderPlayerCharacterStats> getStats()
    {
        return stats;
    }

    public void setStats(List<LadderPlayerCharacterStats> stats)
    {
        this.stats = stats;
    }

    public LadderProPlayer getProPlayer()
    {
        return proPlayer;
    }

    public void setProPlayer(LadderProPlayer proPlayer)
    {
        this.proPlayer = proPlayer;
    }

    public DiscordIdentity getDiscordUser()
    {
        return discordUser;
    }

    public void setDiscordUser(DiscordIdentity discordUser)
    {
        this.discordUser = discordUser;
    }

    public List<LadderMatch> getMatches()
    {
        return matches;
    }

    public void setMatches(List<LadderMatch> matches)
    {
        this.matches = matches;
    }

    public List<LadderTeamState> getHistory()
    {
        return history;
    }

    public void setHistory(List<LadderTeamState> history
    )
    {
        this.history = history;
    }

    public List<LadderPlayerCharacterReport> getReports()
    {
        return reports;
    }

    public void setReports(List<LadderPlayerCharacterReport> reports)
    {
        this.reports = reports;
    }

}
