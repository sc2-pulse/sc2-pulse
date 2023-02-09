// Copyright (C) 2020-2023 Oleksandr Masniuk
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.nephest.battlenet.sc2.web.service;

import com.nephest.battlenet.sc2.discord.Discord;
import com.nephest.battlenet.sc2.discord.DiscordBootstrap;
import com.nephest.battlenet.sc2.discord.GuildRoleStore;
import com.nephest.battlenet.sc2.discord.IdentifiableEntity;
import com.nephest.battlenet.sc2.discord.PulseMappings;
import com.nephest.battlenet.sc2.discord.connection.ApplicationRoleConnection;
import com.nephest.battlenet.sc2.discord.connection.PulseConnectionParameters;
import com.nephest.battlenet.sc2.discord.event.RolesSlashCommand;
import com.nephest.battlenet.sc2.model.QueueType;
import com.nephest.battlenet.sc2.model.discord.DiscordUser;
import com.nephest.battlenet.sc2.model.discord.dao.DiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.AccountDiscordUser;
import com.nephest.battlenet.sc2.model.local.PlayerCharacter;
import com.nephest.battlenet.sc2.model.local.dao.AccountDAO;
import com.nephest.battlenet.sc2.model.local.dao.AccountDiscordUserDAO;
import com.nephest.battlenet.sc2.model.local.dao.PlayerCharacterDAO;
import com.nephest.battlenet.sc2.model.local.dao.SeasonDAO;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeam;
import com.nephest.battlenet.sc2.model.local.ladder.LadderTeamMember;
import com.nephest.battlenet.sc2.model.local.ladder.dao.LadderSearchDAO;
import com.nephest.battlenet.sc2.service.EventService;
import com.nephest.battlenet.sc2.util.MiscUtil;
import discord4j.common.util.Snowflake;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Role;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.convert.ConversionService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

@Service
@Discord
public class DiscordService
{

    private static final Logger LOG = LoggerFactory.getLogger(DiscordService.class);

    public static final int DB_CURSOR_BATCH_SIZE = 1000;
    public static final int USER_UPDATE_BATCH_SIZE = 200;
    public static final int MAIN_TEAM_SEASON_DEPTH = 3;
    public static final Set<QueueType> MAIN_TEAM_QUEUE_TYPES = Set.of(QueueType.LOTV_1V1);
    public static final Comparator<LadderTeam> MAIN_TEAM_COMPARATOR = Comparator
        .comparing(LadderTeam::getSeason).reversed()
        .thenComparing(LadderTeam::getQueueType)
        .thenComparing(Comparator.comparingLong(LadderTeam::getRating).reversed())
        .thenComparing(LadderTeam::getId);
    public static final String DROP_ROLES_REASON = "User has unlinked their Discord account";

    private final DiscordUserDAO discordUserDAO;
    private final AccountDiscordUserDAO accountDiscordUserDAO;
    private final SeasonDAO seasonDAO;
    private final AccountDAO accountDAO;
    private final PlayerCharacterDAO playerCharacterDAO;
    private final LadderSearchDAO ladderSearchDAO;
    private DiscordAPI discordAPI;
    private final GuildRoleStore guildRoleStore;
    private final OAuth2AuthorizedClientService oAuth2AuthorizedClientService;
    private final PulseConnectionParameters pulseConnectionParameters;
    private final ExecutorService dbExecutorService;
    private final ConversionService conversionService;

    @Autowired @Lazy
    private DiscordService discordService;

    @Autowired @Lazy
    private RolesSlashCommand rolesSlashCommand;

    @Autowired
    public DiscordService
    (
        DiscordUserDAO discordUserDAO,
        AccountDiscordUserDAO accountDiscordUserDAO,
        SeasonDAO seasonDAO,
        AccountDAO accountDAO,
        PlayerCharacterDAO playerCharacterDAO,
        LadderSearchDAO ladderSearchDAO,
        DiscordAPI discordAPI,
        GuildRoleStore guildRoleStore,
        OAuth2AuthorizedClientService oAuth2AuthorizedClientService,
        EventService eventService,
        PulseConnectionParameters pulseConnectionParameters,
        @Qualifier("dbExecutorService") ExecutorService dbExecutorService,
        @Qualifier("sc2StatsConversionService") ConversionService conversionService
    )
    {
        this.discordUserDAO = discordUserDAO;
        this.accountDiscordUserDAO = accountDiscordUserDAO;
        this.seasonDAO = seasonDAO;
        this.accountDAO = accountDAO;
        this.playerCharacterDAO = playerCharacterDAO;
        this.ladderSearchDAO = ladderSearchDAO;
        this.pulseConnectionParameters = pulseConnectionParameters;
        this.discordAPI = discordAPI;
        this.guildRoleStore = guildRoleStore;
        this.oAuth2AuthorizedClientService = oAuth2AuthorizedClientService;
        this.dbExecutorService = dbExecutorService;
        this.conversionService = conversionService;
        eventService.getLadderCharacterActivityEvent()
            .subscribeOn(Schedulers.boundedElastic())
            .subscribe(c->updateRoles(c.getAccountId()).subscribe());
    }

    protected DiscordAPI getDiscordAPI()
    {
        return discordAPI;
    }

    protected void setDiscordAPI(DiscordAPI discordAPI)
    {
        this.discordAPI = discordAPI;
    }

    protected DiscordService getDiscordService()
    {
        return discordService;
    }

    protected void setDiscordService(DiscordService discordService)
    {
        this.discordService = discordService;
    }

    protected RolesSlashCommand getRolesSlashCommand()
    {
        return rolesSlashCommand;
    }

    protected void setRolesSlashCommand(RolesSlashCommand rolesSlashCommand)
    {
        this.rolesSlashCommand = rolesSlashCommand;
    }

    /**
     * Remove existing connections and link the ids afterwards within the same transaction.
     *
     * @param accountId accountId
     * @param discordUserId discordUserId to link with
     */
    @Transactional
    public void linkAccountToDiscordUser(Long accountId, Long discordUserId)
    {
        accountDiscordUserDAO.remove(accountId, discordUserId);
        accountDiscordUserDAO.create(new AccountDiscordUser(accountId, discordUserId));
    }

    @Transactional
    public void linkAccountToNewDiscordUser(Long accountId, DiscordUser discordUser)
    {
        discordUserDAO.merge(discordUser);
        linkAccountToDiscordUser(accountId, discordUser.getId());
    }

    public void unlinkAccountFromDiscordUser(Long accountId, Long discordUserId)
    {
        dropRoles(accountId, DROP_ROLES_REASON).blockLast();
        discordService.unlinkAccountFromDiscordUserDB(accountId, discordUserId);
    }

    @Transactional
    public void unlinkAccountFromDiscordUserDB(Long accountId, Long discordUserId)
    {
        accountDiscordUserDAO.remove(accountId, discordUserId);
        oAuth2AuthorizedClientService
            .removeAuthorizedClient(DiscordAPI.USER_CLIENT_REGISTRATION_ID, String.valueOf(accountId));
    }

    public void setVisibility(Long accountId, boolean isVisible)
    {
        accountDiscordUserDAO.updatePublicFlag(accountId, isVisible);
    }

    public void update()
    {
        removeUsersWithNoAccountLinked();
        updateUsersFromAPI();
    }

    private void removeUsersWithNoAccountLinked()
    {
        int removed = discordUserDAO.removeUsersWithNoAccountLinked();
        if(removed > 0) LOG.info("Removed {} empty discord users", removed);
    }

    private int updateUsersFromAPI()
    {
        long idCursor = 0;
        int count = 0;
        while(true)
        {
            List<Long> toUpdate = discordUserDAO.findIdsByIdCursor(idCursor, DB_CURSOR_BATCH_SIZE);
            if(toUpdate.isEmpty()) break;

            List<Future<?>> tasks = new ArrayList<>();
            discordAPI.getUsers(toUpdate)
                .buffer(USER_UPDATE_BATCH_SIZE)
                .toStream()
                .forEach(batch->tasks.add(dbExecutorService.submit(()->updateUsers(batch))));
            MiscUtil.awaitAndThrowException(tasks, true, true);

            count += toUpdate.size();
            idCursor = toUpdate.get(toUpdate.size() - 1);
        }

        if(count > 0) LOG.info("Updated {} discord users", count);
        return count;
    }

    private void updateUsers(List<DiscordUser> users)
    {
        discordUserDAO.merge(users.toArray(DiscordUser[]::new));
    }

    public Optional<LadderTeam> findMainTeam(Long accountId)
    {
        List<PlayerCharacter> characters = playerCharacterDAO.findByAccountId(accountId);
        if(characters.isEmpty()) return Optional.empty();

        int curSeason = seasonDAO.getMaxBattlenetId();
        Set<Integer> seasons = IntStream
            .rangeClosed(curSeason - MAIN_TEAM_SEASON_DEPTH + 1, curSeason)
            .boxed()
            .collect(Collectors.toSet());
        return characters.stream()
            .flatMap(c->ladderSearchDAO
                .findCharacterTeams(c.getId(), seasons, MAIN_TEAM_QUEUE_TYPES, 1).stream())
            .min(MAIN_TEAM_COMPARATOR);
    }

    public Flux<Void> updateRoles(Long accountId)
    {
        return updateRoles(accountId, false, null);
    }

    private Flux<Void> dropRoles(Long accountId, String reason)
    {
        return updateRoles(accountId, true, reason);
    }

    private Flux<Void> updateRoles(Long accountId, boolean drop, String reason)
    {
        if(!accountDiscordUserDAO.findAccountIds().contains(accountId)) return Flux.empty();

        Tuple2<LadderTeam, LadderTeamMember> mainTuple = drop ? null : findMainTuple(accountId);
        ApplicationRoleConnection roleConnection = mainTuple != null
            ? 
                ApplicationRoleConnection.from
                (
                    mainTuple.getT1(),
                    mainTuple.getT2().getAccount().getBattleTag(),
                    mainTuple.getT2().getFavoriteRace(),
                    pulseConnectionParameters.getParameters(),
                    conversionService
                )
            : ApplicationRoleConnection.empty(accountDAO.findByIds(accountId).get(0).getBattleTag());

        String principalName = String.valueOf(accountId);
        return Flux.concat
        (
            discordAPI.updateConnectionMetaData(principalName, roleConnection),
            getManagedRoleGuilds(principalName)
                .flatMap
                (
                    guild->getMemberMappings
                    (
                        guild,
                        discordUserDAO.findByAccountId(accountId, false)
                            .orElseThrow()
                            .getId()
                    )
                )
                .flatMap
                (
                    t->rolesSlashCommand.updateRoles
                    (
                        mainTuple != null ? mainTuple.getT1() : null,
                        mainTuple != null ? mainTuple.getT2().getFavoriteRace() : null,
                        t.getT2(),
                        t.getT1(),
                        reason != null ? reason : RolesSlashCommand.UPDATE_REASON
                    ).getRight()
                )
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Tuple2<LadderTeam, LadderTeamMember> findMainTuple(Long accountId)
    {
        LadderTeam mainTeam = findMainTeam(accountId).orElse(null);
        if(mainTeam == null) return null;

        LadderTeamMember member = mainTeam.getMembers().stream()
            .filter(m->Objects.equals(m.getAccount().getId(), accountId))
            .findFirst()
            .orElseThrow();
        return Tuples.of(mainTeam, member);
    }

    public Flux<Guild> getManagedRoleGuilds(String principalName)
    {
        return discordAPI.getGuilds(principalName, IdentifiableEntity.class)
            .map(IdentifiableEntity::getId)
            .filter(discordAPI.getBotGuilds().keySet()::contains)
            .flatMap(id->discordAPI.getDiscordClient().getClient().getGuildById(Snowflake.of(id)))
            .filterWhen(guild->DiscordBootstrap.haveSelfPermissions(guild, RolesSlashCommand.REQUIRED_PERMISSIONS))
            .filterWhen(guild->guildRoleStore.getManagedRoleMappings(guild).map(mappings->!mappings.isEmpty()));
    }

    private Mono<Tuple2<Member, PulseMappings<Role>>> getMemberMappings(Guild guild, long memberId)
    {
        return guild.getMemberById(Snowflake.of(memberId))
            .zipWith(guildRoleStore.getManagedRoleMappings(guild));
    }

}
